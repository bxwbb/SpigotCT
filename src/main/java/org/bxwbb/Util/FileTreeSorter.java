package org.bxwbb.Util;

import org.bxwbb.MiniWindow.FileManager;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileTreeSorter {

    public enum SortType {
        NAME,
        CREATE_TIME_ASC,
        CREATE_TIME_DESC
    }

    private final DefaultTreeModel treeModel;
    private SortType currentSortType;
    private boolean folderFirst;

    public FileTreeSorter(DefaultTreeModel treeModel) {
        this.treeModel = treeModel;
        this.currentSortType = SortType.NAME;
        this.folderFirst = true;
    }

    /**
     * 设置排序规则（后续插入的节点都会按此规则排序）
     */
    public void setSortRule(SortType sortType, boolean folderFirst) {
        this.currentSortType = sortType;
        this.folderFirst = folderFirst;
    }

    /**
     * 插入节点到父节点的正确位置（核心插入排序方法）
     * @param parentNode 父节点
     * @param newNode 要插入的新节点
     */
    public void insertNodeInSortedPosition(DefaultMutableTreeNode parentNode, DefaultMutableTreeNode newNode) {
        if (parentNode.getUserObject() == null) {
            parentNode.add(newNode);
            treeModel.nodeStructureChanged(parentNode);
            return;
        }

        int childCount = parentNode.getChildCount();
        int insertIndex = 0;

        Comparator<DefaultMutableTreeNode> comparator = getNodeComparator(currentSortType);

        for (; insertIndex < childCount; insertIndex++) {
            DefaultMutableTreeNode existingNode = (DefaultMutableTreeNode) parentNode.getChildAt(insertIndex);

            if (existingNode.getUserObject() == null && newNode.getUserObject() != null) {
                break;
            }

            if (newNode.getUserObject() == null && existingNode.getUserObject() != null) {
                continue;
            }

            if (folderFirst) {
                boolean newIsFolder = isNodeFolder(newNode);
                boolean existingIsFolder = isNodeFolder(existingNode);

                if (newIsFolder && !existingIsFolder) {
                    break;
                } else if (!(!newIsFolder && existingIsFolder)) {
                    if (comparator.compare(newNode, existingNode) < 0) {
                        break;
                    }
                }
            } else {
                if (comparator.compare(newNode, existingNode) < 0) {
                    break;
                }
            }
        }

        parentNode.insert(newNode, insertIndex);
        treeModel.nodesWereInserted(parentNode, new int[]{insertIndex});
    }

    /**
     * 判断节点是否为文件夹类型
     * @param node 待判断节点
     * @return true=文件夹（FileData文件夹/String空文件夹），false=文件/null节点
     */
    private boolean isNodeFolder(DefaultMutableTreeNode node) {
        Object userObj = node.getUserObject();

        return switch (userObj) {
            case String ignored -> true;
            case FileManager.FileData fileData -> fileData.file().isDirectory();
            default -> false;
        };

    }

    /**
     * 获取节点的排序名称
     * @param node 节点
     * @return 排序用的名称（null节点返回空字符串）
     */
    private String getNodeSortName(DefaultMutableTreeNode node) {
        Object userObj = node.getUserObject();

        return switch (userObj) {
            case null -> "";
            case String str -> str.toLowerCase();
            case FileManager.FileData fileData -> fileData.file().getName().toLowerCase();
            default -> userObj.toString().toLowerCase();
        };

    }

    /**
     * 获取节点的修改时间（用于时间排序）
     * @param node 节点
     * @return 修改时间（非FileData节点返回0）
     */
    private long getNodeModifyTime(DefaultMutableTreeNode node) {
        Object userObj = node.getUserObject();

        if (userObj instanceof FileManager.FileData(java.io.File file)) {
            return file.lastModified();
        }

        return 0;
    }

    /**
     * 对已有父节点的所有子节点重新进行插入排序整理（用于首次初始化排序）
     */
    public void reSortExistingChildren(DefaultMutableTreeNode parentNode) {
        if (parentNode.getUserObject() == null) {
            return;
        }

        int childCount = parentNode.getChildCount();
        DefaultMutableTreeNode[] tempNodes = new DefaultMutableTreeNode[childCount];
        for (int i = 0; i < childCount; i++) {
            tempNodes[i] = (DefaultMutableTreeNode) parentNode.getChildAt(i);
        }

        parentNode.removeAllChildren();

        for (DefaultMutableTreeNode node : tempNodes) {
            insertNodeInSortedPosition(parentNode, node);

            if (isNodeFolder(node)) {
                if (!(node.getUserObject() instanceof String)) {
                    reSortExistingChildren(node);
                }
            }
        }

        treeModel.nodeStructureChanged(parentNode);
    }

    /**
     * 获取节点比较器（适配String/null/FileData类型）
     */
    private Comparator<DefaultMutableTreeNode> getNodeComparator(SortType sortType) {
        return (node1, node2) -> {
            if (node1.getUserObject() == null && node2.getUserObject() == null) {
                return 0;
            }
            if (node1.getUserObject() == null) {
                return 1;
            }
            if (node2.getUserObject() == null) {
                return -1;
            }

            return switch (sortType) {
                case NAME -> compareNodesByName(node1, node2);
                case CREATE_TIME_ASC -> Long.compare(getNodeModifyTime(node1), getNodeModifyTime(node2));
                case CREATE_TIME_DESC -> Long.compare(getNodeModifyTime(node2), getNodeModifyTime(node1));
            };
        };
    }

    /**
     * 按名称比较节点（适配String/FileData类型）
     */
    private int compareNodesByName(DefaultMutableTreeNode node1, DefaultMutableTreeNode node2) {
        String s1 = getNodeSortName(node1);
        String s2 = getNodeSortName(node2);

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

    public SortType getCurrentSortType() {
        return currentSortType;
    }

    public boolean isFolderFirst() {
        return folderFirst;
    }
}