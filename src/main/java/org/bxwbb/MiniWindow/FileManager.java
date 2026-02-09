package org.bxwbb.MiniWindow;

import org.bxwbb.Setting;
import org.bxwbb.UI.RoundLabel;
import org.bxwbb.Util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;
import java.util.Objects;

public class FileManager extends MiniWindow {

    private static final Logger log = LoggerFactory.getLogger(FileManager.class);

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
                File file = FileUtil.chooseSingleFolder(parent, FileUtil.getLang("miniWindow.fileManager.selectFile"));
                if (file == null) {
                    return;
                }
                try {
                    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(file);
                    DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
                    List<File> files = FileUtil.listFilesOnce(file);
                    if (files != null && !files.isEmpty()) {
                        for (File childFile : files) {
                            DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(childFile);
                            rootNode.add(childNode);
                        }
                    }
                    JTree fileTree = new JTree(rootNode);
                    fileTree.setRootVisible(true);
                    fileTree.setShowsRootHandles(true);
                    fileTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

                    fileTree.setMinimumSize(new Dimension(0, Short.MAX_VALUE));
                    fileTree.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
                    fileTree.setBackground(Setting.BACKGROUND_COLOR);

                    TreeSelectionModel treeSelectionModel = fileTree.getSelectionModel();
                    treeSelectionModel.addTreeSelectionListener(treeSelectionEvent -> {
                        openFiles(fileTree);
                    });
                    fileTree.addTreeExpansionListener(new TreeExpansionListener() {
                        @Override
                        public void treeExpanded(TreeExpansionEvent event) {
                            SwingUtilities.invokeLater(() -> {
                                TreePath expandedPath = event.getPath();
                                DefaultMutableTreeNode expandedNode = (DefaultMutableTreeNode) expandedPath.getLastPathComponent();
                                autoExpandSingleChildNode(expandedNode, fileTree, treeModel);
                            });
                        }

                        @Override
                        public void treeCollapsed(TreeExpansionEvent event) {
                            // 折叠事件无需处理
                        }
                    });

                    fileTree.setCellRenderer(new FileTreeRenderer());
                    JScrollPane scrollPane = new JScrollPane(fileTree);
                    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
                    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                    scrollPane.setBorder(BorderFactory.createEtchedBorder());

                    scrollPane.getViewport().setMaximumSize(new Dimension(Integer.MAX_VALUE, 400)); // 可视高度限制400，可自定义

                    JPanel centerPanel = getCenterPanel();
                    centerPanel.removeAll();
                    centerPanel.setLayout(new BorderLayout());
                    centerPanel.add(scrollPane, BorderLayout.CENTER);

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
        Label label = new Label(FileUtil.getLang("miniWindow.fileManager.pleaseSelectFile"));
        getCenterPanel().add(label, BorderLayout.CENTER);
        getTopPanel().add(selectFolderButton);
    }

    private static void openFiles(JTree fileTree) {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) fileTree.getLastSelectedPathComponent();
        if (selectedNode == null || selectedNode.isRoot()) return;
        Object nodeObj = selectedNode.getUserObject();
        if (nodeObj instanceof File selectedFile && selectedFile.isDirectory()) {
            System.out.println("选中的文件/文件夹：" + selectedFile.getAbsolutePath());
            List<File> selectedFiles = FileUtil.listFilesOnce(selectedFile);
            if (selectedFiles != null && !selectedFiles.isEmpty()) {
                for (File selectedFile1 : selectedFiles) {
                    DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(selectedFile1);
                    selectedNode.add(childNode);
                }
            }
        }
    }

    /**
     * 加载指定文件的子文件
     */
    private static void loadSubFiles(DefaultMutableTreeNode node) {
        Object userData = node.getUserObject();
        if (userData instanceof File file && file.isDirectory()) {
            File nodeFile = (File) userData;
            FileUtil.FILE_IO_EXECUTOR.submit(() -> {
                List<File> files = FileUtil.listFilesOnce(nodeFile);
            });
        }
    }

    private void autoExpandSingleChildNode(DefaultMutableTreeNode node, JTree fileTree, DefaultTreeModel treeModel) {
        Object object = node.getUserObject();

        if (object instanceof File file && file.isDirectory() && node.getChildCount() == 1) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) node.getChildAt(0);
            File childFile = (File) childNode.getUserObject();
            System.out.println(childFile);
            List<File> childFiles = FileUtil.listFilesOnce(childFile);
            System.out.println(childFiles);
            if (childFiles != null && !childFiles.isEmpty()) {
                for (File f : childFiles) {
                    childNode.add(new DefaultMutableTreeNode(f));
                }
            }
            // 刷新子节点模型
            treeModel.reload(node);

            fileTree.expandPath(new TreePath(node));
            fileTree.updateUI();
            autoExpandSingleChildNode(childNode, fileTree, treeModel);
        }
    }

    private static class FileTreeRenderer extends RoundLabel implements TreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            if (node.getUserObject() instanceof File file) {
                this.setBackground(Setting.BACKGROUND_COLOR);
                this.setText(file.getName());
                this.setIcon(new ImageIcon(FileUtil.getFileIcon(file, 18, 18, row == 0, expanded)));
                this.setToolTipText(file.getPath());
            }
            return this;
        }
    }

}
