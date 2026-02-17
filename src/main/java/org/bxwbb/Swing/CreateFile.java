package org.bxwbb.Swing;

import org.bxwbb.UI.ArrowedTipWindow;
import org.bxwbb.UI.JLabelComboBox;
import org.bxwbb.Util.FileSuffixAdaptiveTool;
import org.bxwbb.Util.FileUtil;
import org.bxwbb.Util.Task.ScheduledTaskManager;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class CreateFile extends JDialog {
    private JPanel contentPane;
    private JButton createButton;
    private JButton cancelButton;
    private JLabel fileNameLabel;
    private JTextField fileNameField;
    private JLabelComboBox fileTypeComboBox;
    private JTextField filePathField;
    private JButton selectionButton;
    private JLabel pathLabel;

    private List<FileUtil.FileTypeInfo> fileTypeInfoList;
    private final String taskID;

    public CreateFile(Path path) {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(createButton);

        this.setIconImage(FileUtil.getImageIconToPath(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/MiniWindowIcon/FileManager.png")).getPath()).getImage());
        createButton.setText(FileUtil.getLang("popWindow.createrFile.create"));
        cancelButton.setText(FileUtil.getLang("popWindow.createrFile.cancel"));
        fileNameLabel.setText(FileUtil.getLang("popWindow.createrFile.folderName"));
        pathLabel.setText(FileUtil.getLang("popWindow.createrFile.folderPath"));
        filePathField.setText(path.toString());
        selectionButton.setIcon(FileUtil.getImageIconToPath(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/FileManager/SelectFolder.png")).getPath()));

        initFileTypeComboBox();

        taskID = ScheduledTaskManager.getInstance().startFixedDelayTask(300, () -> {
            String text = fileNameField.getText();
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

        fileTypeComboBox.addActionListener(e -> {
            if (fileTypeComboBox.isPopupVisible()) {
                int index = fileTypeComboBox.getSelectedIndex();
                String fileName = fileNameField.getText();
                if (index == 0) {
                    int lastDotIndex = fileName.lastIndexOf('.');
                    if (lastDotIndex != -1) {
                        fileName = fileName.substring(0, lastDotIndex);

                    }
                } else {
                    FileUtil.FileTypeInfo fileTypeInfo = fileTypeInfoList.get(index - 1);
                    fileName = FileSuffixAdaptiveTool.adaptFileSuffix(fileName, fileTypeInfo.matches());
                }
                if (!fileName.equals(fileNameField.getText())) {
                    fileNameField.setText(fileName);
                    fileNameField.setCaretPosition(fileName.length());
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
        boolean ret = createFile();
        if (ret) {
            FileUtil.createFile(Path.of(filePathField.getText()), fileNameField.getText());
            dispose();
        }
    }

    private void onCancel() {
        ScheduledTaskManager.getInstance().stopTask(taskID);
        dispose();
    }

    private boolean createFile() {
        String fileName = fileNameField.getText();
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
