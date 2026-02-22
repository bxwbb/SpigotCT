package org.bxwbb.WorkEventer;

import org.bxwbb.UI.MissionTip;
import org.bxwbb.Util.FileUtil;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class WorkController {

    private final ShowButton showButton;
    private final List<Work> works = new ArrayList<>();
    private final JFrame jFrame;
    private final JList<MissionTip> workList;

    public WorkController() {
        showButton = new ShowButton();

        workList = new JList<>();
        workList.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JScrollPane newScrollPane = new JScrollPane(workList);
        newScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        newScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        jFrame = new JFrame(FileUtil.getLang("worker.window.title"));
        jFrame.setLayout(new BorderLayout());
        jFrame.add(newScrollPane, BorderLayout.CENTER);
        jFrame.setSize(600, 300);
        jFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        showButton.addActionListener(e -> {
            jFrame.setVisible(true);
            jFrame.setLocationRelativeTo(null);
        });
    }

    public void showInfo() {
        showButton.showInfo();
    }

    /**
     * 核心：所有MissionTip的创建、布局、添加都在这个方法完成
     */
    public void addWork(Work work) {
        if (containsWork(work)) {
            return;
        }

        MissionTip missionTip = new MissionTip(work.getName());
        missionTip.setProgressValue(work.getValue());
        missionTip.setIndicatorStatus(work.getStatus());

        work.missionTip = missionTip;
        if (work.operationCallback != null) {
            missionTip.setOperationCallback(work.operationCallback);
        }

        workList.add(missionTip);

        works.add(work);
        showButton.workCountAddOne();

        workList.revalidate();
        workList.repaint();
    }

    /**
     * 移除任务（同步移除对应的MissionTip）
     */
    public void removeWork(Work work) {
        if (!containsWork(work) || work.missionTip == null) {
            return;
        }

        work.delete();

        workList.remove(work.missionTip);

        work.missionTip = null;

        works.remove(work);
        showButton.workCountSubOne();

        workList.revalidate();
        workList.repaint();
    }

    public boolean containsWork(Work work) {
        return works.contains(work);
    }

    public ShowButton getShowButton() {
        return showButton;
    }

    public void updateWorkStatus(Work work) {
        if (work.missionTip == null) {
            return;
        }
        work.missionTip.setProgressValue(work.getValue());
        work.missionTip.setIndicatorStatus(work.getStatus());
        work.missionTip.repaint();
    }
}