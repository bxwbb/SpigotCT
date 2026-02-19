package org.bxwbb.Swing;

import org.bxwbb.UI.ArrowedTipWindow;
import org.bxwbb.Util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public class CreateFolder extends JDialog {
    private static final Logger log = LoggerFactory.getLogger(CreateFolder.class);
    private JPanel contentPane;
    private JButton buttonCreate;
    private JButton buttonCancel;
    private JTextField nameField;
    private JTextField pathField;
    private JButton selectionButton;
    private JLabel folderName;
    private JLabel folderPath;
    private JCheckBox hideCheckBox;
    private JLabel propertiesText;
    private JCheckBox onlyReadCheckBox;

    public CreateFolder(Path path) {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonCreate);

        this.setIconImage(FileUtil.getImageIconToPath(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/MiniWindowIcon/FileManager.png")).getPath()).getImage());
        buttonCreate.setText(FileUtil.getLang("popWindow.createrFolder.create"));
        buttonCancel.setText(FileUtil.getLang("popWindow.createrFolder.cancel"));
        folderName.setText(FileUtil.getLang("popWindow.createrFolder.folderName"));
        folderPath.setText(FileUtil.getLang("popWindow.createrFolder.folderPath"));
        pathField.setText(path.toString());
        propertiesText.setText(FileUtil.getLang("popWindow.createrFolder.properties"));
        hideCheckBox.setText(FileUtil.getLang("popWindow.createrFolder.properties.hide"));
        onlyReadCheckBox.setText(FileUtil.getLang("popWindow.createrFolder.properties.onlyRead"));
        selectionButton.setIcon(FileUtil.getImageIconToPath(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/FileManager/SelectFolder.png")).getPath()));

        buttonCreate.addActionListener(e -> onOK());

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
        selectionButton.addActionListener(e -> {

        });
        nameField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (nameField.getText().length() > 255) {
                    ArrowedTipWindow.error(nameField, FileUtil.getLang("popWindow.createrFolder.warn.tooLong"));
                    e.consume();
                }
                boolean hide = hideCheckBox.isSelected();
                if (nameField.getText().startsWith(".") && !hide) {
                    ArrowedTipWindow.warn(nameField, FileUtil.getLang("popWindow.createrFolder.warn.hideFolder"));
                }
            }
        });
        selectionButton.addActionListener(e -> {
            File file = FileUtil.chooseSingleFolder(null, FileUtil.getLang("miniWindow.fileManager.selectFile"));
            if (file != null) {
                pathField.setText(file.getAbsolutePath());
            }
        });
    }

    private void onOK() {
        // 在此处添加您的代码
        boolean ret = createFolder();
        if (ret) {
            String path = pathField.getText();
            if (!path.endsWith("/")) path += "/";
            path += nameField.getText();
            if (!FileUtil.createFoldersStepByStep(path)) {
                log.error("创建文件夹时发生错误 - {}", path);
                return;
            }
            File file = new File(path);
            if (file.isDirectory()) {
                try {
                    if (!FileUtil.setFileHidden(file.getPath(), hideCheckBox.isSelected())) {
                        log.error("隐藏文件时发生错误 - {}", file.getPath());
                        return;
                    }
                } catch (IOException e) {
                    log.error("隐藏文件时发生错误 - {} -> ", file.getPath(), e);
                }
                if (onlyReadCheckBox.isSelected()) {
                    if (!file.setReadOnly()) {
                        log.error("将文件设置为只读时发生错误 - {}", file.getPath());
                    }
                }
            }
            dispose();
        }
    }

    private void onCancel() {
        // 必要时在此处添加您的代码
        dispose();
    }

    private boolean createFolder() {
        String folderName = nameField.getText();
        if (folderName == null || folderName.isBlank()) {
            ArrowedTipWindow.error(nameField, FileUtil.getLang("popWindow.createrFolder.warn.emptyName"));
            return false;
        }
        if (folderName.startsWith(" ") || folderName.endsWith(" ")) {
            ArrowedTipWindow.error(nameField, FileUtil.getLang("popWindow.createrFolder.warn.space"));
            return false;
        }
        if (folderName.matches(".*[\\\\/:*?\"<>|].*")) {
            ArrowedTipWindow.error(nameField, FileUtil.getLang("popWindow.createrFolder.warn.invalidChar"));
            return false;
        }
        String path = pathField.getText();
        if (!path.endsWith("/")) path += "/";
        path += nameField.getText();
        if ((new File(path)).exists()) {
            ArrowedTipWindow.error(nameField, FileUtil.getLang("popWindow.createrFolder.warn.exist"));
        }
        return true;
    }

}
