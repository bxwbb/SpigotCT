package org.bxwbb.WorkEventer;

import org.bxwbb.UI.ArrowedTipWindow;
import org.bxwbb.Util.FileUtil;

import javax.swing.*;

public class ShowButton extends JButton {

    private int workCount = 0;

    protected ShowButton() {
        super(FileUtil.getLang("worker.tip.worked"));
    }

    public void showInfo() {
        ArrowedTipWindow.info(this, FileUtil.getLang("worker.tip.add"));
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
            this.setText(FileUtil.getLang("worker.tip.worked"));
        } else {
            this.setText(FileUtil.getLang("worker.tip.working", String.valueOf(workCount)));
        }
    }
}
