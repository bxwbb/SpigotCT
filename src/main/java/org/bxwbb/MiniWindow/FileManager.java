package org.bxwbb.MiniWindow;

import org.bxwbb.Main;
import org.bxwbb.Setting;
import org.bxwbb.Swing.CreateFile;
import org.bxwbb.Swing.CreateFolder;
import org.bxwbb.Swing.RenameFile;
import org.bxwbb.UI.IndicatorStatus;
import org.bxwbb.UI.MissionTip;
import org.bxwbb.UI.RoundLabel;
import org.bxwbb.Util.ClipboardUtil;
import org.bxwbb.Util.DragDrop.FileTransferHandler;
import org.bxwbb.Util.FileUtil;
import org.bxwbb.Util.JTreeExpandCollapseUtil;
import org.bxwbb.Util.PathInfoFormatter;
import org.bxwbb.Util.Task.ControllableThreadTask;
import org.bxwbb.Util.Task.ScheduledTaskManager;
import org.bxwbb.WorkEventer.Work;
import org.bxwbb.WorkEventer.WorkControllableThreadTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class FileManager extends MiniWindow {

    private static final Logger log = LoggerFactory.getLogger(FileManager.class);
    private final AtomicBoolean isLoading = new AtomicBoolean(false);
    private final AtomicInteger loadingCount = new AtomicInteger(0);
    private static final int MIN_FILE_COUNT = 10000;

    private File rootFile;

    public FileManager() {
        super(FileManager.class);
    }

    private String cutPath;
    private DefaultMutableTreeNode cutNode;
    private boolean isCut = false;
    private String refreshTaskID;

    FileManager self = this;

    @Override
    public void init() {
        Image image = new ImageIcon(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/FileManager/SelectFolder.png"))).getImage();
        JButton selectFolderButton = new JButton(new ImageIcon(image.getScaledInstance(20, 20, Image.SCALE_SMOOTH)));

        selectFolderButton.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                Component parent = getCenterPanel();
                rootFile = FileUtil.chooseSingleFolder(parent, FileUtil.getLang("miniWindow.fileManager.selectFile"));
                if (rootFile == null) {
                    return;
                }

                try {
                    JPanel centerPanel = getCenterPanel();
                    centerPanel.removeAll();
                    centerPanel.revalidate();
                    centerPanel.repaint();

                    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(new FileData(rootFile));
                    DefaultTreeModel newTreeModel = new DefaultTreeModel(rootNode);

                    JTree newFileTree = new JTree(newTreeModel);
                    newFileTree.setRootVisible(true);
                    newFileTree.setShowsRootHandles(true);
                    newFileTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
                    newFileTree.setMinimumSize(new Dimension(0, Short.MAX_VALUE));
                    newFileTree.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
                    newFileTree.setBackground(Setting.BACKGROUND_COLOR);
                    newFileTree.setDragEnabled(true);
                    newFileTree.getTransferHandler().getSourceActions(newFileTree);
                    newFileTree.getSelectionModel().setSelectionMode(
                            TreeSelectionModel.SINGLE_TREE_SELECTION
                    );
                    newFileTree.setDropMode(DropMode.ON);
                    newFileTree.setTransferHandler(new FileTransferHandler());

                    newFileTree.getSelectionModel().addTreeSelectionListener(treeSelectionEvent -> openFiles(newFileTree, newTreeModel));
                    newFileTree.addTreeExpansionListener(new TreeExpansionListener() {
                        @Override
                        public void treeExpanded(TreeExpansionEvent event) {
                            SwingUtilities.invokeLater(() -> {
                                TreePath expandedPath = event.getPath();
                                DefaultMutableTreeNode expandedNode = (DefaultMutableTreeNode) expandedPath.getLastPathComponent();
                                continuouslyUnfolded(expandedNode, newTreeModel, newFileTree);
                            });
                        }

                        @Override
                        public void treeCollapsed(TreeExpansionEvent event) {
                        }
                    });
                    newFileTree.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseReleased(MouseEvent e) {
                            if (SwingUtilities.isRightMouseButton(e)) {
                                int row = newFileTree.getRowForLocation(e.getX(), e.getY());
                                if (row != -1) {
                                    newFileTree.setSelectionRow(row);
                                    TreePath selectedPath = newFileTree.getSelectionPath();
                                    DefaultMutableTreeNode selectedNode = null;
                                    if (selectedPath != null) {
                                        selectedNode = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
                                    }

                                    if (selectedNode != null) {
                                        JPopupMenu popupMenu = new JPopupMenu();
                                        createPopMenu(popupMenu, selectedNode, newTreeModel, newFileTree);
                                        popupMenu.show(newFileTree, e.getX(), e.getY());
                                    }
                                }
                            }
                        }

                    });

                    newFileTree.setCellRenderer(new FileTreeRenderer());

                    JScrollPane newScrollPane = new JScrollPane(newFileTree);
                    newScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
                    newScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                    newScrollPane.setBorder(BorderFactory.createEtchedBorder());
                    newScrollPane.getViewport().setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));

                    refreshTreeAsync(rootNode, newTreeModel, newFileTree, () -> {
                        newFileTree.expandPath(new TreePath(rootNode.getPath()));
                        centerPanel.revalidate();
                        centerPanel.repaint();
                        ScheduledTaskManager.getInstance().stopTask(refreshTaskID);
                        refreshTaskID = ScheduledTaskManager.getInstance().startFixedDelayTask(2500, () -> {
                            try {
                                Thread.sleep(2500);
                            } catch (InterruptedException e) {
                                log.error("线程睡眠时被打断");
                            }
                            self.refreshTreeSync(rootNode, newTreeModel, newFileTree);
                        });
                    });

                    centerPanel.setLayout(new BorderLayout());
                    centerPanel.add(newScrollPane, BorderLayout.CENTER);
                    centerPanel.revalidate();
                    centerPanel.repaint();
                } catch (Exception e) {
                    log.error("加载文件目录树失败", e);
                    JOptionPane.showMessageDialog(
                            parent,
                            FileUtil.getLang("tip.file.load.failed") + e.getMessage(),
                            "错误",
                            JOptionPane.ERROR_MESSAGE,
                            self.getIcon()
                    );
                }
            }
        });

        selectFolderButton.setMaximumSize(new Dimension(30, 30));
        selectFolderButton.setMinimumSize(new Dimension(30, 30));
        selectFolderButton.setPreferredSize(new Dimension(30, 30));

        JLabel initLabel = new JLabel(FileUtil.getLang("miniWindow.fileManager.pleaseSelectFile"));
        getCenterPanel().add(initLabel, BorderLayout.CENTER);
        getTopPanel().add(selectFolderButton);
    }

    private void continuouslyUnfolded(DefaultMutableTreeNode expandedNode, DefaultTreeModel newTreeModel, JTree newFileTree) {
        refreshTreeAsync(expandedNode, newTreeModel, newFileTree, () -> {
            if (expandedNode.getChildCount() == 1) {
                SwingUtilities.invokeLater(() -> {
                    newFileTree.expandPath(new TreePath(((DefaultMutableTreeNode) expandedNode.getChildAt(0)).getPath()));
                    getCenterPanel().revalidate();
                    getCenterPanel().repaint();
                });
            }
        });
    }

    @Override
    public void delete() {
        ScheduledTaskManager.getInstance().stopTask(refreshTaskID);
    }

    private void createPopMenu(JPopupMenu popupMenu, DefaultMutableTreeNode selectedNode, DefaultTreeModel currentModel, JTree tree) {
        if (selectedNode.getUserObject() instanceof FileData(File file)) {
            JMenu createNew = new JMenu(FileUtil.getLang("miniWindow.fileManager.popMenu.create"));
            JMenuItem createFile = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.create.file"),
                    FileUtil.getImageIconToPath(Objects.requireNonNull(FileUtil.loadFile(FileUtil.DEFAULT_FILE_ICON)).getPath()));
            createFile.addActionListener(e -> {
                File pFile;
                if (file.isDirectory()) {
                    pFile = file;
                } else if (file.isFile()) {
                    pFile = file.getParentFile();
                } else {
                    log.error("在获取点击文件时发生未知错误");
                    return;
                }
                CreateFile dialog = new CreateFile(pFile.toPath());
                dialog.setSize(600, 200);
                dialog.setLocationRelativeTo(null);
                dialog.setVisible(true);
                if (file.isDirectory()) {
                    refreshTreeAsync(selectedNode, currentModel, tree, () -> SwingUtilities.invokeLater(() -> tree.expandPath(new TreePath(selectedNode.getPath()))));
                } else {
                    refreshTreeAsync((DefaultMutableTreeNode) selectedNode.getParent(), currentModel, tree, () -> SwingUtilities.invokeLater(() -> tree.expandPath(new TreePath(((DefaultMutableTreeNode) selectedNode.getParent()).getPath()))));
                }
            });
            createNew.add(createFile);
            JMenuItem createFolder = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.create.folder"),
                    FileUtil.getImageIconToPath(Objects.requireNonNull(FileUtil.loadFile(FileUtil.DEFAULT_FOLDER_ICON[1])).getPath()));
            createFolder.addActionListener(event -> {
                File pFile;
                if (file.isDirectory()) {
                    pFile = file;
                } else if (file.isFile()) {
                    pFile = file.getParentFile();
                } else {
                    log.error("在获取点击文件夹时发生未知错误");
                    return;
                }
                CreateFolder dialog = new CreateFolder(pFile.toPath());
                dialog.setSize(600, 200);
                dialog.setLocationRelativeTo(null);
                dialog.setVisible(true);
                if (file.isDirectory()) {
                    refreshTreeAsync(selectedNode, currentModel, tree);
                } else {
                    refreshTreeAsync((DefaultMutableTreeNode) selectedNode.getParent(), currentModel, tree);
                }
            });
            createNew.add(createFolder);
            popupMenu.add(createNew);
            popupMenu.addSeparator();
            JMenuItem openOnOutside = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.openFileOnOutside"));
            openOnOutside.addActionListener(event -> {
                try {
                    FileUtil.openFile(file);
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(
                            null,
                            FileUtil.getLang("miniWindow.fileManager.popMenu.openFileOnOutside.warring", file.getPath(), e.getMessage()),
                            FileUtil.getLang("miniWindow.fileManager.popMenu.openFileOnOutside.warringTitle"),
                            JOptionPane.WARNING_MESSAGE,
                            self.getIcon()
                    );
                    log.error("打开文件失败 -> ", e);
                }
            });
            popupMenu.add(openOnOutside);
            JMenuItem pasteFile = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.paste"));
            pasteFile.addActionListener((e) -> {
                try {
                    ClipboardUtil.ClipboardContent content = ClipboardUtil.getClipboardContent();
                    if (content.isFileList()) {
                        for (File file1 : content.getFileList()) {
                            {
                                try {
                                    if (!FileUtil.copyFileOrDir(file1.getAbsolutePath(), file.getAbsolutePath())) {
                                        log.error("复制时出现错误 - {} >> {}", file1.getPath(), file1);
                                    }
                                } catch (IllegalArgumentException | IOException ex) {
                                    log.error("禁止将文件夹复制到自身的子目录中");
                                }
                            }
                        }
                        if (isCut) {
                            if (!FileUtil.moveToRecycleBin(cutPath)) {
                                log.error("在剪切文件时删除原路径文件失败 - {}", cutPath);
                            }
                            isCut = false;
                            if (file.isDirectory()) {
                                refreshTreeAsync(selectedNode, currentModel, tree);
                            } else {
                                refreshTreeAsync((DefaultMutableTreeNode) selectedNode.getParent(), currentModel, tree);
                            }
                            refreshTreeAsync(cutNode, currentModel, tree);
                        }
                    }
                } catch (UnsupportedFlavorException | IOException ex) {
                    log.error("获取剪贴板内容失败 -> ", ex);
                }
            });
            popupMenu.add(pasteFile);
            JMenuItem cutFile = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.cut"));
            cutFile.addActionListener((e) -> {
                if (!ClipboardUtil.copyFileToClipboard(file)) {
                    log.error("剪切文件失败 - {}", file.getPath());
                }
                isCut = true;
                cutPath = file.getAbsolutePath();
                cutNode = selectedNode;
            });
            popupMenu.add(cutFile);
            JMenuItem copyFile = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.copyFile"));
            copyFile.addActionListener((e) -> {
                boolean ret = ClipboardUtil.copyFileToClipboard(file);
                if (!ret) {
                    JOptionPane.showMessageDialog(
                            null,
                            FileUtil.getLang("miniWindow.fileManager.popMenu.copyFile.fail", file.getPath()),
                            FileUtil.getLang("tip.error"),
                            JOptionPane.ERROR_MESSAGE,
                            self.getIcon()
                    );
                }
            });
            popupMenu.add(copyFile);
            JMenu copyFileOther = new JMenu(FileUtil.getLang("miniWindow.fileManager.popMenu.copy"));
            JMenuItem copyFilePath = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.copy.path"));
            copyFilePath.addActionListener(e -> ClipboardUtil.copyTextToClipboard(file.getPath()));
            copyFileOther.add(copyFilePath);
            JMenuItem copyFileName = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.copy.name"));
            copyFileName.addActionListener(e -> ClipboardUtil.copyTextToClipboard(file.getName()));
            copyFileOther.add(copyFileName);
            popupMenu.add(copyFileOther);
            JMenuItem copyLocalFilePath = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.copy.loaclPath"));
            copyLocalFilePath.addActionListener(e -> ClipboardUtil.copyTextToClipboard(rootFile.toPath().relativize(file.toPath()).toString()));
            copyFileOther.add(copyLocalFilePath);
            copyFileOther.addSeparator();
            JMenuItem copyFileSize = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.copy.size"));
            try {
                BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
                copyFileSize.addActionListener(e -> ClipboardUtil.copyTextToClipboard(FileUtil.formatFileSize(attrs.size())));
                copyFileOther.add(copyFileSize);
                JMenuItem copyFileCreaterTime = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.copy.createTime"));
                copyFileCreaterTime.addActionListener(e -> ClipboardUtil.copyTextToClipboard(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .format(new Date(attrs.creationTime().toMillis()))));
                copyFileOther.add(copyFileCreaterTime);
                JMenuItem copyLastModifiedTime = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.copy.lastModifiedTime"));
                copyLastModifiedTime.addActionListener(e -> ClipboardUtil.copyTextToClipboard(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .format(new Date(attrs.lastModifiedTime().toMillis()))));
                copyFileOther.add(copyLastModifiedTime);
                JMenuItem copyLastAccessTime = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.copy.lastAccessTime"));
                copyLastAccessTime.addActionListener(e -> ClipboardUtil.copyTextToClipboard(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .format(new Date(attrs.lastAccessTime().toMillis()))));
                copyFileOther.add(copyLastAccessTime);
                JMenuItem copyFileOwner = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.copy.owner"));
                copyFileOwner.addActionListener(e -> {
                    try {
                        ClipboardUtil.copyTextToClipboard(Files.getFileAttributeView(file.toPath(), FileOwnerAttributeView.class)
                                .getOwner().getName());
                    } catch (IOException ex) {
                        log.error("获取文件拥有者时发生错误 - {} -> ", file.getPath(), ex);
                    }
                });
                copyFileOther.add(copyFileOwner);
                JMenuItem copyFileAll = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.copy.all"));
                copyFileAll.addActionListener(e -> ClipboardUtil.copyTextToClipboard(PathInfoFormatter.getFormattedPathInfo(file.toPath())));
                copyFileOther.add(copyFileAll);
            } catch (IOException e) {
                log.error("读取文件时发生错误 - {} -> ", file.getPath(), e);
            }
            JMenuItem deleteFile = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.delete"));
            deleteFile.addActionListener(e -> {
                try {
                    if (!FileUtil.deleteWithConfirm(file.getPath(), null)) {
                        log.error("删除文件时发生错误 - {}", file.getPath());
                    }
                    refreshTreeAsync((DefaultMutableTreeNode) selectedNode.getParent(), currentModel, tree);
                } catch (IOException ex) {
                    log.error("删除文件时发生错误 - {} -> ", file.getPath(), ex);
                }
            });
            popupMenu.add(deleteFile);
            JMenuItem renameFile = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.renameFile"));
            renameFile.addActionListener(e -> {
                RenameFile dialog = new RenameFile(file.toPath());
                dialog.setSize(600, 200);
                dialog.setLocationRelativeTo(null);
                dialog.setVisible(true);
                if (file.isDirectory()) {
                    refreshTreeAsync(selectedNode, currentModel, tree);
                } else {
                    refreshTreeAsync((DefaultMutableTreeNode) selectedNode.getParent(), currentModel, tree);
                }
            });
            popupMenu.add(renameFile);
            popupMenu.addSeparator();
            if (file.isDirectory()) {
                JMenuItem refresh = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.refresh"));
                refresh.addActionListener(e -> refreshTreeAsync(selectedNode, currentModel, tree));
                popupMenu.add(refresh);
            }
            JMenuItem expand = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.expand"));
            expand.addActionListener(e -> {
                Work worker = new Work(FileUtil.getLang("miniWindow.fileManager.loadAll.workerName", "?", "正在统计"));
                worker.setStatus(IndicatorStatus.WAITING_OTHER);
                Main.getWorkController().addWork(worker);
                Main.getWorkController().showInfo();
                String taskID = FileUtil.countAllFilesAsync(file, (count) -> {
                    worker.setMaxValue(count);
                    worker.setValue(0);
                    worker.setStatus(IndicatorStatus.RUNNING);
                    worker.setName(FileUtil.getLang("miniWindow.fileManager.loadAll.workerName", "?", String.valueOf(count)));
                    if (count > MIN_FILE_COUNT) {
                        int ret = JOptionPane.showConfirmDialog(
                                null,
                                FileUtil.getLang("miniWindow.fileManager.loadAll.question", String.valueOf(count)),
                                FileUtil.getLang("tip.question"),
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.QUESTION_MESSAGE
                        );
                        if (ret != JOptionPane.YES_OPTION) {
                            Main.getWorkController().removeWork(worker);
                            return;
                        }
                    }
                    loadAllChildNodes(selectedNode, worker, currentModel, () -> {
                        worker.setName(FileUtil.getLang("miniWindow.fileManager.popMenu.expanding"));
                        worker.getMissionTip().setWaiting(true);
                        worker.setStatus(IndicatorStatus.WAITING_OTHER);
                        JTreeExpandCollapseUtil.expandAllChildNodesAsync(tree, selectedNode);
                        Main.getWorkController().removeWork(worker);
                        loadingCount.set(0);
                    });
                });
                worker.setOperationCallback(new MissionTip.OperationCallback() {
                    @Override
                    public boolean onPause() {
                        return FileUtil.FILE_IO_EXECUTOR.pauseTask(taskID);
                    }

                    @Override
                    public boolean onResume() {
                        return FileUtil.FILE_IO_EXECUTOR.resumeTask(taskID);
                    }

                    @Override
                    public boolean onStop() {
                        boolean cancel = FileUtil.FILE_IO_EXECUTOR.cancelTask(taskID);
                        if (cancel) Main.getWorkController().removeWork(worker);
                        return cancel;
                    }
                });
            });
            expand.setEnabled(loadingCount.get() <= 0);
            popupMenu.add(expand);
        }
    }

    private void openFiles(JTree currentTree, DefaultTreeModel currentModel) {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) currentTree.getLastSelectedPathComponent();
        if (selectedNode == null || selectedNode.isRoot() || !isNotLoad(selectedNode)) return;

        Object nodeObj = selectedNode.getUserObject();
        if (nodeObj instanceof FileData(File file) && file.isDirectory()) {
            refreshTreeAsync(selectedNode, currentModel, currentTree);
        }
    }

    private boolean isNotLoad(DefaultMutableTreeNode node) {
        if (node.getChildCount() == 0) return true;
        if (node.getChildCount() == 1) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(0);
            return child.getUserObject() == null;
        } else {
            return false;
        }
    }

    public void refreshTreeAsync(DefaultMutableTreeNode node, DefaultTreeModel currentModel, JTree tree) {
        refreshTreeAsync(node, currentModel, tree, null);
    }

    public void refreshTreeAsync(DefaultMutableTreeNode node, DefaultTreeModel currentModel, JTree tree, Runnable callBackFunction) {
        WorkControllableThreadTask refreshWork = new WorkControllableThreadTask(
                FileUtil.getLang("miniWindow.fileManager.popMenu.workerName"),
                "",
                FileUtil.FILE_IO_EXECUTOR
        );

        ReentrantLock refreshLock = new ReentrantLock();
        Condition addWorkDone = refreshLock.newCondition();

        try {
            refreshLock.lock();
            ControllableThreadTask<Void> refreshTask = new ControllableThreadTask<>() {
                @Override
                protected Void doWork() throws InterruptedException {
                    refreshTree(node, tree, currentModel);
                    SwingUtilities.invokeLater(() -> {
                        currentModel.nodeChanged(node);
                        if (callBackFunction != null) callBackFunction.run();
                    });

                    refreshLock.lock();
                    try {
                        if (!addWorkDone.await(1000, TimeUnit.MILLISECONDS)) {
                            log.error("刷新线程等待时出现错误");
                        }
                        Main.getWorkController().removeWork(refreshWork);
                    } finally {
                        refreshLock.unlock();
                    }
                    return null;
                }
            };

            String taskID = FileUtil.FILE_IO_EXECUTOR.submit(refreshTask);
            refreshWork.setTaskID(taskID);
            Main.getWorkController().addWork(refreshWork);

            addWorkDone.signal();

        } finally {
            refreshLock.unlock();
        }
    }

    public void refreshTreeSync(DefaultMutableTreeNode node, DefaultTreeModel currentModel, JTree tree) {
        refreshTree(node, tree, currentModel);
        SwingUtilities.invokeLater(() -> {
            currentModel.nodeChanged(node);
        });
    }

    private void refreshTree(DefaultMutableTreeNode node, JTree tree, DefaultTreeModel currentModel) {
        if (!(node.getUserObject() instanceof FileData(File folder))) {
            return;
        }
        if (!folder.isDirectory()) {
            return;
        }

        File[] filesArray = folder.listFiles();
        List<File> folderFiles = filesArray == null ? new ArrayList<>() : new ArrayList<>(List.of(filesArray));
        if (folderFiles.isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                Enumeration<TreeNode> childNodesEnum = node.children();
                List<DefaultMutableTreeNode> removeNodes = new ArrayList<>();
                while (childNodesEnum.hasMoreElements()) {
                    DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) childNodesEnum.nextElement();
                    removeNodes.add(childNode);
                }
                for (DefaultMutableTreeNode removeNode : removeNodes) {
                    currentModel.removeNodeFromParent(removeNode);
                }
            });
            return;
        }

        Set<File> fileSet = new HashSet<>(folderFiles);
        List<DefaultMutableTreeNode> removeNodes = new ArrayList<>();
        Enumeration<TreeNode> childNodesEnum = node.children();
        while (childNodesEnum.hasMoreElements()) {
            TreeNode childNode = childNodesEnum.nextElement();
            if (!(childNode instanceof DefaultMutableTreeNode childMutableNode)) {
                continue;
            }
            if ((childMutableNode.getUserObject() instanceof FileData(File nodeFile))) {
                if (fileSet.contains(nodeFile)) {
                    folderFiles.remove(nodeFile);
                    if (nodeFile.isDirectory() && isNodeVisibleInViewport(tree, childMutableNode)) {
                        refreshTree(childMutableNode, tree, currentModel);
                    }
                } else {
                    removeNodes.add(childMutableNode);
                }
            } else {
                removeNodes.add(childMutableNode);
            }
        }

        SwingUtilities.invokeLater(() -> {
            for (DefaultMutableTreeNode removeNode : removeNodes) {
                try {
                    currentModel.removeNodeFromParent(removeNode);
                } catch (Exception ignored) {
                }
            }

            List<File> files = new ArrayList<>();
            List<File> folders = new ArrayList<>();
            folderFiles.sort((o1, o2) -> sort(o1.getName(), o2.getName()));
            for (File folderFile : folderFiles) {
                if (folderFile.isDirectory()) {
                    folders.add(folderFile);
                } else if (folderFile.isFile()) {
                    files.add(folderFile);
                }
            }
            folderFiles.clear();
            folderFiles.addAll(folders);
            folderFiles.addAll(files);
            if (node.getChildCount() == 0) {
                for (File folderFile : folderFiles) {
                    currentModel.insertNodeInto(new DefaultMutableTreeNode(new FileData(folderFile)), node, node.getChildCount());
                }
                return;
            }

            int index = -1, maxIndex;
            for (File folderFile : folderFiles) {
                maxIndex = node.getChildCount() - 1;
                while (true) {
                    index++;
                    if (index > maxIndex) {
                        currentModel.insertNodeInto(new DefaultMutableTreeNode(new FileData(folderFile)), node, index);
                        break;
                    }
                    if (((DefaultMutableTreeNode) node.getChildAt(index)).getUserObject() instanceof FileData(
                            File cf
                    )) {
                        if (folderFile.isDirectory() && cf.isFile()) {
                            currentModel.insertNodeInto(new DefaultMutableTreeNode(new FileData(folderFile)), node, index);
                            break;
                        }
                        if (folderFile.isFile() && cf.isDirectory()) continue;
                        if (sort(folderFile.getName(), cf.getName()) < 0) {
                            currentModel.insertNodeInto(new DefaultMutableTreeNode(new FileData(folderFile)), node, index);
                            break;
                        }
                    }
                }
            }
        });
    }

    public static int sort(String a, String b) {
        if (a.equals(b)) return 0;
        int minLength = Math.min(a.length(), b.length());
        for (int i = 0; i < minLength; i++) {
            char ca = a.charAt(i), cb = b.charAt(i);
            if (ca + ('A' - 'a') == cb) return -1;
            if (('a' <= ca && ca <= 'z') || ('A' <= ca && ca <= 'Z')) {
                ca = Character.toLowerCase(ca);
            }
            if (('a' <= cb && cb <= 'z') || ('A' <= cb && cb <= 'Z')) {
                cb = Character.toLowerCase(cb);
            }
            int r = Integer.compare(ca, cb);
            if (r == 0) continue;
            return r;
        }
        return Integer.compare(a.length(), b.length());
    }

    public static boolean isNodeVisibleInViewport(JTree tree, DefaultMutableTreeNode node) {
        if (node == null || tree == null) {
            return false;
        }

        TreePath nodePath = new TreePath(node.getPath());

        if (!tree.isVisible(nodePath)) {
            return false;
        }

        try {
            Rectangle nodeRect = tree.getPathBounds(nodePath);
            if (nodeRect == null) {
                return false;
            }

            Rectangle viewportRect = tree.getVisibleRect();

            return nodeRect.intersects(viewportRect);
        } catch (Exception e) {
            return false;
        }
    }

    private void loadAllChildNodes(DefaultMutableTreeNode targetNode, DefaultTreeModel currentModel, Runnable callback) {
        if (targetNode == null || currentModel == null) {
            if (callback != null) callback.run();
            return;
        }
        if (!isLoading.compareAndSet(false, true)) {
            log.warn("文件加载中，请勿重复触发全量加载");
            if (callback != null) callback.run();
            return;
        }

        Object userData = targetNode.getUserObject();
        if (!(userData instanceof FileData(File file)) || !file.isDirectory()) {
            isLoading.set(false);
            JOptionPane.showMessageDialog(null,
                    FileUtil.getLang("miniWindow.fileManager.loadAll.notFolder"),
                    FileUtil.getLang("tip.warning"),
                    JOptionPane.WARNING_MESSAGE,
                    self.getIcon()
            );
            if (callback != null) callback.run();
            return;
        }

        ControllableThreadTask<Void> task = new ControllableThreadTask<>() {
            @Override
            protected Void doWork() {
                try {
                    recursiveLoadAllNodes(targetNode, currentModel, file);

                    SwingUtilities.invokeLater(() -> {
                        currentModel.reload(targetNode);
                        log.info("全量加载完成：{}", file.getPath());
                        if (callback != null) callback.run();
                    });
                } catch (Exception e) {
                    log.error("全量加载文件节点失败", e);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null,
                                FileUtil.getLang("miniWindow.fileManager.loadAll.failed", file.getPath(), e.getMessage()),
                                FileUtil.getLang("tip.error"),
                                JOptionPane.ERROR_MESSAGE,
                                self.getIcon()
                        );
                        if (callback != null) callback.run();
                    });
                } finally {
                    isLoading.set(false);
                }
                return null;
            }
        };

        FileUtil.FILE_IO_EXECUTOR.submit(task);
    }

    private void recursiveLoadAllNodes(DefaultMutableTreeNode parentNode, DefaultTreeModel model, File parentFile) throws Exception {
        SwingUtilities.invokeLater(parentNode::removeAllChildren);

        List<File> directFiles = FileUtil.listFilesOnce(parentFile);
        if (directFiles == null || directFiles.isEmpty()) {
            SwingUtilities.invokeLater(() -> {
                parentNode.add(new DefaultMutableTreeNode(FileUtil.getLang("miniWindow.fileManager.emptyFolders")));
                model.reload(parentNode);
            });
            return;
        }

        for (File childFile : directFiles) {
            DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(new FileData(childFile));

            if (childFile.isDirectory()) {
                recursiveLoadAllNodes(childNode, model, childFile);
            }

            SwingUtilities.invokeLater(() -> {
                parentNode.add(childNode);
                model.nodeChanged(parentNode);
            });

            loadingCount.set(loadingCount.get() + 1);
            Thread.sleep(1);
        }
    }

    private void loadAllChildNodes(DefaultMutableTreeNode targetNode, Work worker, DefaultTreeModel currentModel, Runnable callback) {
        loadingCount.set(0);
        worker.setWorkUpdateCallBack(() -> {
            worker.setValue(loadingCount.get());
            worker.setName(FileUtil.getLang("miniWindow.fileManager.loadAll.workerName", loadingCount.toString(), String.valueOf(worker.getMaxValue())));
        });
        loadAllChildNodes(targetNode, currentModel, callback);
    }

    private static class FileTreeRenderer extends RoundLabel implements TreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            this.setBackground(Setting.BACKGROUND_COLOR);
            this.setOpaque(selected);

            if (node.getUserObject() instanceof FileData(File file)) {
                this.setText(file.getName());
                this.setIcon(new ImageIcon(FileUtil.getFileIcon(file, 18, 18, row == 0, expanded)));
            } else if (node.getUserObject() instanceof String text) {
                this.setText(text);
                this.setIcon(null);
            } else {
                this.setText(FileUtil.getLang("miniWindow.fileManager.loading"));
                this.setIcon(FileUtil.getLoadingIcon());
            }

            if (selected) {
                this.setForeground(UIManager.getColor("Tree.selectionForeground"));
                this.setBackground(UIManager.getColor("Tree.selectionBackground"));
            } else {
                this.setForeground(UIManager.getColor("Tree.textForeground"));
            }

            this.setOpaque(false);
            this.setFocusable(false);
            this.setTransferHandler(null);

            return this;
        }
    }

    public record FileData(File file) {
    }

}
