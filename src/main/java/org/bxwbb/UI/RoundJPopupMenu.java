package org.bxwbb.UI;

import org.bxwbb.UI.Animation.AnimationUI;
import org.bxwbb.UI.Implements.Round;

import javax.swing.*;
import java.awt.*;

public class RoundJPopupMenu extends JPopupMenu implements Round, AnimationUI {
    private int roundRadius = 8;
    private Color borderColor = new Color(100, 100, 100);
    private int borderWidth = 1;
    private Shape roundShape;

    // 菜单项选中背景色
    private Color selectionBgColor = new Color(60, 60, 60);
    // 菜单项选中前景色
    private Color selectionFgColor = Color.WHITE;
    // 菜单项默认前景色
    private Color defaultFgColor = new Color(200, 200, 200);

    public RoundJPopupMenu() {
        super();
        initConfig();
    }

    public RoundJPopupMenu(String label) {
        super(label);
        initConfig();
    }

    private void initConfig() {
//        setOpaque(false);
    }

    @Override
    public void paintComponents(Graphics g) {
//        super.paintComponents(g);
    }

    @Override
    protected void paintComponent(Graphics g) {
//        super.paintComponent(g);
    }

    @Override
    public void paintImmediately(Rectangle r) {
//        super.paintImmediately(r);
    }

    @Override
    protected void paintBorder(Graphics g) {
//        super.paintBorder(g);
    }

    @Override
    public int getRoundRadius() {
        return roundRadius;
    }

    @Override
    public void setRoundRadius(int roundRadius) {
        this.roundRadius = roundRadius;
    }
}
