package org.bxwbb.Util;

import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.HashMap;
import java.util.Map;
import java.util.Enumeration;

/**
 * 简化版 JTree 展开状态管理工具
 * 核心：父节点未展开则跳过子节点 + 全容错处理（路径不存在/状态缺失直接跳过）
 */
public class JTreeExpandStateManager {

    private final JTree tree;

    // 仅需绑定目标JTree，无其他依赖
    public JTreeExpandStateManager(JTree tree) {
        this.tree = tree;
    }

    /**
     * 记录展开状态（核心优化：父节点未展开则跳过子节点遍历）
     * @param rootNode 根节点
     * @return 有效路径的展开状态映射（TreePath -> 是否展开）
     */
    public Map<TreePath, Boolean> recordExpandState(DefaultMutableTreeNode rootNode) {
        Map<TreePath, Boolean> stateMap = new HashMap<>();
        if (rootNode == null) return stateMap;

        // 记录当前节点状态（先做容错：节点路径是否有效）
        TreePath rootPath = getSafeTreePath(rootNode);
        if (rootPath == null) return stateMap;
        stateMap.put(rootPath, tree.isExpanded(rootPath));

        // 父节点未展开 → 直接跳过子节点遍历（核心优化）
        if (!tree.isExpanded(rootPath)) return stateMap;

        // 父节点展开 → 递归遍历子节点
        Enumeration<TreeNode> children = rootNode.children();
        while (children.hasMoreElements()) {
            TreeNode child = children.nextElement();
            if (child instanceof DefaultMutableTreeNode childNode) {
                stateMap.putAll(recordExpandState(childNode));
            }
        }
        return stateMap;
    }

    /**
     * 恢复展开状态（全容错：路径不存在/状态缺失直接跳过）
     * @param expandStateMap 记录的状态映射
     */
    public void restoreExpandState(Map<TreePath, Boolean> expandStateMap) {
        if (expandStateMap == null || expandStateMap.isEmpty()) return;

        // 延迟执行，避免UI刷新冲突
        SwingUtilities.invokeLater(() -> {
            for (Map.Entry<TreePath, Boolean> entry : expandStateMap.entrySet()) {
                TreePath path = entry.getKey();
                Boolean shouldExpand = entry.getValue();

                // 容错1：路径/状态为空 → 跳过
                if (path == null || shouldExpand == null) continue;
                // 容错2：路径在树中不存在 → 跳过
                if (!isTreePathValid(path)) continue;

                boolean currentExpand = tree.isExpanded(path);
                // 仅状态不一致时修改，减少冗余操作
                if (shouldExpand && !currentExpand) {
                    tree.expandPath(path);
                } else if (!shouldExpand && currentExpand) {
                    tree.collapsePath(path);
                }
            }
        });
    }

    // ===================== 私有辅助方法（容错核心） =====================
    /**
     * 安全获取节点的TreePath（容错：节点路径无效返回null）
     */
    private TreePath getSafeTreePath(DefaultMutableTreeNode node) {
        try {
            return new TreePath(node.getPath());
        } catch (Exception e) {
            // 节点路径无效（如已被删除）→ 返回null
            return null;
        }
    }

    /**
     * 校验TreePath是否在树中有效（避免恢复不存在的路径）
     */
    private boolean isTreePathValid(TreePath path) {
        try {
            // 尝试获取路径的最后一个节点，验证路径有效性
            Object lastNode = path.getLastPathComponent();
            return lastNode instanceof TreeNode && tree.getModel().isLeaf(lastNode) ||
                    tree.getPathBounds(path) != null;
        } catch (Exception e) {
            // 路径无效（如节点已被删除）→ 返回false
            return false;
        }
    }
}