package org.bxwbb.MiniWindow;

import org.bxwbb.Main;
import org.bxwbb.Setting;
import org.bxwbb.UI.IndicatorStatus;
import org.bxwbb.UI.RoundLabel;
import org.bxwbb.Util.FileUtil;
import org.bxwbb.Util.JTreeExpandCollapseUtil;
import org.bxwbb.WorkEventer.Work;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class FileManager extends MiniWindow {

    private static final Logger log = LoggerFactory.getLogger(FileManager.class);
    private final AtomicBoolean isLoading = new AtomicBoolean(false);
    private final AtomicInteger loadingCount = new AtomicInteger(0);
    // 询问用户文件数最低值
    private static final int MIN_FILE_COUNT = 10000;

    public FileManager() {
        super(FileManager.class);
    }

    @Override
    public void init() {
        Image image = new ImageIcon(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/FileManager/SelectFolder.png"))).getImage();
        JButton selectFolderButton = new JButton(new ImageIcon(image.getScaledInstance(20, 20, Image.SCALE_SMOOTH)));

        selectFolderButton.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                Component parent = getCenterPanel();
                File selectedFolder = FileUtil.chooseSingleFolder(parent, FileUtil.getLang("miniWindow.fileManager.selectFile"));
                if (selectedFolder == null) {
                    return;
                }

                try {
                    JPanel centerPanel = getCenterPanel();
                    centerPanel.removeAll();
                    centerPanel.revalidate();
                    centerPanel.repaint();

                    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(new FileData(selectedFolder));
                    DefaultTreeModel newTreeModel = new DefaultTreeModel(rootNode);

                    JTree newFileTree = new JTree(newTreeModel);
                    newFileTree.setRootVisible(true);
                    newFileTree.setShowsRootHandles(true);
                    newFileTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
                    newFileTree.setMinimumSize(new Dimension(0, Short.MAX_VALUE));
                    newFileTree.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
                    newFileTree.setBackground(Setting.BACKGROUND_COLOR);

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
                    JOptionPane.showMessageDialog(parent, FileUtil.getLang("tip.file.load.failed") + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
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

    /**
     * 创建右键菜单
     *
     * @param popupMenu    右键菜单
     * @param selectedNode 选中的节点
     */

    private void createPopMenu(JPopupMenu popupMenu, DefaultMutableTreeNode selectedNode, DefaultTreeModel currentModel, JTree tree) {
        if (selectedNode.getUserObject() instanceof FileData(File file)) {
            JMenuItem openOnOutside = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.openFileOnOutside"));
            openOnOutside.addActionListener(event -> {
                try {
                    FileUtil.openFile(file);
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(null, FileUtil.getLang("miniWindow.fileManager.popMenu.openFileOnOutside.warring", file.getPath(), e.getMessage()), FileUtil.getLang("miniWindow.fileManager.popMenu.openFileOnOutside.warringTitle"), JOptionPane.WARNING_MESSAGE);
                    log.error("打开文件失败 -> ", e);
                }
            });
            popupMenu.add(openOnOutside);
            popupMenu.addSeparator();
            JMenuItem refresh = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.refresh"));
            refresh.addActionListener(e -> refreshTree(selectedNode, currentModel));
            popupMenu.add(refresh);
            JMenuItem expand = new JMenuItem(FileUtil.getLang("miniWindow.fileManager.popMenu.expand"));
            expand.addActionListener(e -> {
                Work worker = new Work(FileUtil.getLang("miniWindow.fileManager.loadAll.workerName", "?", "正在统计")) {
                    @Override
                    public boolean onPause() {
                        return false;
                    }

                    @Override
                    public boolean onResume() {
                        return false;
                    }

                    @Override
                    public boolean onStop() {
                        return false;
                    }
                };
                worker.setStatus(IndicatorStatus.WAITING_OTHER);
                Main.getWorkController().addWork(worker);
                FileUtil.countAllFilesAsync(file, (count) -> {
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

        FileUtil.FILE_IO_EXECUTOR.submit(() -> {
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
        });
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

    private void refreshTree(DefaultMutableTreeNode node, DefaultTreeModel currentModel) {
        node.removeAllChildren();
        loadSubFiles(node, currentModel);
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
                    JOptionPane.WARNING_MESSAGE);
            if (callback != null) callback.run();
            return;
        }

        FileUtil.FILE_IO_EXECUTOR.submit(() -> {
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
                            JOptionPane.ERROR_MESSAGE);
                    if (callback != null) callback.run();
                });
            } finally {
                isLoading.set(false);
            }
        });
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