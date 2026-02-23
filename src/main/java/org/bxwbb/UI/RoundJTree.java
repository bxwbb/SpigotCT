package org.bxwbb.UI;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;

public class RoundJTree extends JTree {

    public RoundJTree(TreeNode root) {
        super(root);
    }

    public RoundJTree(TreeModel model) {
        super(model);
    }

}

