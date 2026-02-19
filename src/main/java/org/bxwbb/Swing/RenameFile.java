package org.bxwbb.Swing;

import org.bxwbb.UI.ArrowedTipWindow;
import org.bxwbb.UI.JLabelComboBox;
import org.bxwbb.Util.FileSuffixAdaptiveTool;
import org.bxwbb.Util.FileUtil;
import org.bxwbb.Util.Task.ScheduledTaskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.*;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class RenameFile extends JDialog {
    private static final Logger log = LoggerFactory.getLogger(RenameFile.class);
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField newNameTextField;
    private JLabelComboBox fileTypeComboBox;
    private JLabel oldNameLabel;
    private JLabel newNameLabel;
    private JLabel oldName;

    private List<FileUtil.FileTypeInfo> fileTypeInfoList;
    private final String taskID;
    private final Path oldPath;

    public RenameFile(Path oldPath) {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        this.setIconImage(FileUtil.getImageIconToPath(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/MiniWindowIcon/FileManager.png")).getPath()).getImage());
        buttonOK.setText(FileUtil.getLang("popWindow.renameFile.create"));
        buttonCancel.setText(FileUtil.getLang("popWindow.renameFile.cancel"));
        oldNameLabel.setText(FileUtil.getLang("popWindow.renameFile.oldName"));
        newNameLabel.setText(FileUtil.getLang("popWindow.renameFile.newName"));
        oldName.setText(String.valueOf(oldPath.getFileName()));
        this.oldPath = oldPath;
        newNameTextField.setText(oldName.getText());

        initFileTypeComboBox();

        taskID = ScheduledTaskManager.getInstance().startFixedDelayTask(300, () -> {
            String text = newNameTextField.getText();
            int index = 1;
            for (FileUtil.FileTypeInfo fileTypeInfo : fileTypeInfoList) {
                if (text.matches(fileTypeInfo.matches())) {
                    if (fileTypeComboBox.getSelectedIndex() != index) fileTypeComboBox.setSelectedIndex(index);
                    return;
                }
                index++;
            }
            fileTypeComboBox.setSelectedIndex(0);
        });

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
        newNameTextField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (newNameTextField.getText().length() > 255) {
                    ArrowedTipWindow.error(newNameTextField, FileUtil.getLang("popWindow.createrFile.warn.tooLong"));
                    e.consume();
                }
            }
        });
        fileTypeComboBox.addActionListener(e -> {
            if (fileTypeComboBox.isPopupVisible()) {
                int index = fileTypeComboBox.getSelectedIndex();
                String fileName = newNameTextField.getText();
                if (fileName.isBlank()) return;
                if (index == 0) {
                    int lastDotIndex = fileName.lastIndexOf('.');
                    if (lastDotIndex != -1) {
                        fileName = fileName.substring(0, lastDotIndex);

                    }
                } else {
                    FileUtil.FileTypeInfo fileTypeInfo = fileTypeInfoList.get(index - 1);
                    fileName = FileSuffixAdaptiveTool.adaptFileSuffix(fileName, fileTypeInfo.matches());
                }
                if (!fileName.equals(newNameTextField.getText())) {
                    newNameTextField.setText(fileName);
                    newNameTextField.setCaretPosition(fileName.length());
                }
            }
        });

    }

    private void initFileTypeComboBox() {
        fileTypeComboBox.removeAllItems();
        JLabel label = new JLabel();
        label.setText(FileUtil.getLang("popWindow.createrFile.name.default"));
        label.setIcon(FileUtil.getImageIconToPath(Objects.requireNonNull(FileUtil.loadFile(FileUtil.DEFAULT_FILE_ICON)).getPath()));
        fileTypeComboBox.addItem(label);
        fileTypeInfoList = FileUtil.getFileTypeInfoList();
        for (FileUtil.FileTypeInfo fileTypeInfo : fileTypeInfoList) {
            label = new JLabel();
            label.setText(fileTypeInfo.name());
            label.setIcon(FileUtil.getImageIconToPath(Objects.requireNonNull(FileUtil.loadFile(fileTypeInfo.icon())).getPath()));
            fileTypeComboBox.addItem(label);
        }
    }

    private void onOK() {
        // 在此处添加您的代码
        if (Path.of(newNameTextField.getText()).toFile().exists()) {
            ArrowedTipWindow.error(newNameTextField, FileUtil.getLang("popWindow.renameFile.warn.exist"));
            return;
        }
        boolean ret = createFile();
        if (ret) {
            Path newPath = Path.of(oldPath.getParent().toString(), newNameTextField.getText());
            if (!FileUtil.renameFile(newPath, oldPath)) {
                log.error("重命名文件时出现错误");
            }
            ScheduledTaskManager.getInstance().stopTask(taskID);
            dispose();
        }
    }

    private void onCancel() {
        // 必要时在此处添加您的代码
        ScheduledTaskManager.getInstance().stopTask(taskID);
        dispose();
    }

    private boolean createFile() {
        String fileName = newNameTextField.getText();
        if (fileName == null || fileName.isBlank()) {
            ArrowedTipWindow.error(newNameTextField, FileUtil.getLang("popWindow.renameFile.warn.emptyName"));
            return false;
        }
        if (fileName.startsWith(" ") || fileName.endsWith(" ")) {
            ArrowedTipWindow.error(newNameTextField, FileUtil.getLang("popWindow.renameFile.warn.space"));
            return false;
        }
        if (fileName.matches(".*[\\\\/:*?\"<>|].*")) {
            ArrowedTipWindow.error(newNameTextField, FileUtil.getLang("popWindow.renameFile.warn.invalidChar"));
            return false;
        }
        String path = newNameTextField.getText();
        if (!path.endsWith("/")) path += "/";
        path += newNameTextField.getText();
        if ((new File(path)).exists()) {
            ArrowedTipWindow.error(newNameTextField, FileUtil.getLang("popWindow.renameFile.warn.exist"));
        }
        return true;
    }

}
