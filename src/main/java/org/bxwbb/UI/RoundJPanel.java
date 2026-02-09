package org.bxwbb.UI;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class RoundJPanel extends JPanel {
    private int roundRadius = 10;
    private Color borderColor = new Color(0, 0, 0, 0);
    private int borderWidth = 1;
    private Shape roundShape;
    // 新增：渐变背景的起始色和结束色（默认null，即普通背景）
    private Color gradientStartColor;
    private Color gradientEndColor;

    public RoundJPanel() {
        super();
        initConfig();
    }

    public RoundJPanel(int roundRadius) {
        super();
        this.roundRadius = roundRadius;
        initConfig();
    }

    public RoundJPanel(int roundRadius, Color bgColor) {
        super();
        this.roundRadius = roundRadius;
        setBackground(bgColor);
        initConfig();
    }

    // 新增：渐变背景构造方法
    public RoundJPanel(int roundRadius, Color gradientStartColor, Color gradientEndColor) {
        super();
        this.roundRadius = roundRadius;
        this.gradientStartColor = gradientStartColor;
        this.gradientEndColor = gradientEndColor;
        initConfig();
    }

    private void initConfig() {
        setOpaque(false);
        setDoubleBuffered(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

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

        // 绘制背景：有渐变色则绘制渐变，否则绘制普通背景
        if (gradientStartColor != null && gradientEndColor != null) {
            // 创建线性渐变（从上到下）
            GradientPaint gradient = new GradientPaint(
                    0, 0, gradientStartColor,
                    0, height, gradientEndColor
            );
            g2d.setPaint(gradient);
        } else {
            g2d.setColor(getBackground());
        }
        g2d.fill(roundShape);

        super.paintComponent(g2d);
        g2d.dispose();
    }

    @Override
    protected void paintBorder(Graphics g) {
        if (borderWidth <= 0 || borderColor.getAlpha() == 0) {
            return;
        }
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(borderColor);
        g2d.setStroke(new BasicStroke(borderWidth));
        g2d.draw(roundShape);
        g2d.dispose();
    }

    public Shape getShape() {
        return roundShape;
    }

    // 新增渐变色的setter/getter
    public Color getGradientStartColor() {
        return gradientStartColor;
    }

    public void setGradientStartColor(Color gradientStartColor) {
        this.gradientStartColor = gradientStartColor;
        repaint();
    }

    public Color getGradientEndColor() {
        return gradientEndColor;
    }

    public void setGradientEndColor(Color gradientEndColor) {
        this.gradientEndColor = gradientEndColor;
        repaint();
    }

    // 保留原有所有setter/getter
    public int getRoundRadius() {
        return roundRadius;
    }

    public void setRoundRadius(int roundRadius) {
        this.roundRadius = Math.max(0, roundRadius);
        repaint();
    }

    public Color getBorderColor() {
        return borderColor;
    }

    public void setBorderColor(Color borderColor) {
        this.borderColor = borderColor;
        repaint();
    }

    public int getBorderWidth() {
        return borderWidth;
    }

    public void setBorderWidth(int borderWidth) {
        this.borderWidth = Math.max(0, borderWidth);
        repaint();
    }
}