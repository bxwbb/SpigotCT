package org.bxwbb.Swing;

import org.bxwbb.Briefcase.BriefcaseNodeData;
import org.bxwbb.Briefcase.BriefcaseNodeType;
import org.bxwbb.Main;
import org.bxwbb.Util.FileUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.*;
import java.util.Objects;

public class CreateBriefcaseBox extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField nameTextField;
    private JButton changeColorButton;
    private JLabel nameLabel;

    private Color selectedColor;

    public CreateBriefcaseBox() {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        this.setIconImage(FileUtil.getImageIconToPath(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/Briefcase/Briefcase.png")).getPath()).getImage());
        buttonOK.setText(FileUtil.getLang("briefcase.popMenu.create.box.create"));
        buttonCancel.setText(FileUtil.getLang("briefcase.popMenu.create.box.cancel"));
        changeColorButton.setText(FileUtil.getLang("briefcase.popMenu.create.box.changeColorButton"));
        nameLabel.setText(FileUtil.getLang("briefcase.popMenu.create.box.nameLabel"));

        buttonOK.addActionListener(e -> onOK());

        buttonCancel.addActionListener(e -> onCancel());

        // 点击 X 时调用 onCancel()
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // 遇到 ESCAPE 时调用 onCancel()
        contentPane.registerKeyboardAction(e -> onCancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        changeColorButton.addActionListener(e -> selectedColor = JColorChooser.showDialog(null, "选择颜色", Color.white));
    }

    private void onOK() {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode();
        BriefcaseNodeData briefcaseNodeData = new BriefcaseNodeData(nameTextField.getText(), null, "", BriefcaseNodeType.FOLDER);
        briefcaseNodeData.setColor(selectedColor);
        node.setUserObject(briefcaseNodeData);
        Main.briefcaseControl.addNodeToSelectedNode(node);
        dispose();
    }

    private void onCancel() {
        // 必要时在此处添加您的代码
        dispose();
    }

}
