package org.bxwbb.Util.DragDrop;

import org.bxwbb.MiniWindow.FileManager;
import org.bxwbb.Util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileTransferHandler extends TransferHandler {

    private static final Logger log = LoggerFactory.getLogger(FileTransferHandler.class);
    private final AtomicBoolean isRefresh = new AtomicBoolean(false);

    @Override
    public boolean canImport(TransferSupport support) {
        return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        JTree tree = (JTree) c;
        TreePath[] paths = tree.getSelectionPaths();
        if (paths != null) {
            List<File> fileList = new ArrayList<>();
            for (TreePath path : paths) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                if (node.getUserObject() instanceof FileManager.FileData(var nodeFile)) {
                    fileList.add(nodeFile);
                }
            }
            try {
                return new SystemFileTransferable(fileList);
            } catch (Exception e) {
                log.error("创建系统文件传输对象失败 -> ", e);
            }
        }
        return null;
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
        super.exportDone(source, data, action);

        if (!isRefresh.get()) {
            JTree tree = (JTree) source;
            FileManager fileManager = (FileManager) tree.getParent().getParent().getParent().getParent();
            fileManager.refreshTreeAsync((DefaultMutableTreeNode) tree.getModel().getRoot(), (DefaultTreeModel) tree.getModel(), tree, () -> isRefresh.set(false));
            isRefresh.set(true);
        }
    }


    @Override
    public int getSourceActions(JComponent c) {
        return COPY_OR_MOVE;
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (canImport(support)) {
            try {
                JTree tree = (JTree) support.getComponent();
                TreePath treePath = tree.getPathForLocation(support.getDropLocation().getDropPoint().x, support.getDropLocation().getDropPoint().y);
                if (treePath != null) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
                    List<File> fileList = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (node.getUserObject() instanceof FileManager.FileData(File nodeFile)) {
                        if (nodeFile.isDirectory()) {
                            for (File file : fileList) {
                                FileUtil.copyFileOrDir(file.getAbsolutePath(), nodeFile.getAbsolutePath());
                                if (support.getDropAction() == MOVE) {
                                    FileUtil.moveToRecycleBin(file.getPath());
                                }
                            }
                        } else {
                            for (File file : fileList) {
                                FileUtil.copyFileOrDir(file.getAbsolutePath(), nodeFile.getParentFile().getAbsolutePath());
                            }
                        }
                        if (isRefresh.get()) {
                            FileManager fileManager = (FileManager) tree.getParent().getParent().getParent().getParent();
                            fileManager.refreshTreeAsync((DefaultMutableTreeNode) tree.getModel().getRoot(), (DefaultTreeModel) tree.getModel(), tree, () -> isRefresh.set(false));
                            isRefresh.set(true);
                        }
                        return true;
                    }
                }
            } catch (Exception e) {
                log.error("处理拖拽文件时出现错误 -> ", e);
            }
        }
        return false;
    }
}
