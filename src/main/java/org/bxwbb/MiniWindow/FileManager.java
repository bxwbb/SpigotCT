package org.bxwbb.MiniWindow;

import org.bxwbb.Main;
import org.bxwbb.Setting;
import org.bxwbb.Swing.CreateFile;
import org.bxwbb.Swing.CreateFolder;
import org.bxwbb.UI.IndicatorStatus;
import org.bxwbb.UI.MissionTip;
import org.bxwbb.UI.RoundLabel;
import org.bxwbb.Util.ClipboardUtil;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class FileManager extends MiniWindow {

    private static final Logger log = LoggerFactory.getLogger(FileManager.class);
    private final AtomicBoolean isLoading = new AtomicBoolean(false);
    private final AtomicInteger loadingCount = new AtomicInteger(0);
    // 询问用户文件数最低值
    private static final int MIN_FILE_COUNT = 10000;

    private File rootFile;

    public FileManager() {
        super(FileManager.class);
    }

    // 剪切路径
    private String cutPath;
    // 是否开启剪切功能
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

                    refreshTaskID = ScheduledTaskManager.getInstance().startFixedRateTask(1000, () -> {
                        refreshTreeAsync(getParentNode(rootNode), newTreeModel);
                        System.out.println("刷新啦");
                    });
                    newFileTree.getSelectionModel().addTreeSelectionListener(treeSelectionEvent -> openFiles(newFileTree, newTreeModel));
                    newFileTree.addTreeExpansionListener(new TreeExpansionListener() {
                        @Override
                        public void treeExpanded(TreeExpansionEvent event) {
                            SwingUtilities.invokeLater(() -> {
                                TreePath expandedPath = event.getPath();
                                DefaultMutableTreeNode expandedNode = (DefaultMutableTreeNode) expandedPath.getLastPathComponent();
                                if (isNotLoad(expandedNode)) loadSubFiles(expandedNode, newTreeModel);
                                autoExpandSingleChildNode(expandedNode, newFileTree, newTreeModel);
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

                    loadSubFiles(rootNode, newTreeModel);

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

        Label initLabel = new Label(FileUtil.getLang("miniWindow.fileManager.pleaseSelectFile"));
        getCenterPanel().add(initLabel, BorderLayout.CENTER);
        getTopPanel().add(selectFolderButton);
    }

    @Override
    public void delete() {
        ScheduledTaskManager.getInstance().stopTask(refreshTaskID);
    }

    private DefaultMutableTreeNode getParentNode(DefaultMutableTreeNode node) {
        return node.isRoot() ? node : (DefaultMutableTreeNode) node.getParent();
    }

    /**
     * 创建右键菜单
     *
     * @param popupMenu    右键菜单
     * @param selectedNode 选中的节点
     */
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
                                    if (FileUtil.copyFileOrDir(file1.getAbsolutePath(), file.getAbsolutePath())) {
                                        log.error("复制时出现错误 - {} >> {}", file1.getPath(), file1);
                                    }
                                } catch (IllegalArgumentException | IOException ex) {
                                    log.error("禁止将文件夹复制到自身的子目录中");
                                }
                            }
                        }
                    }
                } catch (UnsupportedFlavorException | IOException ex) {
                    log.error("获取剪贴板内容失败 -> ", ex);
                }
            });
            popupMenu.add(pasteFile);
            JMenuItem cutFile = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.cut"));
            cutFile.addActionListener((e) -> {
                if (ClipboardUtil.copyFileToClipboard(file)) {
                    log.error("剪切文件失败 - {}", file.getPath());
                }
                isCut = true;
                cutPath = file.getAbsolutePath();
            });
            popupMenu.add(cutFile);
            JMenuItem coptFile = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.copyFile"));
            coptFile.addActionListener((e) -> {
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
            popupMenu.add(coptFile);
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
                copyFileAll.addActionListener(e -> {
                    ClipboardUtil.copyTextToClipboard(PathInfoFormatter.getFormattedPathInfo(file.toPath()));
                });
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
                } catch (IOException ex) {
                    log.error("删除文件时发生错误 - {} -> ", file.getPath(), ex);
                }
            });
            popupMenu.add(deleteFile);
            popupMenu.addSeparator();
            if (file.isDirectory()) {
                JMenuItem refresh = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.refresh"));
                refresh.addActionListener(e -> refreshTreeAsync(selectedNode, currentModel));
                popupMenu.add(refresh);
            }
            JMenuItem expand = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.expand"));
            expand.addActionListener(e -> {
                Work worker = new Work(FileUtil.getLang("miniWindow.fileManager.loadAll.workerName", "?", "正在统计"));
                worker.setStatus(IndicatorStatus.WAITING_OTHER);
                Main.getWorkController().addWork(worker);
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

    /**
     * 打开文件：仅操作传入的当前树/模型（无成员变量引用，避免错乱）
     */
    private void openFiles(JTree currentTree, DefaultTreeModel currentModel) {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) currentTree.getLastSelectedPathComponent();
        if (selectedNode == null || selectedNode.isRoot() || !isNotLoad(selectedNode)) return;

        Object nodeObj = selectedNode.getUserObject();
        if (nodeObj instanceof FileData(File file) && file.isDirectory()) {
            loadSubFiles(selectedNode, currentModel);
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

    /**
     * 加载子文件：仅操作传入的当前节点/模型（完全隔离）
     */
    private void loadSubFiles(DefaultMutableTreeNode node, DefaultTreeModel currentModel) {
        if (!isNotLoad(node)) {
            isLoading.set(false);
            return;
        }

        if (!isLoading.compareAndSet(false, true)) {
            log.warn("文件加载中，请勿重复操作");
            return;
        }

        Object userData = node.getUserObject();
        if (!(userData instanceof FileData(File file)) || !file.isDirectory()) {
            isLoading.set(false);
            return;
        }

        node.removeAllChildren();
        if (node.getChildCount() == 0) {
            DefaultMutableTreeNode loadingNode = new DefaultMutableTreeNode();
            node.add(loadingNode);
        }
        SwingUtilities.invokeLater(() -> currentModel.reload(node));

        ControllableThreadTask<Void> task = new ControllableThreadTask<>() {
            @Override
            protected Void doWork() {
                try {
                    List<File> files = FileUtil.listFilesOnce(file);

                    SwingUtilities.invokeLater(() -> {
                        if (files != null && !files.isEmpty()) {
                            if (files.size() == 1 && files.getFirst().isDirectory()) {
                                DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(new FileData(files.getFirst()));
                                node.add(newNode);
                                loadSubFiles(newNode, currentModel);
                            } else {
                                for (File file1 : files) {
                                    DefaultMutableTreeNode addNode = new DefaultMutableTreeNode(new FileData(file1));
                                    if (file1.isDirectory()) {
                                        DefaultMutableTreeNode loadingNode = new DefaultMutableTreeNode();
                                        addNode.add(loadingNode);
                                    }
                                    node.add(addNode);
                                }
                            }
                        } else {
                            node.add(new DefaultMutableTreeNode(FileUtil.getLang("miniWindow.fileManager.emptyFolders")));
                        }
                        node.remove(0);
                        currentModel.reload(node);
                    });
                } catch (Exception e) {
                    log.error("加载子文件失败", e);
                    SwingUtilities.invokeLater(() -> {
                        node.remove(0);
                        node.add(new DefaultMutableTreeNode(FileUtil.getLang("miniWindow.fileManager.loadFailed") + e.getMessage()));
                        currentModel.reload(node);
                    });
                } finally {
                    isLoading.set(false);
                }
                return null;
            }
        };

        FileUtil.FILE_IO_EXECUTOR.submit(task);
    }

    /**
     * 自动展开唯一子节点：仅操作传入的当前对象
     */
    private void autoExpandSingleChildNode(DefaultMutableTreeNode node, JTree currentTree, DefaultTreeModel currentModel) {
        if (node == null || currentTree == null || currentModel == null) return;
        if (isNotLoad(node)) loadSubFiles(node, currentModel);
        if (node.getChildCount() != 1) return;

        DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) node.getFirstChild();
        Object childObj = childNode.getUserObject();
        if (!(childObj instanceof FileData) || !((FileData) childObj).file().isDirectory()) return;

        currentModel.reload(node);
        TreePath childPath = new TreePath(childNode.getPath());

        SwingUtilities.invokeLater(() -> {
            if (!currentTree.isExpanded(childPath)) {
                currentTree.expandPath(childPath);
                autoExpandSingleChildNode(childNode, currentTree, currentModel);
            }
        });
    }

//    private void refreshTree(DefaultMutableTreeNode node, DefaultTreeModel currentModel) {
//        node.removeAllChildren();
//        loadSubFiles(node, currentModel);
//    }

    /**
     * 刷新树：仅操作传入的当前节点/模型（完全隔离）
     *
     * @param node         当前节点
     * @param currentModel 当前树模型
     */
    private void refreshTreeAsync(DefaultMutableTreeNode node, DefaultTreeModel currentModel) {
        WorkControllableThreadTask refreshWork = new WorkControllableThreadTask(
                FileUtil.getLang("miniWindow.fileManager.popMenu.workerName"),
                "",
                FileUtil.FILE_IO_EXECUTOR
        );
        ControllableThreadTask<Void> refreshTask = new ControllableThreadTask<>() {
            @Override
            protected Void doWork() throws InterruptedException {
                Thread.sleep(500);
                refreshTree(node, currentModel);
                currentModel.reload(node);
                Main.getWorkController().removeWork(refreshWork);
                return null;
            }
        };
        String taskID = FileUtil.FILE_IO_EXECUTOR.submit(refreshTask);
        refreshWork.setTaskID(taskID);
        Main.getWorkController().addWork(refreshWork);
    }

    /**
     * 刷新树形节点：同步文件夹内容与树形节点（增删节点）+ 递归刷新未加载的文件夹节点
     * 优化点：移除冗余的nodeFileMap，消除静态检查提示，逻辑更简洁
     */
    private void refreshTree(DefaultMutableTreeNode node, DefaultTreeModel currentModel) {
        // 1. 校验节点数据（确保是文件夹，符合你的业务约定）
        if (!(node.getUserObject() instanceof FileData(File folder))) {
            return;
        }
        // 假设FileData有getFile()方法获取对应File
        if (!folder.isDirectory()) {
            return; // 按你要求：传入节点一定是文件夹，此处做兜底校验
        }

        // 2. 获取文件夹下的所有子文件（处理null，避免空指针）
        File[] filesArray = folder.listFiles();
        List<File> folderFiles = filesArray == null ? new ArrayList<>() : Arrays.asList(filesArray);
        if (folderFiles.isEmpty()) {
            node.removeAllChildren();
            node.add(new DefaultMutableTreeNode(FileUtil.getLang("miniWindow.fileManager.emptyFolders")));
            return;
        }
        if (isNotLoad(node)) node.removeAllChildren();
        // 转换为Set，方便快速判断文件是否存在
        Set<File> folderFileSet = new HashSet<>(folderFiles);

        // 3. 收集待删除的节点 + 标记已匹配的文件
        List<DefaultMutableTreeNode> deleteNodes = new ArrayList<>();
        Enumeration<TreeNode> childNodesEnum = node.children();

        // 遍历所有子节点，分类处理
        while (childNodesEnum.hasMoreElements()) {
            TreeNode childNode = childNodesEnum.nextElement();
            if (!(childNode instanceof DefaultMutableTreeNode childMutableNode)) {
                continue;
            }

            // 3.1 提取子节点对应的File
            File nodeFile = null;
            if (childMutableNode.getUserObject() instanceof FileData(File file)) {
                nodeFile = file;
            }

            // 3.2 情况1：节点无对应File 或 File已不存在 → 标记删除
            if (nodeFile == null || !folderFileSet.contains(nodeFile)) {
                deleteNodes.add(childMutableNode);
            } else {
                // 3.3 情况2：节点有对应File → 移除已匹配的File（剩余的就是需要新增的）
                folderFileSet.remove(nodeFile);

                // 3.4 递归刷新：如果子节点是文件夹且未加载 → 调用refreshTree
                if (nodeFile.isDirectory() && isNotLoad(childMutableNode)) {
                    refreshTree(childMutableNode, currentModel); // 递归刷新未加载的文件夹节点
                }
            }
        }

        // 4. 处理需要新增的节点（剩余的folderFileSet中的File都是未匹配的）
        for (File fileToAdd : folderFileSet) {
            DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(new FileData(fileToAdd));
            node.add(newNode);

            // 新增节点如果是文件夹且未加载 → 立即递归刷新（可选，按你的需求）
            if (fileToAdd.isDirectory() && isNotLoad(newNode)) {
                refreshTree(newNode, currentModel);
            }
        }

        // 5. 批量删除无效节点（有节点无文件）
        for (DefaultMutableTreeNode deleteNode : deleteNodes) {
            node.remove(deleteNode);
        }
    }

    /**
     * 修复版：递归加载指定文件夹节点下的所有层级子节点（仅加载，不展开）
     * 解决无限递归问题，完全隔离展开逻辑
     *
     * @param targetNode   目标文件夹节点
     * @param currentModel 当前树模型
     * @param callback     加载完成后的回调
     */
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

    /**
     * 修复版递归加载：仅加载节点，不触发展开、不调用autoExpandSingleChildNode
     *
     * @param parentNode 父节点
     * @param model      树模型
     * @param parentFile 父文件夹
     */
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

    /**
     * 渲染器：每次创建新实例，无状态共享
     */
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

            return this;
        }
    }

    /**
     * 文件数据载体：纯数据类，无状态
     *
     * @param file 改为final，避免被修改
     */
    public record FileData(File file) {
    }

}