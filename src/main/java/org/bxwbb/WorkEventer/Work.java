package org.bxwbb.WorkEventer;

import org.bxwbb.UI.IndicatorStatus;
import org.bxwbb.UI.MissionTip;
import org.bxwbb.Util.ScheduledTaskManager;

public abstract class Work implements MissionTip.OperationCallback {

    private String name;
    private int value = 0;
    private int maxValue = 100;
    private IndicatorStatus status = IndicatorStatus.RUNNING;
    protected MissionTip missionTip;
    private WorkUpdateCallBack workUpdateCallBack;
    private final String updateTaskID;

    public Work(String name) {
        this.name = name;
        updateTaskID = ScheduledTaskManager.getInstance().startDynamicDelayTask(500, 4000, () -> {
            if (workUpdateCallBack != null) workUpdateCallBack.update();
        });
    }

    public int getValue() {
        return value;
    }

    public void setValue(int newValue) {
        value = newValue;
        if (missionTip != null) {
            missionTip.setProgressValue((int) (value / (float) maxValue * 100));
        }
    }

    public int getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(int maxValue) {
        this.maxValue = maxValue;
        if (missionTip != null) {
            missionTip.setProgressValue((int) (value / (float) maxValue * 100));
        }
    }

    public WorkUpdateCallBack getWorkUpdateCallBack() {
        return workUpdateCallBack;
    }

    public void setWorkUpdateCallBack(WorkUpdateCallBack workUpdateCallBack) {
        this.workUpdateCallBack = workUpdateCallBack;
    }

    public final MissionTip getMissionTip() {
        return missionTip;
    }

    public IndicatorStatus getStatus() {
        return status;
    }

    public void setStatus(IndicatorStatus status) {
        this.status = status;
        if (missionTip != null) {
            missionTip.setIndicatorStatus(status);
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        if (missionTip != null) {
            missionTip.setMissionName(name);
        }
    }

    protected void delete() {
        ScheduledTaskManager.getInstance().stopTask(updateTaskID);
    }

    @FunctionalInterface
    public interface WorkUpdateCallBack {
        void update();
    }

}
