package org.bxwbb.Briefcase;

import org.bxwbb.MiniWindow.FileManager;
import org.bxwbb.Swing.CreateBriefcaseBox;
import org.bxwbb.UI.PinButton;
import org.bxwbb.UI.RoundLabel;
import org.bxwbb.Util.FileUtil;
import org.bxwbb.Util.UIUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class BriefcaseControl extends JFrame {
    private static final Logger log = LoggerFactory.getLogger(BriefcaseControl.class);

    // TODO: 完成公文包类的实现
    // TODO: 使用深度神经网络技术完成公文包类的自动分类

    private final JButton openBriefcaseButton;
    private final JTree briefcaseTree;
    private final DefaultTreeModel briefcaseTreeModel;


    public BriefcaseControl() {
        super();

        openBriefcaseButton = new JButton();
        ImageIcon imageIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/Briefcase/Briefcase.png")));
        imageIcon = new ImageIcon(imageIcon.getImage().getScaledInstance(20, 20, Image.SCALE_SMOOTH));
        openBriefcaseButton.setIcon(imageIcon);

        this.setIconImage(imageIcon.getImage());

        this.add(openBriefcaseButton);
        this.setTitle(FileUtil.getLang("briefcase.window.title", "BXWBB"));

        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
        rootNode.setUserObject(new BriefcaseNodeData("测试公文包", "这是测试信息", null, BriefcaseNodeType.BRIEFCASE));
        briefcaseTree = new JTree(rootNode);
        briefcaseTree.setCellRenderer(new CellRenderer());
        briefcaseTree.setDragEnabled(true);
        briefcaseTree.setDropMode(DropMode.ON);
        briefcaseTree.setTransferHandler(new BriefcaseTransferHandler());
        briefcaseTreeModel = (DefaultTreeModel) briefcaseTree.getModel();

        DefaultMutableTreeNode fNode = new DefaultMutableTreeNode("Tag");
        fNode.setUserObject(new BriefcaseNodeData("Tag", "这是Tag", null, BriefcaseNodeType.FOLDER));
        rootNode.add(fNode);

        briefcaseTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = briefcaseTree.getRowForLocation(e.getX(), e.getY());
                    if (row != -1) {
                        briefcaseTree.setSelectionRow(row);
                        TreePath selectedPath = briefcaseTree.getSelectionPath();
                        DefaultMutableTreeNode selectedNode = null;
                        if (selectedPath != null) {
                            selectedNode = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
                        }
                        if (selectedNode != null) {
                            JPopupMenu popupMenu = new JPopupMenu();
                            createPopMenu(popupMenu, selectedNode, briefcaseTreeModel, briefcaseTree);
                            popupMenu.show(briefcaseTree, e.getX(), e.getY());
                        }
                    }
                }
            }

        });

        JScrollPane briefcaseScrollPane = new JScrollPane(briefcaseTree);
        briefcaseScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        briefcaseScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        JPanel panel = new JPanel(new BorderLayout(1, 1));
        panel.add(briefcaseScrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(1));
        buttonPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        JButton createNewFolderButton = new JButton(FileUtil.getImageIconToPath(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/Briefcase/CreateNewFolderButton.png"))));
        buttonPanel.add(createNewFolderButton);
        buttonPanel.add(new PinButton(this));
        panel.add(buttonPanel, BorderLayout.NORTH);
        this.add(panel);

        this.pack();
        openBriefcaseButton.addActionListener(e -> {
            this.setVisible(true);
            this.setLocationRelativeTo(null);
        });
    }

    private void createPopMenu(JPopupMenu popupMenu, DefaultMutableTreeNode selectedNode, DefaultTreeModel currentModel, JTree tree) {
        BriefcaseNodeData selectedNodeData = (BriefcaseNodeData) selectedNode.getUserObject();
        JMenu createMenu = new JMenu(FileUtil.getLang("briefcase.popMenu.create"));
        JMenuItem createBox = new JMenuItem(FileUtil.getLang("briefcase.popMenu.create.box"));
        createBox.addActionListener(e -> {
            CreateBriefcaseBox dialog = new CreateBriefcaseBox();
            dialog.setSize(600, 200);
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
        });
        createMenu.add(createBox);
        popupMenu.add(createMenu);
        JMenuItem clear = new JMenuItem();
        clear.setText(FileUtil.getLang("briefcase.popMenu.clear"));
        if (!selectedNodeData.getNodeType().equals(BriefcaseNodeType.BRIEFCASE)) {
            clear.addActionListener(e -> currentModel.removeNodeFromParent(selectedNode));
        } else {
            clear.setEnabled(false);
        }
        popupMenu.add(clear);
        JMenuItem clearAll = new JMenuItem(FileUtil.getLang("briefcase.popMenu.clearAll"));
        if (selectedNodeData.getNodeType().equals(BriefcaseNodeType.FOLDER)) {
            clearAll.addActionListener(e -> {
                selectedNode.removeAllChildren();
                tree.collapsePath(new TreePath(selectedNode.getPath()));
                currentModel.reload(selectedNode);
            });
        } else {
            clearAll.setEnabled(false);
        }
        popupMenu.add(clearAll);
        JMenuItem deduplication = new JMenuItem(FileUtil.getLang("briefcase.popMenu.deduplication"));
        if (selectedNodeData.getNodeType().equals(BriefcaseNodeType.FOLDER)) {
            deduplication.addActionListener(e -> {
                Map<Object, String> map = new HashMap<>();
                Enumeration<TreeNode> children = (selectedNode.children());
                List<DefaultMutableTreeNode> removeNodes = new ArrayList<>();
                children.asIterator().forEachRemaining(node -> {
                    BriefcaseNodeData nodeData = (BriefcaseNodeData) ((DefaultMutableTreeNode) node).getUserObject();
                    if (nodeData.getNodeType().equals(BriefcaseNodeType.VALUE) && map.containsKey(nodeData.getValue()) && map.get(nodeData.getValue()).equals(nodeData.getName())) {
                        removeNodes.add((DefaultMutableTreeNode) node);
                    } else {
                        map.put(nodeData.getValue(), nodeData.getName());
                    }
                });
                removeNodes.forEach(currentModel::removeNodeFromParent);
                currentModel.reload(selectedNode);
                tree.expandPath(new TreePath(selectedNode.getPath()));
            });
        } else {
            deduplication.setEnabled(false);
        }
        popupMenu.add(deduplication);
        JMenu sort = new JMenu(FileUtil.getLang("briefcase.popMenu.sort"));
        if (!selectedNodeData.getNodeType().equals(BriefcaseNodeType.FOLDER)) sort.setEnabled(false);
        JMenuItem sortByName = new JMenuItem(FileUtil.getLang("briefcase.popMenu.sort.name"));
        sortByName.addActionListener(e -> {
            List<DefaultMutableTreeNode> nodes = new ArrayList<>();
            selectedNode.children().asIterator().forEachRemaining(node -> nodes.add((DefaultMutableTreeNode) node));
            nodes.sort((o1, o2) -> {
                BriefcaseNodeData b1 = (BriefcaseNodeData) o1.getUserObject();
                BriefcaseNodeData b2 = (BriefcaseNodeData) o2.getUserObject();
                return FileManager.sort(b1.getName(), b2.getName());
            });
            selectedNode.removeAllChildren();
            for (DefaultMutableTreeNode node : nodes) {
                currentModel.insertNodeInto(node, selectedNode, selectedNode.getChildCount());
            }
            currentModel.reload(selectedNode);
            tree.expandPath(new TreePath(selectedNode.getPath()));
        });
        sort.add(sortByName);
        popupMenu.add(sort);
    }

    private static class CellRenderer extends RoundLabel implements TreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            BriefcaseNodeData nodeData = (BriefcaseNodeData) node.getUserObject();
            setText(nodeData.getName());
            BriefcaseNodeType briefcaseNodeType = nodeData.getNodeType();

            Color imageColor = nodeData.getColor();
            if (imageColor != null && imageColor.getAlpha() != 0) {
                try {
                    BufferedImage bufferedImage = ImageIO.read(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/Briefcase/BriefcaseFolder.png")));
                    setIcon(new ImageIcon(UIUtil.imageResetSize(UIUtil.changeLabelImageColor(nodeData.getColor(), bufferedImage))));
                } catch (IOException e) {
                    log.error("读取公文包组件档案袋图标时发生IO错误 -> ", e);
                }
            } else {
                switch (briefcaseNodeType) {
                    case BRIEFCASE:
                        setIcon(FileUtil.getImageIconToPath(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/Briefcase/Briefcase.png"))));
                        break;
                    case FOLDER:
                        setIcon(FileUtil.getImageIconToPath(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/Briefcase/BriefcaseFolder.png"))));
                        break;
                    case VALUE:
                        switch (nodeData.getNodeValueType()) {
                            case FILES:
                                List<File> fileList = (List<File>) nodeData.getValue();
                                if (fileList.size() == 1) {
                                    setIcon(new ImageIcon(FileUtil.getFileIcon(fileList.getFirst(), 20, 20, false, false)));
                                } else {
                                    setIcon(FileUtil.getImageIconToPath(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/Briefcase/Items.png"))));
                                }
                                break;
                            case STRING:
                                setIcon(FileUtil.getImageIconToPath(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/Briefcase/String.png"))));
                                break;
                            default:
                                setIcon(FileUtil.getImageIconToPath(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/Item.png"))));
                        }

                }
            }

            this.setOpaque(false);
            this.setFocusable(false);
            this.setTransferHandler(null);

            return this;
        }
    }

    public JButton getOpenBriefcaseButton() {
        return openBriefcaseButton;
    }

    public void addNodeToSelectedNode(DefaultMutableTreeNode node) {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) briefcaseTree.getLastSelectedPathComponent();
        BriefcaseNodeData briefcaseNodeData = (BriefcaseNodeData) selectedNode.getUserObject();
        if (briefcaseNodeData.getNodeType().equals(BriefcaseNodeType.VALUE)) {
            selectedNode = (DefaultMutableTreeNode) selectedNode.getParent();
        }
        selectedNode.add(node);
        briefcaseTreeModel.insertNodeInto(node, selectedNode, selectedNode.getChildCount() - 1);
        briefcaseTree.expandPath(new TreePath(selectedNode.getPath()));
    }

    public void addNode(DefaultMutableTreeNode node, DefaultMutableTreeNode selectedNode) {
        BriefcaseNodeData briefcaseNodeData = (BriefcaseNodeData) selectedNode.getUserObject();
        if (briefcaseNodeData.getNodeType().equals(BriefcaseNodeType.VALUE)) {
            selectedNode = (DefaultMutableTreeNode) selectedNode.getParent();
        }
        selectedNode.add(node);
        briefcaseTreeModel.insertNodeInto(node, selectedNode, selectedNode.getChildCount() - 1);
        briefcaseTree.expandPath(new TreePath(selectedNode.getPath()));
    }

}
