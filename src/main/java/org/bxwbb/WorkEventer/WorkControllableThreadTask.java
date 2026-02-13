package org.bxwbb.WorkEventer;

import org.bxwbb.Main;
import org.bxwbb.UI.MissionTip;
import org.bxwbb.Util.Task.ControllableThreadPool;

public class WorkControllableThreadTask extends Work {

    private String taskID;

    public WorkControllableThreadTask(String name, String taskID, ControllableThreadPool controllableThreadPool) {
        super(name);
        this.taskID = taskID;
        Work self = this;
        this.setOperationCallback(new MissionTip.OperationCallback() {
            @Override
            public boolean onPause() {
                return controllableThreadPool.pauseTask(taskID);
            }

            @Override
            public boolean onResume() {
                return controllableThreadPool.resumeTask(taskID);
            }

            @Override
            public boolean onStop() {
                boolean result = controllableThreadPool.cancelTask(taskID);
                if (result) Main.getWorkController().removeWork(self);
                return result;
            }
        });
    }

    public String getTaskID() {
        return taskID;
    }

    public void setTaskID(String taskID) {
        this.taskID = taskID;
    }
}
