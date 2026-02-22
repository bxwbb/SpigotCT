package org.bxwbb.Briefcase;

import org.bxwbb.Main;
import org.bxwbb.Util.DragDrop.SystemFileTransferable;
import org.bxwbb.Util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class BriefcaseTransferHandler extends TransferHandler {

    private static final Logger log = LoggerFactory.getLogger(BriefcaseTransferHandler.class);

    @Override
    public boolean canImport(TransferSupport support) {
        return true;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        JTree tree = (JTree) c;
        TreePath[] paths = tree.getSelectionPaths();
        if (paths != null) {
            for (TreePath path : paths) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                BriefcaseNodeData briefcaseNodeData = (BriefcaseNodeData) node.getUserObject();
                return switch (briefcaseNodeData.getNodeValueType()) {
                    case STRING -> createToString(c);
                    case FILES -> createToFiles(c);
                    case OBJECT -> createToObject(c);
                };
            }
        }
        return null;
    }

    private Transferable createToString(JComponent c) {
        JTree tree = (JTree) c;
        TreePath[] paths = tree.getSelectionPaths();
        if (paths != null) {
            StringBuilder stringBuilder = new StringBuilder();
            for (TreePath path : paths) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                BriefcaseNodeData briefcaseNodeData = (BriefcaseNodeData) node.getUserObject();
                stringBuilder.append((String) briefcaseNodeData.getValue()).append("\n");
            }
            return new StringSelection(stringBuilder.toString());
        }
        return null;
    }

    private Transferable createToFiles(JComponent c) {
        JTree tree = (JTree) c;
        TreePath[] paths = tree.getSelectionPaths();
        if (paths != null) {
            List<File> fileList = new ArrayList<>();
            for (TreePath path : paths) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                BriefcaseNodeData data = (BriefcaseNodeData) node.getUserObject();
                fileList.addAll((List<File>) data.getValue());
            }
            try {
                return new SystemFileTransferable(fileList);
            } catch (Exception e) {
                log.error("创建系统文件传输对象失败 -> ", e);
            }
        }
        return null;
    }

    private Transferable createToObject(JComponent c) {
        JTree tree = (JTree) c;
        TreePath[] paths = tree.getSelectionPaths();
        if (paths != null) {
            if (paths.length == 1) {
                return (Transferable) (((BriefcaseNodeData) ((DefaultMutableTreeNode) paths[0].getLastPathComponent()).getUserObject()).getValue());
            }
            List<Transferable> list = new ArrayList<>();
            for (TreePath path : paths) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                BriefcaseNodeData data = (BriefcaseNodeData) node.getUserObject();
                list.add((Transferable) data.getValue());
            }
            log.warn("拖拽数据已经生成但是无法传输,因为技术问题请尝试拖拽单个元素 - {}", list);
            return null;
        }
        return null;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return COPY;
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (canImport(support)) {
            try {
                JTree tree = (JTree) support.getComponent();
                TreePath treePath = tree.getPathForLocation(support.getDropLocation().getDropPoint().x, support.getDropLocation().getDropPoint().y);
                if (treePath != null) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
                    if (support.getDataFlavors().length == 0) return false;
                    DataFlavor dataFlavor = support.getDataFlavors()[0];
                    if (dataFlavor.isFlavorJavaFileListType()) {
                        List<File> fileList = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                        DefaultMutableTreeNode newNode = new DefaultMutableTreeNode();
                        BriefcaseNodeData briefcaseNodeData = new BriefcaseNodeData(fileList.getFirst().getName() + ((fileList.size() == 1) ? "" : FileUtil.getLang("briefcase.transferHandler.files.more", String.valueOf(fileList.size()))), fileList, "", BriefcaseNodeType.VALUE);
                        briefcaseNodeData.setNodeValueType(BriefcaseNodeValueType.FILES);
                        newNode.setUserObject(briefcaseNodeData);
                        Main.briefcaseControl.addNode(newNode, node);
                        return true;
                    }
                    if (dataFlavor.isFlavorTextType()) {
                        String text = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                        DefaultMutableTreeNode newNode = new DefaultMutableTreeNode();
                        BriefcaseNodeData briefcaseNodeData = new BriefcaseNodeData(text , text, "", BriefcaseNodeType.VALUE);
                        briefcaseNodeData.setNodeValueType(BriefcaseNodeValueType.STRING);
                        newNode.setUserObject(briefcaseNodeData);
                        Main.briefcaseControl.addNode(newNode, node);
                        return true;
                    }
                    Transferable transferable = support.getTransferable();
                    DefaultMutableTreeNode newNode = new DefaultMutableTreeNode();
                    BriefcaseNodeData briefcaseNodeData = new BriefcaseNodeData(transferable.toString(), transferable, "", BriefcaseNodeType.VALUE);
                    briefcaseNodeData.setNodeValueType(BriefcaseNodeValueType.OBJECT);
                    newNode.setUserObject(briefcaseNodeData);
                    Main.briefcaseControl.addNode(newNode, node);
                    return true;
                }
            } catch (Exception e) {
                log.error("处理拖拽文件时出现错误 -> ", e);
            }
        }
        return false;
    }
}
