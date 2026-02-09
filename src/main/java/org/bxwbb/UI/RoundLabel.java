package org.bxwbb.UI;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * 自定义圆角标签（RoundLabel）
 * 支持圆角、背景色、边框，同时保留JLabel显示图片+文字的核心功能
 */
public class RoundLabel extends JLabel {
    // 圆角半径（默认10像素）
    private int roundRadius = 10;
    // 边框颜色（默认透明，无边框）
    private Color borderColor = new Color(0, 0, 0, 0);
    // 边框宽度（默认1像素）
    private int borderWidth = 1;
    // 图文间距（默认5像素）
    private int iconTextGap = 5;
    // 缓存圆角形状，避免重复创建
    private Shape roundShape;

    // ========== 构造方法（覆盖JLabel核心构造，保持使用习惯） ==========
    public RoundLabel() {
        super();
        initConfig();
    }

    public RoundLabel(String text) {
        super(text);
        initConfig();
    }

    public RoundLabel(Icon icon) {
        super(icon);
        initConfig();
    }

    public RoundLabel(String text, Icon icon, int horizontalAlignment) {
        super(text, icon, horizontalAlignment);
        initConfig();
    }

    /**
     * 带圆角的构造方法（常用）
     * @param text 显示文本
     * @param roundRadius 圆角半径
     * @param bgColor 背景色
     */
    public RoundLabel(String text, int roundRadius, Color bgColor) {
        super(text);
        this.roundRadius = roundRadius;
        setBackground(bgColor);
        initConfig();
    }

    /**
     * 带图片+文字+圆角的构造方法（常用）
     * @param text 显示文本
     * @param icon 显示图片
     * @param roundRadius 圆角半径
     * @param bgColor 背景色
     */
    public RoundLabel(String text, Icon icon, int roundRadius, Color bgColor) {
        super(text, icon, SwingConstants.CENTER);
        this.roundRadius = roundRadius;
        setBackground(bgColor);
        initConfig();
    }

    // ========== 初始化配置 ==========
    private void initConfig() {
        // 关键：设置透明，避免原生矩形背景覆盖圆角
        setOpaque(false);
        // 开启双缓冲，绘制无闪烁
        setDoubleBuffered(true);
        // 默认图文间距
        setIconTextGap(iconTextGap);
        // 默认文本居中（可外部修改）
        setHorizontalAlignment(SwingConstants.CENTER);
        setVerticalAlignment(SwingConstants.CENTER);
    }

    // ========== 核心：重写绘制方法 ==========
    /**
     * 绘制圆角背景+边框
     */
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        // 抗锯齿：圆角/文字/图片边缘更平滑
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 获取标签尺寸，创建圆角形状（扣除边框宽度，避免边框被裁剪）
        int width = getWidth();
        int height = getHeight();
        roundShape = new RoundRectangle2D.Double(
                borderWidth / 2.0,
                borderWidth / 2.0,
                width - borderWidth,
                height - borderWidth,
                roundRadius,
                roundRadius
        );

        // 1. 绘制圆角背景
        if (getBackground() != null) {
            g2d.setColor(getBackground());
            g2d.fill(roundShape);
        }

        // 2. 调用父类方法，绘制图片+文字（必须保留，否则图文不显示）
        super.paintComponent(g2d);

        g2d.dispose();
    }

    /**
     * 绘制圆角边框
     */
    @Override
    protected void paintBorder(Graphics g) {
        // 边框宽度为0或透明时，不绘制边框
        if (borderWidth <= 0 || borderColor.getAlpha() == 0) {
            return;
        }

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 设置边框样式
        g2d.setColor(borderColor);
        g2d.setStroke(new BasicStroke(borderWidth));
        // 绘制圆角边框
        g2d.draw(roundShape);

        g2d.dispose();
    }

    /**
     * 重写鼠标形状检测：点击圆角外部无响应（符合视觉直觉）
     */
    @Override
    public boolean contains(int x, int y) {
        return roundShape != null && roundShape.contains(x, y);
    }

    // ========== 对外暴露的自定义方法 ==========
    // 设置圆角半径（自动重绘）
    public void setRoundRadius(int roundRadius) {
        this.roundRadius = Math.max(0, roundRadius);
        repaint();
    }

    public int getRoundRadius() {
        return roundRadius;
    }

    // 设置边框颜色（自动重绘）
    public void setBorderColor(Color borderColor) {
        this.borderColor = borderColor;
        repaint();
    }

    public Color getBorderColor() {
        return borderColor;
    }

    // 设置边框宽度（自动重绘）
    public void setBorderWidth(int borderWidth) {
        this.borderWidth = Math.max(0, borderWidth);
        repaint();
    }

    public int getBorderWidth() {
        return borderWidth;
    }

    // 重写图文间距（保持父类逻辑，仅增加重绘）
    @Override
    public void setIconTextGap(int iconTextGap) {
        this.iconTextGap = iconTextGap;
        super.setIconTextGap(iconTextGap);
        repaint();
    }

    public int getIconTextGap() {
        return iconTextGap;
    }
}