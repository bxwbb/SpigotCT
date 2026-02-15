package org.bxwbb.UI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;

/**
 * 最终版：窗口永远在控件上方、箭头向下指向控件、文字完整显示、边框一体化
 */
public class ArrowedTipWindow extends JWindow {
    // 核心属性（固定箭头向下）
    private int arrowSize = 10;     // 箭头大小
    private Point arrowPoint;       // 箭头顶点（窗口下方居中）
    private Color tipBgColor = Color.WHITE; // 背景色
    private String tipText;         // 提示文本
    private final int cornerRadius = 8;   // 圆角半径

    // 动画相关属性
    private float opacity = 0.0f; // 透明度（0=完全透明，1=完全不透明）
    private Timer fadeInTimer;    // 渐入定时器
    private Timer fadeOutTimer;   // 渐出定时器
    private Timer autoHideTimer;  // 自动隐藏计时器（3秒）
    private static final int ANIMATION_DELAY = 15; // 动画帧间隔
    private static final int AUTO_HIDE_DELAY = 3000; // 自动隐藏延迟

    // ===================== 静态简便方法 =====================
    public static ArrowedTipWindow info(Component parent, String text) {
        return createTipWindow(parent, text, new Color(240, 248, 255));
    }

    public static ArrowedTipWindow warn(Component parent, String text) {
        return createTipWindow(parent, text, new Color(255, 250, 205));
    }

    public static ArrowedTipWindow error(Component parent, String text) {
        return createTipWindow(parent, text, new Color(255, 224, 224));
    }

    private static ArrowedTipWindow createTipWindow(Component parent, String text, Color bgColor) {
        Window parentWindow = SwingUtilities.getWindowAncestor(parent);
        ArrowedTipWindow tipWindow = (parentWindow instanceof Frame)
                ? new ArrowedTipWindow((Frame) parentWindow)
                : new ArrowedTipWindow((Dialog) parentWindow);

        tipWindow.setTipBgColor(bgColor);
        tipWindow.setTipText(text);
        tipWindow.attachToComponent(parent, text);
        return tipWindow;
    }

    // 构造器
    public ArrowedTipWindow(Frame owner) {
        super(owner);
        initUI();
        SwingUtilities.invokeLater(this::initMouseListener);
    }

    public ArrowedTipWindow(Dialog owner) {
        super(owner);
        initUI();
        SwingUtilities.invokeLater(this::initMouseListener);
    }

    // 初始化UI
    private void initUI() {
        setBackground(new Color(0, 0, 0, 0));
        setLayout(new BorderLayout());
        super.setOpacity(opacity);

        JPanel contentPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (arrowPoint == null) return;

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));

                drawIntegratedTip(g2);
                drawTipText(g2);

                g2.dispose();
            }

            @Override
            public boolean contains(int x, int y) {
                if (arrowPoint == null) return false;
                return getIntegratedShape().contains(x, y);
            }
        };
        contentPanel.setOpaque(false);
        add(contentPanel, BorderLayout.CENTER);
    }

    // 鼠标监听器
    private void initMouseListener() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                stopAllTimers();
                opacity = 1.0f;
                updateOpacity();
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                startAutoHideTimer();
            }
        });
    }

    // ===================== 核心绘制 =====================
    private void drawIntegratedTip(Graphics2D g2) {
        int width = getWidth();
        int height = getHeight();
        int rectHeight = height - arrowSize;

        // 一体化路径（圆角矩形 + 向下箭头）
        Path2D path = new Path2D.Double();
        path.moveTo(cornerRadius, 0);
        path.lineTo(width - cornerRadius, 0);
        path.quadTo(width, 0, width, cornerRadius);
        path.lineTo(width, rectHeight - cornerRadius);
        path.quadTo(width, rectHeight, width - cornerRadius, rectHeight);
        path.lineTo(arrowPoint.x + arrowSize, rectHeight);
        path.lineTo(arrowPoint.x, height);
        path.lineTo(arrowPoint.x - arrowSize, rectHeight);
        path.lineTo(cornerRadius, rectHeight);
        path.quadTo(0, rectHeight, 0, rectHeight - cornerRadius);
        path.lineTo(0, cornerRadius);
        path.quadTo(0, 0, cornerRadius, 0);
        path.closePath();

        // 绘制背景和一体化边框
        g2.setColor(tipBgColor);
        g2.fill(path);
        g2.setColor(Color.GRAY);
        g2.setStroke(new BasicStroke(1.0f));
        g2.draw(path);
    }

    private Shape getIntegratedShape() {
        int width = getWidth();
        int height = getHeight();
        int rectHeight = height - arrowSize;

        Path2D path = new Path2D.Double();
        path.moveTo(cornerRadius, 0);
        path.lineTo(width - cornerRadius, 0);
        path.quadTo(width, 0, width, cornerRadius);
        path.lineTo(width, rectHeight - cornerRadius);
        path.quadTo(width, rectHeight, width - cornerRadius, rectHeight);
        path.lineTo(arrowPoint.x + arrowSize, rectHeight);
        path.lineTo(arrowPoint.x, height);
        path.lineTo(arrowPoint.x - arrowSize, rectHeight);
        path.lineTo(cornerRadius, rectHeight);
        path.quadTo(0, rectHeight, 0, rectHeight - cornerRadius);
        path.lineTo(0, cornerRadius);
        path.quadTo(0, 0, cornerRadius, 0);
        path.closePath();

        return path;
    }

    private void drawTipText(Graphics2D g2) {
        if (tipText == null || tipText.isBlank()) return;

        Font font = new Font("微软雅黑", Font.PLAIN, 12);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();

        // 文字绘制区域（仅在圆角矩形内）
        int textAreaWidth = getWidth() - 20;
        int textAreaHeight = getHeight() - arrowSize - 10;
        int textAreaX = 10;
        int textAreaY = 5;

        // 多行文字居中
        String[] lines = tipText.split("\n");
        int totalTextHeight = lines.length * fm.getHeight();
        int startY = textAreaY + (textAreaHeight - totalTextHeight) / 2 + fm.getAscent();

        g2.setColor(Color.BLACK);
        for (String line : lines) {
            int lineWidth = fm.stringWidth(line);
            int lineX = textAreaX + (textAreaWidth - lineWidth) / 2;
            g2.drawString(line, lineX, startY);
            startY += fm.getHeight();
        }
    }

    // ===================== 动画控制 =====================
    private void startFadeIn() {
        stopAllTimers();
        opacity = 0.0f;
        updateOpacity();

        if (fadeInTimer == null) {
            fadeInTimer = new Timer(ANIMATION_DELAY, e -> {
                opacity = Math.min(opacity + 0.05f, 1.0f);
                updateOpacity();
                repaint();
                if (opacity >= 1.0f) {
                    fadeInTimer.stop();
                    startAutoHideTimer();
                }
            });
        }

        if (!isVisible()) super.setVisible(true);
        fadeInTimer.start();
    }

    private void startFadeOut() {
        stopAllTimers();

        if (fadeOutTimer == null) {
            fadeOutTimer = new Timer(ANIMATION_DELAY, e -> {
                opacity = Math.max(opacity - 0.05f, 0.0f);
                updateOpacity();
                repaint();
                if (opacity <= 0.0f) {
                    fadeOutTimer.stop();
                    super.setVisible(false);
                }
            });
        }
        fadeOutTimer.start();
    }

    private void startAutoHideTimer() {
        stopAutoHideTimer();
        if (autoHideTimer == null) {
            autoHideTimer = new Timer(AUTO_HIDE_DELAY, e -> startFadeOut());
            autoHideTimer.setRepeats(false);
        }
        autoHideTimer.start();
    }

    private void stopAllTimers() {
        if (fadeInTimer != null) fadeInTimer.stop();
        if (fadeOutTimer != null) fadeOutTimer.stop();
        if (autoHideTimer != null) autoHideTimer.stop();
    }

    private void stopAutoHideTimer() {
        if (autoHideTimer != null && autoHideTimer.isRunning()) autoHideTimer.stop();
    }

    private void updateOpacity() {
        try {
            super.setOpacity(opacity);
        } catch (IllegalComponentStateException ignored) {}
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) startFadeIn();
        else startFadeOut();
    }

    // ===================== 绑定组件+位置计算（核心修正） =====================
    public void attachToComponent(Component comp, String tipText) {
        this.tipText = tipText;
        // 1. 设置文字并计算窗口大小
        setTipText(tipText);
        // 2. 获取组件屏幕位置
        Point compScreenLoc = comp.getLocationOnScreen();
        Rectangle compRect = new Rectangle(compScreenLoc, comp.getSize());
        // 3. 计算窗口位置：永远在控件上方，箭头指向控件中心
        Point windowLoc = calculateWindowLoc(compRect);
        setLocation(windowLoc);
        // 4. 初始化箭头位置（窗口下方居中，指向控件中心）
        arrowPoint = new Point(getWidth() / 2, getHeight() - arrowSize);
        // 5. 设置窗口形状
        setShape(getIntegratedShape());
        // 6. 启动渐入动画
        startFadeIn();
    }

    /**
     * 核心修正：窗口永远在控件上方，且箭头对齐控件中心
     */
    private Point calculateWindowLoc(Rectangle compRect) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle screenRect = ge.getMaximumWindowBounds();

        // 窗口X轴：与控件中心对齐
        int windowX = compRect.x + (compRect.width - getWidth()) / 2;
        // 窗口Y轴：永远在控件上方，偏移5像素
        int windowY = compRect.y - getHeight() - 5;

        // 修正X轴：避免超出屏幕左右边界
        windowX = Math.max(screenRect.x, Math.min(windowX, screenRect.x + screenRect.width - getWidth()));
        // 极端情况：如果上方超出屏幕顶部，强制贴顶显示
        windowY = Math.max(screenRect.y, windowY);

        return new Point(windowX, windowY);
    }

    // ===================== 辅助方法 =====================
    public void setTipText(String tipText) {
        this.tipText = tipText;
        Font font = new Font("微软雅黑", Font.PLAIN, 12);
        FontMetrics fm = getFontMetrics(font);

        // 计算文字宽高
        String[] lines = tipText.split("\n");
        int maxLineWidth = 0;
        for (String line : lines) {
            maxLineWidth = Math.max(maxLineWidth, fm.stringWidth(line));
        }
        int width = maxLineWidth + 20;
        int height = (lines.length * fm.getHeight()) + 10 + arrowSize;

        // 最小宽高限制
        width = Math.max(width, 80);
        height = Math.max(height, 40 + arrowSize);

        setSize(width, height);
    }

    public void setTipBgColor(Color tipBgColor) {
        this.tipBgColor = tipBgColor;
        repaint();
    }

    public void setArrowSize(int arrowSize) {
        this.arrowSize = arrowSize;
        repaint();
    }
}