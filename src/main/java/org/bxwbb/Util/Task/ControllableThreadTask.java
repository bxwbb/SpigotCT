package org.bxwbb.Util.Task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 可控制的伴随线程任务（继承ControllableTask）
 * 自动创建伴随工作线程，通过ScheduledTaskManager定时检测取消/暂停状态
 * @param <V> 任务返回值类型
 */
public abstract class ControllableThreadTask<V> extends ControllableTask<V> {
    // 日志实例
    private static final Logger log = LoggerFactory.getLogger(ControllableThreadTask.class);

    // 任务核心状态（原子布尔保证线程安全）
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    // 伴随工作线程
    private Thread workerThread;
    // 状态检测任务ID（用于停止ScheduledTaskManager的检测任务）
    private String statusCheckTaskId;
    // 状态检测间隔（毫秒）
    private static final long STATUS_CHECK_INTERVAL = 100;

    // 业务逻辑：由子类实现具体工作
    protected abstract V doWork() throws Exception;

    // ===================== 核心构造器 =====================
    public ControllableThreadTask() {
        // 初始化状态检测任务
        initStatusCheckTask();
    }

    /**
     * 初始化状态检测任务（通过ScheduledTaskManager定时检测取消/暂停状态）
     */
    private void initStatusCheckTask() {
        ScheduledTaskManager taskManager = ScheduledTaskManager.getInstance();
        // 启动固定间隔的状态检测任务
        statusCheckTaskId = taskManager.startFixedRateTask(STATUS_CHECK_INTERVAL, () -> {
            if (isCancelled.get()) {
                // 检测到取消：终止工作线程
                stopWorkerThread();
                log.info("任务检测到取消指令，终止伴随线程");
            } else if (isPaused.get()) {
                // 检测到暂停：阻塞工作线程（通过wait实现，不占用CPU）
                synchronized (this) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("暂停状态等待被中断", e);
                    }
                }
            }
        });
    }

    // ===================== 任务生命周期控制（核心实现） =====================
    @Override
    public V execute() throws Exception {
        if (isRunning.compareAndSet(false, true)) {
            // 创建伴随工作线程
            workerThread = new Thread(() -> {
                try {
                    // 执行核心业务逻辑
                    V result = doWork();
                    // 任务完成：设置结果
                    setResult(result);
                } catch (Exception e) {
                    // 捕获业务异常
                    setException(e);
                    log.error("伴随线程业务逻辑执行异常", e);
                } finally {
                    // 任务结束：清理状态
                    cleanup();
                }
            }, "ControllableWorkerThread-" + UUID.randomUUID());

            // 启动工作线程
            workerThread.setDaemon(false); // 非守护线程，保证任务执行完成
            workerThread.start();

            // 等待工作线程完成（可选：根据业务是否需要同步等待）
            workerThread.join();

            // 检查是否有异常
            if (getException() != null) {
                throw getException();
            }
            return getResult();
        } else {
            throw new IllegalStateException("任务已在运行中，无法重复执行");
        }
    }

    @Override
    public void cancel() {
        if (isCancelled.compareAndSet(false, true)) {
            // 唤醒可能处于暂停等待的线程，使其快速响应取消
            synchronized (this) {
                this.notifyAll();
            }
        }
    }

    @Override
    public void pause() {
        if (!isPaused.get() && !isCancelled.get()) {
            isPaused.set(true);
            log.info("触发任务暂停操作");
        } else {
            log.warn("任务无法暂停（已取消/已暂停）");
        }
    }

    @Override
    public void resume() {
        if (isPaused.compareAndSet(true, false)) {
            log.info("触发任务恢复操作");
            // 唤醒暂停的线程
            synchronized (this) {
                this.notifyAll();
            }
        } else {
            log.warn("任务无需恢复（未暂停/已取消）");
        }
    }

    // ===================== 辅助方法 =====================
    /**
     * 停止工作线程
     */
    private void stopWorkerThread() {
        if (workerThread != null && workerThread.isAlive()) {
            // 中断线程（需在doWork中检测interrupt状态）
            workerThread.interrupt();
            log.info("工作线程已中断");
        }
    }

    /**
     * 任务完成/取消后的清理工作
     */
    private void cleanup() {
        isRunning.set(false);
        // 停止状态检测任务
        ScheduledTaskManager.getInstance().stopTask(statusCheckTaskId);
    }

    // ===================== 状态查询方法 =====================
    public boolean isTaskPaused() {
        return isPaused.get();
    }

    public boolean isTaskCancelled() {
        return isCancelled.get();
    }

    public boolean isTaskRunning() {
        return isRunning.get();
    }
}