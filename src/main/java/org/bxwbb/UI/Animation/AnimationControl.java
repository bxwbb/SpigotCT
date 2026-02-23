package org.bxwbb.UI.Animation;

import org.bxwbb.Main;
import org.bxwbb.Util.Task.ScheduledTaskManager;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AnimationControl {

    public static final int REFRESH_RATE = 30;

    public static AnimationControl getInstance() {
        return Main.animationControl;
    }

    public AnimationControl() {}

    public void startAnimation(AnimationTask<? extends Number> animationTask) {
        animationTask.startTime = System.currentTimeMillis();
        animationTask.taskID = ScheduledTaskManager.getInstance().startFixedRateTask(REFRESH_RATE, animationTask::updateAnimation);
    }

    public void stopAnimation(AnimationTask<? extends Number> animationTask) {
        ScheduledTaskManager.getInstance().stopTask(animationTask.taskID);
    }

}
