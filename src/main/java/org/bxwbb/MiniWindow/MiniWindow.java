package org.bxwbb.MiniWindow;

import org.bxwbb.Setting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

public abstract class MiniWindow extends ResizablePanel {

    private static final Logger log = LoggerFactory.getLogger(MiniWindow.class);
    public String NAME = "错误";
    public String ICON_PATH = "/SpigotCT/icon/MiniWindowIcon/StartPage.png";
    private JPanel topPanel = new JPanel();
    private JPanel centerPanel = new JPanel();

    public MiniWindow(Class<? extends MiniWindow> clazz) {
        super();
        this.setOpaque(false);
        for (String s : MiniWindowEnum.getWindowInfoMap().keySet()) {
            MiniWindowEnum.MiniWindowInfo miniWindowInfo = MiniWindowEnum.getWindowInfo(s);
            if (clazz.equals(miniWindowInfo.clazz())) {
                NAME = s;
                ICON_PATH = miniWindowInfo.path();
                break;
            }
        }
        Jinit();
        init();
    }

    public abstract void init();

    @Override
    public Cursor getCursorByPosition(DragPosition pos) {
        return switch (pos) {
            case CORNER_TOP_LEFT, CORNER_BOTTOM_RIGHT, CORNER_TOP_RIGHT, CORNER_BOTTOM_LEFT ->
                    Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
            case BORDER_TOP, BORDER_BOTTOM -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
            case BORDER_LEFT, BORDER_RIGHT -> Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
            default -> Cursor.getDefaultCursor();
        };
    }

    public void Jinit() {
        BorderLayout borderLayout = new BorderLayout();
        borderLayout.setVgap(1);
        this.setLayout(borderLayout);
        getTopPanel().setLayout(new FlowLayout(FlowLayout.LEFT));
        getTopPanel().setBackground(Setting.BACKGROUND_COLOR);
        getCenterPanel().setBackground(Setting.BACKGROUND_COLOR);
        this.add(getTopPanel(), BorderLayout.NORTH);
        this.add(getCenterPanel(), BorderLayout.CENTER);
        JComboBox<IconItem> comboBox = new JComboBox<>();
        ImageIcon icon = new ImageIcon(Objects.requireNonNull(getClass().getResource(ICON_PATH)));
        icon = new ImageIcon(icon.getImage().getScaledInstance(20, 20, Image.SCALE_SMOOTH));
        comboBox.addItem(new IconItem(icon, NAME));
        comboBox.setRenderer((list, item, index, isSelected, cellHasFocus) -> {
            // 创建JLabel作为渲染组件（显示图标+文字）
            JLabel label = new JLabel();
            if (item != null) {
                label.setIcon(item.imageIcon()); // 设置图标
                label.setText(item.text()); // 设置文字
                label.setIconTextGap(8); // 图标和文字的间距
            }
            // 设置选中/未选中的背景色（贴合Swing默认风格）
            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            } else {
                label.setBackground(list.getBackground());
                label.setForeground(list.getForeground());
            }
            label.setOpaque(true); // 必须设置不透明，否则背景色不生效
            label.setHorizontalAlignment(JLabel.LEFT);
            return label;
        });
        comboBox.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                comboBox.removeAllItems();
                ImageIcon icon;
                MiniWindowEnum.MiniWindowInfo miniWindowInfo;
                int index = 0;
                boolean flag = true;
                for (String s : MiniWindowEnum.getWindowInfoMap().keySet()) {
                    miniWindowInfo = MiniWindowEnum.getWindowInfo(s);
                    icon = new ImageIcon(Objects.requireNonNull(getClass().getResource(miniWindowInfo.path())));
                    icon = new ImageIcon(icon.getImage().getScaledInstance(20, 20, Image.SCALE_SMOOTH));
                    comboBox.addItem(new IconItem(icon, s));
                    if (s.equals(NAME)) flag = false;
                    if (flag) index++;
                }
                comboBox.setSelectedIndex(index);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
        comboBox.addActionListener(e -> {
            IconItem item = (IconItem) comboBox.getSelectedItem();
            if (item != null && comboBox.isPopupVisible()) {
                Container parent = comboBox.getParent().getParent().getParent();
                MiniWindowEnum.MiniWindowInfo miniWindowInfo = MiniWindowEnum.getWindowInfo(item.text());
                MiniWindow miniWindow;
                try {
                    miniWindow = miniWindowInfo.clazz().getDeclaredConstructor().newInstance();
                    miniWindow.setSize(comboBox.getParent().getParent().getSize());
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                         NoSuchMethodException ex) {
                    log.error("切换窗口类型出现错误", ex);
                    return;
                }
                // 使用miniWindow替换parent中的this
                if (parent == null) {
                    log.error("无法替换组件，父组件为空");
                    return;
                }

                int comboIndex = parent.getComponentZOrder(comboBox.getParent().getParent());
                if (comboIndex < 0) {
                    comboIndex = parent.getComponentCount();
                    log.warn("未获取到comboBox的索引，将插入到容器最后位置");
                }

                parent.remove(comboBox.getParent().getParent());

                parent.add(miniWindow, comboIndex);

                parent.revalidate();
                parent.repaint();
            }
        });
        getTopPanel().add(comboBox);
        MiniWindow thisMiniWindow = this;
        this.addResizeListener(new ResizeListener() {
            DragPosition dragPosition = DragPosition.NONE;
            boolean notCreate = true;

            @Override
            public void resizeStarted(ResizeEvent e) {
                dragPosition = e.getDragPosition();
            }

            @Override
            public void resizing(ResizeEvent e) {
                if (thisMiniWindow.getSize().width <= Setting.windowSplitDistance ||
                        thisMiniWindow.getSize().height <= Setting.windowSplitDistance) return;
                if (Math.abs(e.getDx()) >= Setting.windowSplitDistance || Math.abs(e.getDy()) >= Setting.windowSplitDistance) {
                    if (Math.abs(e.getDx()) > Math.abs(e.getDy())) {
                        switch (dragPosition) {
                            case CORNER_TOP_LEFT, CORNER_BOTTOM_LEFT -> {
                                if (e.getDx() > 0) {
                                    if (notCreate) {
                                        createWindow(true, false);
                                        notCreate = false;
                                    }
                                }
                            }
                            case CORNER_TOP_RIGHT, CORNER_BOTTOM_RIGHT -> {
                                if (e.getDx() < 0) {
                                    if (notCreate) {
                                        createWindow(false, false);
                                        notCreate = false;
                                    }
                                }
                            }
                        }
                    } else {
                        switch (e.getDragPosition()) {
                            case CORNER_TOP_LEFT, CORNER_TOP_RIGHT -> {
                                if (e.getDy() > 0) {
                                    if (notCreate) {
                                        createWindow(true, true);
                                        notCreate = false;
                                    }
                                }
                            }
                            case CORNER_BOTTOM_LEFT, CORNER_BOTTOM_RIGHT -> {
                                if (e.getDy() < 0) {
                                    if (notCreate) {
                                        createWindow(false, true);
                                        notCreate = false;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void resizeEnded(ResizeEvent e) {
                notCreate = true;
            }

            public void createWindow(boolean lrud, boolean hv) {
                Dimension windowSize = thisMiniWindow.getSize();
                windowSize.width -= Setting.windowSplitDistance;
                thisMiniWindow.resetSize(windowSize);
                JPanel pj = (JPanel) thisMiniWindow.getParent();
                JPanel parentPanel;
                boolean axisSame = true;
                if (((BoxLayout) pj.getLayout()).getAxis() == (hv ? BoxLayout.Y_AXIS : BoxLayout.X_AXIS)) {
                    parentPanel = pj;
                } else {
                    parentPanel = new JPanel();
                    BoxLayout boxLayout = new BoxLayout(parentPanel, hv ? BoxLayout.Y_AXIS : BoxLayout.X_AXIS);
                    parentPanel.setLayout(boxLayout);
                    parentPanel.setBorder(BorderFactory.createEmptyBorder(Setting.windowGap, Setting.windowGap, Setting.windowGap, Setting.windowGap));
                    axisSame = false;
                }
                MiniWindow newMiniWindow;
                try {
                    newMiniWindow = thisMiniWindow.createSameTypeWindow();
                    newMiniWindow.resetSize(new Dimension(Setting.windowSplitDistance, windowSize.height));
                    if (axisSame) {
                        if (lrud) {
                            parentPanel.add(newMiniWindow, parentPanel.getComponentZOrder(thisMiniWindow));
                            parentPanel.add(Box.createHorizontalStrut(Setting.windowGap), parentPanel.getComponentZOrder(thisMiniWindow));
                        } else {
                            parentPanel.add(Box.createHorizontalStrut(Setting.windowGap));
                            parentPanel.add(newMiniWindow);
                        }
                    } else {
                        if (lrud) {
                            parentPanel.add(newMiniWindow, parentPanel.getComponentZOrder(thisMiniWindow));
                            parentPanel.add(Box.createHorizontalStrut(Setting.windowGap), parentPanel.getComponentZOrder(thisMiniWindow));
                        } else {
                            parentPanel.add(Box.createHorizontalStrut(Setting.windowGap));
                            parentPanel.add(newMiniWindow);
                        }
                        pj.add(parentPanel, parentPanel.getComponentZOrder(thisMiniWindow));
                        pj.remove(thisMiniWindow);
                        parentPanel.add(thisMiniWindow, 0);
                    }
                } catch (NoSuchMethodException e) {
                    log.error("没有找到这个窗口对应的类 - {}", thisMiniWindow.NAME);
                } catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
                    log.error("创建窗口失败 - {} -> {}", thisMiniWindow.NAME, e.toString());
                }
                pj.revalidate();
                pj.repaint();
            }

        });
    }

    public JPanel getTopPanel() {
        return topPanel;
    }

    public void setTopPanel(JPanel topPanel) {
        this.topPanel = topPanel;
        topPanel.setBackground(Setting.BACKGROUND_COLOR);
        this.add(topPanel, BorderLayout.NORTH);
    }

    public JPanel getCenterPanel() {
        return centerPanel;
    }

    public void setCenterPanel(JPanel centerPanel) {
        this.centerPanel = centerPanel;
        centerPanel.setBackground(Setting.BACKGROUND_COLOR);
        this.add(centerPanel, BorderLayout.CENTER);
    }

    public MiniWindow createSameTypeWindow() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        MiniWindowEnum.MiniWindowInfo miniWindowInfo = MiniWindowEnum.getWindowInfo(this.NAME);
        return miniWindowInfo.clazz().getDeclaredConstructor().newInstance();
    }

    public void resetSize(Dimension dimension) {
        this.setPreferredSize(dimension);
        this.setMaximumSize(dimension);
        this.setMinimumSize(new Dimension(Setting.windowSplitDistance, Setting.windowSplitDistance));
    }

    public ImageIcon getIcon() {
        ImageIcon icon = new ImageIcon(Objects.requireNonNull(getClass().getResource(ICON_PATH)));
        return new ImageIcon(icon.getImage().getScaledInstance(20, 20, Image.SCALE_SMOOTH));
    }

    public record IconItem(ImageIcon imageIcon, String text) {
    }

}
