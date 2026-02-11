package org.bxwbb.UI;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class RoundedProgressBar extends JProgressBar {
    // 圆角半径
    private static final int CORNER_RADIUS = 5;
    // 等待模式标识
    private boolean waiting = false;
    // 渐变偏移量（控制动态效果）
    private int gradientOffset = 0;
    // 定时器，驱动等待模式的动态渐变
    private Timer animationTimer;

    public RoundedProgressBar() {
        super();
        init();
    }

    public RoundedProgressBar(int min, int max) {
        super(min, max);
        init();
    }

    private void init() {
        // 设置透明背景，避免默认绘制干扰
        setOpaque(false);
        // 关闭边框，自定义绘制
        setBorderPainted(false);
        // 初始化动画定时器（20ms刷新一次，约50帧/秒）
        animationTimer = new Timer(20, e -> {
            if (waiting) {
                if (getWidth() != 0) gradientOffset = (gradientOffset + 2) % getWidth();
                repaint();
            }
        });
    }

    /**
     * 设置等待模式
     * @param waiting true-进入等待模式，false-退出等待模式
     */
    public void setWaiting(boolean waiting) {
        this.waiting = waiting;
        if (waiting) {
            // 启动定时器，开始动态渐变
            animationTimer.start();
        } else {
            // 停止定时器，重置偏移量
            animationTimer.stop();
            gradientOffset = 0;
        }
        repaint();
    }

    public boolean isWaiting() {
        return waiting;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        // 开启抗锯齿，让圆角更平滑
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();
        Insets insets = getInsets();
        // 计算实际绘制区域（排除内边距）
        int drawWidth = width - insets.left - insets.right;
        int drawHeight = height - insets.top - insets.bottom;

        // 1. 绘制进度条背景（灰色圆角矩形）
        g2.setColor(getBackground());
        g2.fill(new RoundRectangle2D.Double(
                insets.left, insets.top,
                drawWidth, drawHeight,
                CORNER_RADIUS, CORNER_RADIUS));

        if (waiting) {
            // 2. 等待模式：绘制动态渐变的填充区域
            // 创建线性渐变（从左到右，蓝→青→蓝，偏移量控制起始位置）
            GradientPaint gradient = new GradientPaint(
                    insets.left - gradientOffset, insets.top, new Color(66, 133, 244),
                    insets.left + drawWidth + gradientOffset, insets.top, new Color(52, 168, 83),
                    true); // 循环渐变
            g2.setPaint(gradient);
            // 填充整个进度条区域（等待模式下满宽）
            g2.fill(new RoundRectangle2D.Double(
                    insets.left, insets.top,
                    drawWidth, drawHeight,
                    CORNER_RADIUS, CORNER_RADIUS));
        } else {
            // 3. 普通模式：绘制对应进度的填充区域
            int progressWidth = (int) (drawWidth * ((double) getValue() / getMaximum()));
            if (progressWidth > 0) {
                // 使用进度条的前景色填充
                g2.setColor(getForeground());
                g2.fill(new RoundRectangle2D.Double(
                        insets.left, insets.top,
                        progressWidth, drawHeight,
                        CORNER_RADIUS, CORNER_RADIUS));
            }
        }

        // 4. 绘制进度文本（保留原有文本显示逻辑）
        if (isStringPainted()) {
            g2.setColor(Color.WHITE);
            FontMetrics fm = g2.getFontMetrics();
            String text = getString();
            int textX = insets.left + (drawWidth - fm.stringWidth(text)) / 2;
            int textY = insets.top + (drawHeight - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(text, textX, textY);
        }

        g2.dispose();
    }
}