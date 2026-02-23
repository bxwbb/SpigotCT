package org.bxwbb.UI.Animation;

import org.bxwbb.Util.Task.ScheduledTaskManager;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class AnimationTask<T extends Number> {

    private AnimationValue<T> animationValue;
    private final UUID taskId = UUID.randomUUID();
    private InterpolationFunction interpolationFunction;
    private long animationTime;
    private T startNumber, endNumber;
    private AnimationTask<T> interruptAnimation;
    private final AtomicBoolean interrupted = new AtomicBoolean(false);
    protected long startTime;
    protected String taskID;

    public AnimationTask(AnimationValue<T> animationValue, long animationTime, T startNumber, T endNumber) {
        this.animationValue = animationValue;
        this.animationTime = animationTime;
        this.startNumber = startNumber;
        this.endNumber = endNumber;
    }

    protected void updateAnimation() {
        double p = Math.max(1.0, Math.min(0.0, (double) (System.currentTimeMillis() - startTime) / getAnimationTime()));
        animationValue.setValue(interpolationFunction.interpolation(p));
        if (System.currentTimeMillis() - startTime > getAnimationTime()) stop();
    }

    public boolean isInterrupted() {
        return interrupted.get();
    }

    public void interrupt() {
        interrupted.set(true);
        AnimationControl.getInstance().stopAnimation(this);
        getInterruptAnimation().setStartNumber(getAnimationValue().getValue());
        AnimationControl.getInstance().startAnimation(getInterruptAnimation());
    }

    private void stop() {
        ScheduledTaskManager.getInstance().stopTask(taskID);
    }

    public AnimationValue<T> getAnimationValue() {
        return animationValue;
    }

    public void setAnimationValue(AnimationValue<T> animationValue) {
        this.animationValue = animationValue;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public InterpolationFunction getInterpolationFunction() {
        return interpolationFunction;
    }

    public void setInterpolationFunction(InterpolationFunction interpolationFunction) {
        this.interpolationFunction = interpolationFunction;
    }

    public long getAnimationTime() {
        return animationTime;
    }

    public void setAnimationTime(long animationTime) {
        this.animationTime = animationTime;
    }

    public T getStartNumber() {
        return startNumber;
    }

    public void setStartNumber(T startNumber) {
        this.startNumber = startNumber;
    }

    public T getEndNumber() {
        return endNumber;
    }

    public void setEndNumber(T endNumber) {
        this.endNumber = endNumber;
    }

    public AnimationTask<T> getInterruptAnimation() {
        return interruptAnimation;
    }

    public void setInterruptAnimation(AnimationTask<T> interruptAnimation) {
        this.interruptAnimation = interruptAnimation;
    }

}
