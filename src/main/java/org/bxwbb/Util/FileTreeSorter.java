package org.bxwbb.Util;

import org.bxwbb.MiniWindow.FileManager;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileTreeSorter {

    // 排序类型枚举
    public enum SortType {
        NAME,
        CREATE_TIME_ASC,
        CREATE_TIME_DESC
    }

    private final JTree tree;
    private final DefaultTreeModel treeModel;

    public FileTreeSorter(JTree tree, DefaultTreeModel treeModel) {
        this.tree = tree;
        this.treeModel = treeModel;
    }

    /**
     * 对指定节点的子节点进行排序（递归可选）
     * @param rootNode 要排序的根节点
     * @param sortType 排序类型
     * @param folderFirst 是否文件夹置顶
     * @param recursive 是否递归排序子文件夹
     */
    public void sortTree(DefaultMutableTreeNode rootNode, SortType sortType, boolean folderFirst, boolean recursive) {
        Map<DefaultMutableTreeNode, Boolean> expandStateMap = recordExpandState(rootNode);

        sortNodeChildren(rootNode, sortType, folderFirst, recursive);

        restoreExpandState(expandStateMap);
    }

    /**
     * 记录指定节点及其所有子节点的展开状态
     */
    private Map<DefaultMutableTreeNode, Boolean> recordExpandState(DefaultMutableTreeNode node) {
        Map<DefaultMutableTreeNode, Boolean> stateMap = new HashMap<>();
        stateMap.put(node, tree.isExpanded(new TreePath(node.getPath())));
        Enumeration<TreeNode> children = node.children();
        while (children.hasMoreElements()) {
            TreeNode child = children.nextElement();
            if (child instanceof DefaultMutableTreeNode childNode) {
                stateMap.putAll(recordExpandState(childNode));
            }
        }
        return stateMap;
    }

    /**
     * 恢复节点展开状态
     */
    private void restoreExpandState(Map<DefaultMutableTreeNode, Boolean> expandStateMap) {
        for (Map.Entry<DefaultMutableTreeNode, Boolean> entry : expandStateMap.entrySet()) {
            DefaultMutableTreeNode node = entry.getKey();
            boolean isExpanded = entry.getValue();
            TreePath path = new TreePath(node.getPath());
            if (isExpanded && !tree.isExpanded(path)) {
                tree.expandPath(path);
            } else if (!isExpanded && tree.isExpanded(path)) {
                tree.collapsePath(path);
            }
        }
    }

    /**
     * 排序单个节点的子节点（核心排序逻辑）
     */
    private void sortNodeChildren(DefaultMutableTreeNode parentNode, SortType sortType, boolean folderFirst, boolean recursive) {
        if (!(parentNode.getUserObject() instanceof FileManager.FileData) || !((FileManager.FileData) parentNode.getUserObject()).file().isDirectory()) {
            return;
        }

        List<DefaultMutableTreeNode> folderNodes = new ArrayList<>();
        List<DefaultMutableTreeNode> fileNodes = new ArrayList<>();
        Enumeration<TreeNode> children = parentNode.children();
        while (children.hasMoreElements()) {
            TreeNode child = children.nextElement();
            if (!(child instanceof DefaultMutableTreeNode childNode)) {
                continue;
            }
            if (!(childNode.getUserObject() instanceof FileManager.FileData)) {
                continue;
            }
            File file = ((FileManager.FileData) childNode.getUserObject()).file();
            if (file.isDirectory()) {
                folderNodes.add(childNode);
            } else {
                fileNodes.add(childNode);
            }
        }

        Comparator<DefaultMutableTreeNode> comparator = getNodeComparator(sortType);
        folderNodes.sort(comparator);
        fileNodes.sort(comparator);

        List<DefaultMutableTreeNode> sortedNodes = new ArrayList<>();
        if (folderFirst) {
            sortedNodes.addAll(folderNodes);
            sortedNodes.addAll(fileNodes);
        } else {
            sortedNodes.addAll(folderNodes);
            sortedNodes.addAll(fileNodes);
            sortedNodes.sort(comparator);
        }

        parentNode.removeAllChildren();
        for (DefaultMutableTreeNode node : sortedNodes) {
            parentNode.add(node);
            if (recursive && ((FileManager.FileData) node.getUserObject()).file().isDirectory()) {
                sortNodeChildren(node, sortType, folderFirst, true);
            }
        }
    }

    /**
     * 获取节点比较器（按名称/创建时间）
     */
    private Comparator<DefaultMutableTreeNode> getNodeComparator(SortType sortType) {
        return (node1, node2) -> {
            File file1 = ((FileManager.FileData) node1.getUserObject()).file();
            File file2 = ((FileManager.FileData) node2.getUserObject()).file();

            return switch (sortType) {
                case NAME -> compareByName(file1, file2);
                case CREATE_TIME_ASC -> Long.compare(file1.lastModified(), file2.lastModified());
                case CREATE_TIME_DESC -> Long.compare(file2.lastModified(), file1.lastModified());
            };
        };
    }

    /**
     * Windows风格名称自然排序（核心）
     */
    private int compareByName(File f1, File f2) {
        String s1 = f1.getName().toLowerCase();
        String s2 = f2.getName().toLowerCase();
        Pattern numberPattern = Pattern.compile("(\\d+)");
        Matcher m1 = numberPattern.matcher(s1);
        Matcher m2 = numberPattern.matcher(s2);

        int pos1 = 0, pos2 = 0;
        while (m1.find() && m2.find()) {
            String nonDigit1 = s1.substring(pos1, m1.start());
            String nonDigit2 = s2.substring(pos2, m2.start());
            int nonDigitComp = nonDigit1.compareTo(nonDigit2);
            if (nonDigitComp != 0) {
                return nonDigitComp;
            }
            long num1 = Long.parseLong(m1.group());
            long num2 = Long.parseLong(m2.group());
            if (num1 != num2) {
                return Long.compare(num1, num2);
            }
            pos1 = m1.end();
            pos2 = m2.end();
        }
        String remaining1 = s1.substring(pos1);
        String remaining2 = s2.substring(pos2);
        return remaining1.compareTo(remaining2);
    }
}