package org.bxwbb.UI;

import javax.swing.*;
import java.awt.*;

public class JLabelComboBox extends JComboBox<JLabel> {

    public JLabelComboBox() {
        super();
        initRenderer();
    }

    public JLabelComboBox(JLabel[] items) {
        super(items);
        initRenderer();
    }

    /**
     * 初始化自定义渲染器，核心逻辑：直接返回传入的JLabel作为渲染组件
     */
    private void initRenderer() {
        setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            if (value == null) {
                return new JLabel("");
            }

            if (isSelected) {
                value.setBackground(list.getSelectionBackground());
                value.setForeground(list.getSelectionForeground());
            } else {
                value.setBackground(list.getBackground());
                value.setForeground(list.getForeground());
            }

            value.setOpaque(true);

            return value;
        });
    }

    public void addItem(String text, Icon icon) {
        JLabel label = new JLabel(text, icon, SwingConstants.LEFT);
        label.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5)); // 增加内边距，显示更美观
        addItem(label);
    }
}