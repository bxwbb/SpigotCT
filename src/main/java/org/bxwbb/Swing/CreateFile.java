package org.bxwbb.Swing;

import org.bxwbb.UI.ArrowedTipWindow;
import org.bxwbb.Util.FileUtil;

import javax.swing.*;
import java.awt.event.*;
import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

public class CreateFile extends JDialog {
    private JPanel contentPane;
    private JButton createButton;
    private JButton cancelButton;
    private JLabel fileNameLabel;
    private JTextField fileNameField;
    private JComboBox fileTypeComboBox;
    private JTextField filePathField;
    private JButton selectionButton;
    private JLabel pathLabel;

    public CreateFile(Path path) {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(createButton);

        this.setIconImage(FileUtil.getImageIconToPath(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/MiniWindowIcon/FileManager.png")).getPath()).getImage());
        createButton.setText(FileUtil.getLang("popWindow.createrFile.create"));
        cancelButton.setText(FileUtil.getLang("popWindow.createrFile.cancel"));
        fileNameLabel.setText(FileUtil.getLang("popWindow.createrFile.folderName"));
        filePathField.setText(path.toString());
        selectionButton.setIcon(FileUtil.getImageIconToPath(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/FileManager/SelectFolder.png")).getPath()));

        createButton.addActionListener(e -> onOK());

        cancelButton.addActionListener(e -> onCancel());

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
            File file = FileUtil.chooseSingleFolder(null, FileUtil.getLang("miniWindow.fileManager.selectFile"));
            if (file != null) {
                filePathField.setText(file.getAbsolutePath());
            }
        });
    }

    private void onOK() {
        boolean ret = createFile();
        if (ret) {
            FileUtil.createFile(Path.of(filePathField.getText()), fileNameField.getText());
            dispose();
        }
    }

    private void onCancel() {
        // 必要时在此处添加您的代码
        dispose();
    }

    private boolean createFile() {
        String fileName = fileNameField.getText();
        String filePath = filePathField.getText();
        if (fileName == null || fileName.isBlank()) {
            ArrowedTipWindow.error(fileNameField, FileUtil.getLang("popWindow.createrFile.warn.emptyName"));
            return false;
        }
        if (fileName.startsWith(" ") || fileName.endsWith(" ")) {
            ArrowedTipWindow.error(fileNameField, FileUtil.getLang("popWindow.createrFile.warn.space"));
            return false;
        }
        if (fileName.matches(".*[\\\\/:*?\"<>|].*")) {
            ArrowedTipWindow.error(fileNameField, FileUtil.getLang("popWindow.createrFile.warn.invalidChar"));
            return false;
        }
        return true;
    }

}
