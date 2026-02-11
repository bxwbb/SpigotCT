package org.bxwbb.UI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.Objects;

public class MissionTip extends JPanel {
    // ===================== 样式常量（颜色+尺寸）=====================
    // 1. 尺寸常量
    private static final int BUTTON_GAP = 2;               // 按钮上下间距
    private static final int COMPONENT_GAP = 2;           // 按钮与指示灯间距
    private static final int HORIZONTAL_PADDING = 2;      // 水平内边距
    private static final int VERTICAL_PADDING = 2;        // 垂直内边距
    private static final int PROGRESS_BAR_HEIGHT = 5;     // 进度条高度
    private static final int BUTTON_WIDTH = 30;           // 圆角按钮宽度
    private static final int BUTTON_HEIGHT = 24;          // 圆角按钮高度
    private static final int BUTTON_CORNER_RADIUS = 6;    // 按钮圆角半径
    private static final int CORNER_RADIUS = 10;          // 组件背景圆角

    // 2. 颜色常量（所有颜色集中定义，便于统一修改）
    private static final Color COLOR_BUTTON_NORMAL_BG = Color.darkGray;       // 按钮默认背景色
    private static final Color COLOR_BUTTON_HOVER_BG = Color.gray;        // 按钮悬停背景色
    private static final Color COLOR_BUTTON_PRESSED_BG = new Color(80, 80, 80);      // 按钮按下背景色
    private static final Color COLOR_BUTTON_BORDER = new Color(0, 0, 0, 0);               // 按钮边框色
    private static final Color COLOR_BUTTON_TEXT_NORMAL = new Color(51, 51, 51);             // 按钮文字默认色
    private static final Color COLOR_BUTTON_TEXT_HOVER = new Color(0, 122, 255);             // 按钮文字悬停色

    private static final Color COLOR_PANEL_BG = Color.darkGray;                    // 右侧面板背景色
    private static final Color COLOR_PANEL_BORDER = new Color(0, 0, 0, 0);                // 右侧面板边框色

    private static final Color COLOR_MISSION_NAME = new Color(33, 33, 33);                   // 任务名称文字色
    private static final Color COLOR_PROGRESS_BG = Color.LIGHT_GRAY;                          // 进度条背景色
    private static final Color COLOR_PROGRESS_FG = Color.cyan;                  // 进度条前景色

    // ===================== 组件成员 =====================
    private final StatusIndicator indicator;
    private final JLabel missionNameLabel;
    private final RoundedProgressBar progressBar;
    private final JButton pauseButton;
    private final int FIXED_TOTAL_HEIGHT;  // 固化整体高度
    private boolean isPaused = false;

    // 回调接口
    public interface OperationCallback {
        boolean onPause();

        boolean onResume();

        boolean onStop();
    }

    private OperationCallback operationCallback;

    public MissionTip(String missionName) {
        // 计算固定总高度（按钮面板高度 + 上下内边距）
        int buttonPanelHeight = BUTTON_HEIGHT * 2 + BUTTON_GAP;
        FIXED_TOTAL_HEIGHT = buttonPanelHeight + VERTICAL_PADDING * 2;

        // 外层布局
        setLayout(new BorderLayout(COMPONENT_GAP, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(VERTICAL_PADDING, HORIZONTAL_PADDING, VERTICAL_PADDING, HORIZONTAL_PADDING));

        // 左侧按钮面板（固定尺寸）
        JPanel leftButtonPanel = new FixedSizePanel(new Dimension(BUTTON_WIDTH, buttonPanelHeight));
        leftButtonPanel.setLayout(new BoxLayout(leftButtonPanel, BoxLayout.Y_AXIS));
        leftButtonPanel.setOpaque(false);
        leftButtonPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, COMPONENT_GAP));

        // 初始化按钮
        Image pauseIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/pause.png"))).getImage();
        pauseIcon = pauseIcon.getScaledInstance(20, 20, Image.SCALE_SMOOTH);
        pauseButton = createRoundedButton(new ImageIcon(pauseIcon));

        Image stopIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/stop.png"))).getImage();
        stopIcon = stopIcon.getScaledInstance(20, 20, Image.SCALE_SMOOTH);
        JButton stopButton = createRoundedButton(new ImageIcon(stopIcon));

        leftButtonPanel.add(pauseButton);
        leftButtonPanel.add(Box.createVerticalStrut(BUTTON_GAP));
        leftButtonPanel.add(stopButton);

        // 右侧内容面板（高度强制等于按钮面板高度）
        JPanel rightContentPanel = new FixedSizePanel(new Dimension(0, buttonPanelHeight), new Dimension(Integer.MAX_VALUE, buttonPanelHeight));
        rightContentPanel.setLayout(new BoxLayout(rightContentPanel, BoxLayout.X_AXIS));
        rightContentPanel.setOpaque(false);

        // 重写背景绘制（使用颜色常量）
        rightContentPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // 使用颜色常量绘制背景
                g2.setColor(COLOR_PANEL_BG);
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, CORNER_RADIUS, CORNER_RADIUS));

                g2.setColor(COLOR_PANEL_BORDER);
                g2.draw(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, CORNER_RADIUS, CORNER_RADIUS));

                g2.dispose();
            }
        };

        // 重新设置右侧面板属性（使用常量）
        (rightContentPanel).setLayout(new BoxLayout(rightContentPanel, BoxLayout.X_AXIS));
        (rightContentPanel).setOpaque(false);
        (rightContentPanel).setPreferredSize(new Dimension(0, buttonPanelHeight));
        (rightContentPanel).setMinimumSize(new Dimension(0, buttonPanelHeight));
        (rightContentPanel).setMaximumSize(new Dimension(Integer.MAX_VALUE, buttonPanelHeight));

        // 状态指示灯
        indicator = new StatusIndicator();
        indicator.setAlignmentY(Component.CENTER_ALIGNMENT);
        indicator.setPreferredSize(indicator.getPreferredSize());
        indicator.setMaximumSize(indicator.getPreferredSize());

        // 文本+进度条面板
        JPanel textProgressPanel = new JPanel();
        textProgressPanel.setLayout(new BoxLayout(textProgressPanel, BoxLayout.Y_AXIS));
        textProgressPanel.setOpaque(false);
        textProgressPanel.setBorder(BorderFactory.createEmptyBorder(0, COMPONENT_GAP, 0, 0));
        textProgressPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, buttonPanelHeight));

        // 任务名称（使用颜色常量）
        missionNameLabel = new JLabel(missionName);
        missionNameLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        missionNameLabel.setForeground(COLOR_MISSION_NAME);  // 替换为常量
        missionNameLabel.setAlignmentY(Component.CENTER_ALIGNMENT);

        // 进度条（使用颜色常量）
        progressBar = new RoundedProgressBar(0, 100);
        progressBar.setBackground(COLOR_PROGRESS_BG);        // 替换为常量
        progressBar.setForeground(COLOR_PROGRESS_FG);        // 替换为常量
        progressBar.setStringPainted(false);
        progressBar.setPreferredSize(new Dimension(0, PROGRESS_BAR_HEIGHT));
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, PROGRESS_BAR_HEIGHT));

        // 组装文本面板
        textProgressPanel.add(Box.createVerticalGlue());
        textProgressPanel.add(missionNameLabel);
        textProgressPanel.add(Box.createVerticalStrut(COMPONENT_GAP));
        textProgressPanel.add(progressBar);
        textProgressPanel.add(Box.createVerticalGlue());

        // 组装右侧面板
        (rightContentPanel).add(indicator);
        (rightContentPanel).add(textProgressPanel);
        (rightContentPanel).add(Box.createHorizontalGlue());

        // 组装整体
        add(leftButtonPanel, BorderLayout.WEST);
        add(rightContentPanel, BorderLayout.CENTER);

        // 按钮事件
        pauseButton.addActionListener(e -> SwingUtilities.invokeLater(this::handlePauseClick));
        stopButton.addActionListener(e -> SwingUtilities.invokeLater(this::handleStopClick));

        // 默认回调
        operationCallback = new OperationCallback() {
            @Override
            public boolean onPause() {
                return true;
            }

            @Override
            public boolean onResume() {
                return true;
            }

            @Override
            public boolean onStop() {
                return true;
            }
        };

        // 强制刷新布局
        revalidate();
        repaint();
    }

    // 固定尺寸面板工具类
    private static class FixedSizePanel extends JPanel {
        private final Dimension preferredSize;
        private final Dimension maximumSize;

        public FixedSizePanel(Dimension preferredSize) {
            this.preferredSize = preferredSize;
            this.maximumSize = preferredSize;
        }

        public FixedSizePanel(Dimension preferredSize, Dimension maximumSize) {
            this.preferredSize = preferredSize;
            this.maximumSize = maximumSize;
        }

        @Override
        public Dimension getPreferredSize() {
            return preferredSize;
        }

        @Override
        public Dimension getMinimumSize() {
            return preferredSize;
        }

        @Override
        public Dimension getMaximumSize() {
            return maximumSize;
        }
    }

    /**
     * 创建圆角按钮（所有颜色替换为常量）
     */
    private JButton createRoundedButton(ImageIcon icon) {
        JButton button = new JButton(icon) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT);
            }

            @Override
            public Dimension getMinimumSize() {
                return getPreferredSize();
            }

            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // 按钮背景色（使用常量）
                Color bgColor;
                if (getModel().isRollover()) {
                    bgColor = COLOR_BUTTON_HOVER_BG;       // 替换为常量
                } else if (getModel().isPressed()) {
                    bgColor = COLOR_BUTTON_PRESSED_BG;     // 替换为常量
                } else {
                    bgColor = COLOR_BUTTON_NORMAL_BG;      // 替换为常量
                }
                g2.setColor(bgColor);

                // 绘制按钮背景
                g2.fill(new RoundRectangle2D.Double(
                        0, 0, getWidth() - 1, getHeight() - 1,
                        BUTTON_CORNER_RADIUS, BUTTON_CORNER_RADIUS));

                // 按钮边框（使用常量）
                g2.setColor(COLOR_BUTTON_BORDER);          // 替换为常量
                g2.draw(new RoundRectangle2D.Double(
                        0, 0, getWidth() - 1, getHeight() - 1,
                        BUTTON_CORNER_RADIUS, BUTTON_CORNER_RADIUS));

                super.paintComponent(g2);
                g2.dispose();
            }
        };

        // 按钮样式
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setForeground(COLOR_BUTTON_TEXT_NORMAL);    // 替换为常量

        // 按钮文字颜色交互（使用常量）
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setForeground(COLOR_BUTTON_TEXT_HOVER); // 替换为常量
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setForeground(COLOR_BUTTON_TEXT_NORMAL); // 替换为常量
            }
        });

        return button;
    }

    // 暂停/继续点击处理
    private void handlePauseClick() {
        boolean success;
        if (!isPaused) {
            success = operationCallback.onPause();
            if (success) {
                isPaused = true;
                Image runIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/run.png"))).getImage();
                runIcon = runIcon.getScaledInstance(20, 20, Image.SCALE_SMOOTH);
                pauseButton.setIcon(new ImageIcon(runIcon));
                indicator.setStatus(IndicatorStatus.WAITING_OTHER);
            }
        } else {
            success = operationCallback.onResume();
            if (success) {
                isPaused = false;
                Image pauseIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/pause.png"))).getImage();
                pauseIcon = pauseIcon.getScaledInstance(20, 20, Image.SCALE_SMOOTH);
                pauseButton.setIcon(new ImageIcon(pauseIcon));
                indicator.setStatus(IndicatorStatus.RUNNING);
            }
        }
    }

    // 停止点击处理
    private void handleStopClick() {
        boolean success = operationCallback.onStop();
        if (success) {
            isPaused = false;
            Image pauseIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/SpigotCT/icon/pause.png"))).getImage();
            pauseIcon = pauseIcon.getScaledInstance(20, 20, Image.SCALE_SMOOTH);
            pauseButton.setIcon(new ImageIcon(pauseIcon));
            progressBar.setValue(0);
            indicator.setStatus(IndicatorStatus.IDLE);
        }
    }

    // 对外方法
    public void setMissionName(String missionName) {
        missionNameLabel.setText(missionName);
        revalidate();
        repaint();
    }

    public void setProgressValue(int value) {
        progressBar.setWaiting(false);
        progressBar.setValue(Math.max(0, Math.min(100, value)));
    }

    public void setWaiting(boolean waiting) {
        progressBar.setWaiting(waiting);
        if (waiting && !isPaused) {
            indicator.setStatus(IndicatorStatus.WAITING_SELF);
        }
    }

    public void setIndicatorStatus(IndicatorStatus status) {
        indicator.setStatus(status);
        if (status == IndicatorStatus.WAITING_OTHER) {
            progressBar.setWaiting(true);
        }
    }

    public void setOperationCallback(OperationCallback callback) {
        this.operationCallback = Objects.requireNonNull(callback);
    }

    public RoundedProgressBar getProgressBar() {
        return progressBar;
    }

    public StatusIndicator getIndicator() {
        return indicator;
    }

    // 重写MissionTip的尺寸方法
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(0, FIXED_TOTAL_HEIGHT);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, FIXED_TOTAL_HEIGHT);
    }

}