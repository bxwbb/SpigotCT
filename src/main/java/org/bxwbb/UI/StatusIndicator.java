package org.bxwbb.UI;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class StatusIndicator extends JComponent {

    private static final int INDICATOR_SIZE = 16;
    private static final int CORNER_RADIUS = 4;
    private static final int GLOW_EXTEND = 8;
    private IndicatorStatus status = IndicatorStatus.IDLE;

    public StatusIndicator() {
        setPreferredSize(new Dimension(INDICATOR_SIZE + GLOW_EXTEND, INDICATOR_SIZE + GLOW_EXTEND));
        setOpaque(false);
    }

    public void setStatus(IndicatorStatus status) {
        this.status = status;
        repaint();
    }

    public IndicatorStatus getStatus() {
        return status;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;
        int halfSize = INDICATOR_SIZE / 2;

        // 光晕效果
        Color statusColor = status.getColor();
        RadialGradientPaint glowPaint = new RadialGradientPaint(
                centerX, centerY,
                halfSize + (float) GLOW_EXTEND / 2,
                new float[]{0.0f, 1.0f},
                new Color[]{new Color(statusColor.getRed(), statusColor.getGreen(), statusColor.getBlue(), 150),
                        new Color(statusColor.getRed(), statusColor.getGreen(), statusColor.getBlue(), 0)}
        );
        g2.setPaint(glowPaint);
        g2.fillOval(centerX - halfSize - GLOW_EXTEND / 2,
                centerY - halfSize - GLOW_EXTEND / 2,
                INDICATOR_SIZE + GLOW_EXTEND,
                INDICATOR_SIZE + GLOW_EXTEND);

        // 圆角正方形指示灯
        g2.setColor(statusColor);
        g2.fill(new RoundRectangle2D.Double(
                centerX - halfSize,
                centerY - halfSize,
                INDICATOR_SIZE,
                INDICATOR_SIZE,
                CORNER_RADIUS,
                CORNER_RADIUS));

        g2.dispose();
    }
}
