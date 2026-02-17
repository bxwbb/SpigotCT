package org.bxwbb.Util;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.concurrent.ExecutionException;

/**
 * JTree 节点展开/折叠工具类（异步版本，适配大文件树）
 */
public class JTreeExpandCollapseUtil {

    /**
     * 【异步】递归展开指定节点的所有子节点（适合大文件树，避免UI卡顿）
     *
     * @param tree       目标JTree
     * @param targetNode 要展开的根节点
     * @param callback   展开完成后的回调（可选，比如提示用户）
     */
    public static void expandAllChildNodesAsync(JTree tree, DefaultMutableTreeNode targetNode, Runnable callback) {
        if (tree == null || targetNode == null) {
            if (callback != null) callback.run();
            return;
        }

        SwingWorker<Void, Void> expandWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                expandAllChildNodes(tree, targetNode);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    if (callback != null) {
                        callback.run();
                    }
                } catch (InterruptedException | ExecutionException e) {
                    JOptionPane.showMessageDialog(tree.getTopLevelAncestor(),
                            "展开节点失败：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        expandWorker.execute();
    }

    /**
     * 【异步】递归折叠指定节点的所有子节点（适合大文件树）
     *
     * @param tree         目标JTree
     * @param targetNode   要折叠的根节点
     * @param collapseSelf 是否折叠节点本身
     * @param callback     折叠完成后的回调（可选）
     */
    public static void collapseAllChildNodesAsync(JTree tree, DefaultMutableTreeNode targetNode,
                                                  boolean collapseSelf, Runnable callback) {
        if (tree == null || targetNode == null) {
            if (callback != null) callback.run();
            return;
        }

        SwingWorker<Void, Void> collapseWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                collapseAllChildNodes(tree, targetNode, collapseSelf);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    if (callback != null) {
                        callback.run();
                    }
                } catch (InterruptedException | ExecutionException e) {
                    JOptionPane.showMessageDialog(tree.getTopLevelAncestor(),
                            "折叠节点失败：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        collapseWorker.execute();
    }

    public static void collapseAllChildNodesAsync(JTree tree, DefaultMutableTreeNode targetNode) {
        collapseAllChildNodesAsync(tree, targetNode, true, null);
    }

    public static void expandAllChildNodesAsync(JTree tree, DefaultMutableTreeNode targetNode) {
        expandAllChildNodesAsync(tree, targetNode, null);
    }

    /**
     * 同步递归展开（供后台线程调用，不要直接在UI线程调用大节点）
     */
    private static void expandAllChildNodes(JTree tree, DefaultMutableTreeNode targetNode) {
        SwingUtilities.invokeLater(() -> tree.expandPath(new TreePath(targetNode.getPath())));

        for (int i = 0; i < targetNode.getChildCount(); i++) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) targetNode.getChildAt(i);
            expandAllChildNodes(tree, childNode);
        }
    }

    /**
     * 同步递归折叠（供后台线程调用）
     */
    private static void collapseAllChildNodes(JTree tree, DefaultMutableTreeNode targetNode, boolean collapseSelf) {
        for (int i = 0; i < targetNode.getChildCount(); i++) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) targetNode.getChildAt(i);
            collapseAllChildNodes(tree, childNode, true);
        }

        if (collapseSelf) {
            SwingUtilities.invokeLater(() -> tree.collapsePath(new TreePath(targetNode.getPath())));
        }
    }

    public static DefaultMutableTreeNode findNodeByName(JTree tree, String nodeName) {
        if (tree == null || nodeName == null || nodeName.isEmpty()) {
            return null;
        }
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        return findNodeRecursive(root, nodeName);
    }

    private static DefaultMutableTreeNode findNodeRecursive(DefaultMutableTreeNode parent, String nodeName) {
        if (parent.toString().equals(nodeName)) {
            return parent;
        }
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            DefaultMutableTreeNode found = findNodeRecursive(child, nodeName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}