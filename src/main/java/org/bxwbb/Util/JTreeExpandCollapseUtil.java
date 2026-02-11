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
        // 空节点校验
        if (tree == null || targetNode == null) {
            if (callback != null) callback.run();
            return;
        }

        // 使用SwingWorker在后台线程执行展开逻辑（Swing官方推荐）
        SwingWorker<Void, Void> expandWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                // 后台线程：递归展开所有节点（耗时操作）
                expandAllChildNodes(tree, targetNode);
                return null;
            }

            @Override
            protected void done() {
                // EDT线程（UI线程）：展开完成后的操作
                try {
                    get(); // 捕获后台线程的异常
                    if (callback != null) {
                        callback.run(); // 执行回调
                    }
                } catch (InterruptedException | ExecutionException e) {
                    JOptionPane.showMessageDialog(tree.getTopLevelAncestor(),
                            "展开节点失败：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                    e.printStackTrace();
                }
            }
        };

        // 启动异步任务
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
            protected Void doInBackground() throws Exception {
                // 后台线程：递归折叠所有节点
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
                    e.printStackTrace();
                }
            }
        };

        collapseWorker.execute();
    }

    // 重载：默认折叠自身 + 无回调
    public static void collapseAllChildNodesAsync(JTree tree, DefaultMutableTreeNode targetNode) {
        collapseAllChildNodesAsync(tree, targetNode, true, null);
    }

    // 重载：无回调的展开方法
    public static void expandAllChildNodesAsync(JTree tree, DefaultMutableTreeNode targetNode) {
        expandAllChildNodesAsync(tree, targetNode, null);
    }

    // ==================== 核心递归逻辑（后台执行）====================

    /**
     * 同步递归展开（供后台线程调用，不要直接在UI线程调用大节点）
     */
    private static void expandAllChildNodes(JTree tree, DefaultMutableTreeNode targetNode) {
        // 必须在EDT线程更新UI（展开节点是UI操作）
        SwingUtilities.invokeLater(() -> tree.expandPath(new TreePath(targetNode.getPath())));

        // 递归展开子节点（后台线程遍历，EDT线程更新UI）
        for (int i = 0; i < targetNode.getChildCount(); i++) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) targetNode.getChildAt(i);
            expandAllChildNodes(tree, childNode);

            // 可选：添加微小延迟，避免一次性展开过多节点导致UI压力
            try {
                Thread.sleep(1); // 1ms延迟，几乎不影响速度，大幅降低UI卡顿
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 恢复中断状态
                break;
            }
        }
    }

    /**
     * 同步递归折叠（供后台线程调用）
     */
    private static void collapseAllChildNodes(JTree tree, DefaultMutableTreeNode targetNode, boolean collapseSelf) {
        // 先递归折叠所有子节点
        for (int i = 0; i < targetNode.getChildCount(); i++) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) targetNode.getChildAt(i);
            collapseAllChildNodes(tree, childNode, true);

            // 可选：微小延迟，优化大节点折叠体验
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
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