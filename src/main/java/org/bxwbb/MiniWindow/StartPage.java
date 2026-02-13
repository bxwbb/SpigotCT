package org.bxwbb.MiniWindow;

import org.bxwbb.Setting;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;

public class StartPage extends MiniWindow {

    private final String[][] tips = {
            {"随处搜索", "双击 Shift"},
            {"转到文件", "Ctrl+Shift+N"},
            {"最近的文件", "Ctrl+E"},
            {"导航栏", "Alt+Home"},
            {"将文件拖放到此处以打开", ""}
    };

    public StartPage() {
        super(StartPage.class);
    }

    @Override
    public void init() {
        setCenterPanel(new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // 计算整体文本块的宽度和高度，实现居中
                int totalWidth = 0;
                int totalHeight = 0;
                int lineGap = 18; // 行间距
                for (String[] tip : tips) {
                    // 获取每一行的宽度
                    Rectangle2D textBounds = g2d.getFontMetrics(Setting.textFont).getStringBounds(tip[0], g2d);
                    Rectangle2D shortcutBounds = g2d.getFontMetrics(Setting.shortcutFont).getStringBounds(tip[1], g2d);
                    int lineWidth = (int) (textBounds.getWidth() + (tip[1].isEmpty() ? 0 : 20 + shortcutBounds.getWidth()));
                    totalWidth = Math.max(totalWidth, lineWidth);
                    totalHeight += (int) (textBounds.getHeight() + lineGap);
                }
                totalHeight -= lineGap; // 减去最后一行的多余间距

                // 计算整体居中的起始坐标
                int textX = (getWidth() - totalWidth) / 2;
                int currentY = (getHeight() - totalHeight) / 2;

                // 逐行绘制文字和快捷键
                for (String[] tip : tips) {
                    String text = tip[0];
                    String shortcut = tip[1];

                    // 绘制主文本
                    g2d.setFont(Setting.textFont);
                    g2d.setColor(Setting.textColor);
                    Rectangle2D textBounds = g2d.getFontMetrics().getStringBounds(text, g2d);
                    int textY = currentY + (int) textBounds.getHeight();
                    g2d.drawString(text, textX, textY);

                    // 绘制快捷键（如果有）
                    if (!shortcut.isEmpty()) {
                        g2d.setFont(Setting.shortcutFont);
                        g2d.setColor(Setting.shortcutColor);
                        Rectangle2D shortcutBounds = g2d.getFontMetrics().getStringBounds(shortcut, g2d);
                        int shortcutX = textX + (int) textBounds.getWidth() + 20; // 文字和快捷键间距20
                        int shortcutY = currentY + (int) shortcutBounds.getHeight();
                        g2d.drawString(shortcut, shortcutX, shortcutY);
                    }

                    currentY += (int) (textBounds.getHeight() + lineGap);
                }
            }
        });
        getCenterPanel().setLayout(new BorderLayout());
    }

    @Override
    public void delete() {

    }

}
