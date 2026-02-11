package org.bxwbb.WorkEventer;

import org.bxwbb.Util.FileUtil;

import javax.swing.*;

public class ShowButton {

    protected final JButton showButton;

    private int workCount = 0;

    protected ShowButton() {
        showButton = new JButton(FileUtil.getLang("worker.tip.worked"));
        showButton.setVisible(false);
    }

    public JButton getShowButton() {
        return showButton;
    }

    public int getWorkCount() {
        return workCount;
    }

    public void workCountAddOne() {
        setWorkCount(getWorkCount() + 1);
    }

    public void workCountSubOne() {
        setWorkCount(Math.max(getWorkCount() - 1, 0));
    }

    public void setWorkCount(int workCount) {
        this.workCount = workCount;
        if (workCount == 0) {
            showButton.setText(FileUtil.getLang("worker.tip.worked"));
            showButton.setVisible(false);
        } else {
            showButton.setText(FileUtil.getLang("worker.tip.working", String.valueOf(workCount)));
            showButton.setVisible(true);
        }
    }
}
