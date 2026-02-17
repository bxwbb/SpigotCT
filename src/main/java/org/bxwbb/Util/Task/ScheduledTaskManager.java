package org.bxwbb.Util.Task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * 应用程序全局定时任务管理器（毫秒版+指数型动态延迟）
 * 支持：
 * 1. 固定间隔任务（核心业务，scheduleAtFixedRate）
 * 2. 指数型动态延迟任务（非重要任务如UI更新，繁忙时延迟指数上升）
 * 3. 固定延迟任务（任务执行完毕后等待指定时间再执行，scheduleWithFixedDelay）
 */
public class ScheduledTaskManager {
    private static volatile ScheduledTaskManager INSTANCE;

    private final ScheduledExecutorService scheduledExecutor;

    private final Map<String, DynamicTaskWrapper> taskMap;

    private static final double EXPONENT_FACTOR = 2.0;

    // 日志
    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskManager.class);

    /**
     * 动态延迟任务的包装类，存储任务核心参数
     */
    private static class DynamicTaskWrapper {
        final Runnable task;
        final long minDelayMillis;
        final long maxDelayMillis;
        ScheduledFuture<?> future;

        public DynamicTaskWrapper(Runnable task, long minDelayMillis, long maxDelayMillis) {
            this.task = task;
            this.minDelayMillis = minDelayMillis;
            this.maxDelayMillis = maxDelayMillis;
        }
    }

    // 私有构造器（禁止外部实例化）
    private ScheduledTaskManager() {
        this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "GlobalScheduledTask-Thread");
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, e) -> log.error("定时任务线程异常", e));
            return thread;
        });

        this.taskMap = new ConcurrentHashMap<>();
    }

    /**
     * 获取单例实例（双重检查锁，线程安全）
     */
    public static ScheduledTaskManager getInstance() {
        if (INSTANCE == null) {
            synchronized (ScheduledTaskManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ScheduledTaskManager();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 检测当前程序繁忙度（0.0~1.0），用于动态调整延迟
     * 核心逻辑：仅基于【JVM进程CPU使用率】+【系统负载】计算，跨平台兼容Linux/Mac/Windows
     * 优先级：进程CPU使用率 > 系统负载（进程CPU更精准反映当前程序繁忙度）
     * @return 繁忙度（0=完全空闲，1=极度繁忙，异常兜底返回0.2）
     */
    private double getSystemBusyLevel() {
        double finalBusyLevel = 0.2;

        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            int cpuCoreNum = Runtime.getRuntime().availableProcessors();
            double processCpuLoad;
            double systemLoadNormalized = 0.0;

            try {
                Class<?> sunOsBeanClz = Class.forName("com.sun.management.OperatingSystemMXBean");
                Method getProcessCpuLoadMethod = sunOsBeanClz.getMethod("getProcessCpuLoad");
                processCpuLoad = (double) getProcessCpuLoadMethod.invoke(osBean);
                processCpuLoad = Math.max(0.0, Math.min(1.0, processCpuLoad));
            } catch (Exception e) {
                processCpuLoad = 0.0;
                log.warn("获取进程CPU使用率失败 -> ", e);
            }

            double systemLoadAverage = osBean.getSystemLoadAverage();
            if (systemLoadAverage > 0) {
                systemLoadNormalized = Math.min(systemLoadAverage / cpuCoreNum, 1.0);
                systemLoadNormalized = Math.max(0.0, systemLoadNormalized);
            }

            if (processCpuLoad > 0) {
                finalBusyLevel = (processCpuLoad * 0.7) + (systemLoadNormalized * 0.3);
            } else {
                finalBusyLevel = systemLoadNormalized;
            }

            finalBusyLevel = Math.max(0.0, Math.min(1.0, finalBusyLevel));

        } catch (Exception e) {
            log.error("计算繁忙度时出现错误 -> ", e);
        }

        return finalBusyLevel;
    }

    /**
     * 指数型动态延迟计算（核心修改点）
     * 逻辑：繁忙度越高，延迟呈指数级上升，而非线性
     * @param minDelayMillis 最小延迟（毫秒）
     * @param maxDelayMillis 最大延迟（毫秒）
     * @return 指数型动态延迟时间（毫秒），钳制在min~max之间
     */
    private long calculateDynamicDelay(long minDelayMillis, long maxDelayMillis) {
        double busyLevel = getSystemBusyLevel();

        double exponentValue = Math.exp(EXPONENT_FACTOR * busyLevel) - 1;
        double normalizedExponent = exponentValue / (Math.exp(EXPONENT_FACTOR) - 1);
        long dynamicDelay = (long) (minDelayMillis + (maxDelayMillis - minDelayMillis) * normalizedExponent);

        return Math.max(minDelayMillis, Math.min(maxDelayMillis, dynamicDelay));
    }

    /**
     * 启动固定间隔执行的定时任务（匿名方法/拉姆达）
     * @param intervalMillis 执行间隔（毫秒）
     * @param task 要执行的任务（拉姆达/匿名Runnable）
     * @return 任务ID（用于后续停止该任务）
     */
    public String startFixedRateTask(long intervalMillis, Runnable task) {
        return startFixedRateTask(0, intervalMillis, task);
    }

    /**
     * 启动固定间隔执行的定时任务（支持初始延迟）
     * @param initialDelayMillis 初始延迟（毫秒，0=立即执行第一次）
     * @param intervalMillis 执行间隔（毫秒）
     * @param task 要执行的任务（拉姆达/匿名Runnable）
     * @return 任务ID（用于后续停止该任务）
     */
    public String startFixedRateTask(long initialDelayMillis, long intervalMillis, Runnable task) {
        if (task == null) {
            return null;
        }
        if (intervalMillis <= 0) {
            return null;
        }

        String taskId = UUID.randomUUID().toString();

        Runnable wrappedTask = () -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("固定间隔任务执行异常（任务ID：{}）", taskId, e);
            }
        };

        ScheduledFuture<?> future = scheduledExecutor.scheduleAtFixedRate(
                wrappedTask,
                initialDelayMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS
        );

        DynamicTaskWrapper wrapper = new DynamicTaskWrapper(task, 0, 0);
        wrapper.future = future;
        taskMap.put(taskId, wrapper);

        return taskId;
    }

    /**
     * 启动固定延迟执行的定时任务（核心新增方法）
     * 逻辑：任务执行完毕后，等待指定延迟时间，再执行下一次（和固定间隔的核心区别）
     * @param delayMillis 任务执行完毕后的等待时间（毫秒）
     * @param task 要执行的任务（拉姆达/匿名Runnable）
     * @return 任务ID（用于后续停止该任务）
     */
    public String startFixedDelayTask(long delayMillis, Runnable task) {
        return startFixedDelayTask(0, delayMillis, task);
    }

    /**
     * 启动固定延迟执行的定时任务（支持初始延迟，重载方法）
     * @param initialDelayMillis 初始延迟（毫秒，0=立即执行第一次）
     * @param delayMillis 任务执行完毕后的等待时间（毫秒）
     * @param task 要执行的任务（拉姆达/匿名Runnable）
     * @return 任务ID（用于后续停止该任务）
     */
    public String startFixedDelayTask(long initialDelayMillis, long delayMillis, Runnable task) {
        if (task == null) {
            return null;
        }
        if (delayMillis <= 0) {
            return null;
        }

        String taskId = UUID.randomUUID().toString();

        Runnable wrappedTask = () -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("固定延迟任务执行异常（任务ID：{}）", taskId, e);
            }
        };

        ScheduledFuture<?> future = scheduledExecutor.scheduleWithFixedDelay(
                wrappedTask,
                initialDelayMillis,
                delayMillis,
                TimeUnit.MILLISECONDS
        );

        DynamicTaskWrapper wrapper = new DynamicTaskWrapper(task, 0, 0);
        wrapper.future = future;
        taskMap.put(taskId, wrapper);

        return taskId;
    }

    /**
     * 启动指数型动态延迟的定时任务（非重要任务如UI更新）
     * 延迟时间根据程序繁忙度呈指数级调整，钳制在min~max之间：
     * - 程序空闲 → 延迟接近最小值（更新频繁）
     * - 程序繁忙 → 延迟指数级上升（接近最大值，减少资源消耗）
     * @param minDelayMillis 最小延迟（毫秒，程序空闲时的间隔）
     * @param maxDelayMillis 最大延迟（毫秒，程序繁忙时的间隔）
     * @param task 要执行的任务（拉姆达/匿名Runnable，非重要任务如UI进度更新）
     * @return 任务ID（用于后续停止该任务）
     */
    public String startDynamicDelayTask(long minDelayMillis, long maxDelayMillis, Runnable task) {
        if (task == null) {
            return null;
        }
        if (minDelayMillis < 0 || maxDelayMillis < 0 || minDelayMillis > maxDelayMillis) {
            return null;
        }

        String taskId = UUID.randomUUID().toString();

        DynamicTaskWrapper wrapper = new DynamicTaskWrapper(task, minDelayMillis, maxDelayMillis);
        taskMap.put(taskId, wrapper);

        Runnable dynamicTask = new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();

                    if (!taskMap.containsKey(taskId) || (wrapper.future != null && wrapper.future.isCancelled())) {
                        return;
                    }

                    long delay = calculateDynamicDelay(minDelayMillis, maxDelayMillis);

                    wrapper.future = scheduledExecutor.schedule(this, delay, TimeUnit.MILLISECONDS);

                } catch (Exception e) {
                    if (taskMap.containsKey(taskId) && (wrapper.future == null || !wrapper.future.isCancelled())) {
                        long delay = calculateDynamicDelay(minDelayMillis, maxDelayMillis);
                        wrapper.future = scheduledExecutor.schedule(this, delay, TimeUnit.MILLISECONDS);
                    }
                }
            }
        };

        wrapper.future = scheduledExecutor.schedule(dynamicTask, minDelayMillis, TimeUnit.MILLISECONDS);
        return taskId;
    }

    /**
     * 停止指定ID的定时任务（支持固定间隔/动态延迟/固定延迟任务）
     * @param taskId 任务ID（启动任务时返回的字符串）
     * @return true=停止成功，false=任务不存在/已停止
     */
    public boolean stopTask(String taskId) {
        if (taskId == null || !taskMap.containsKey(taskId)) {
            return false;
        }

        DynamicTaskWrapper wrapper = taskMap.get(taskId);
        boolean isCancelled = (wrapper.future != null) && wrapper.future.cancel(false); // 允许当前任务执行完成
        if (isCancelled) {
            taskMap.remove(taskId);
        } else {
            log.error("停止定时任务失败：任务已完成/已停止（任务ID：{}）", taskId);
        }
        return isCancelled;
    }

    /**
     * 停止所有定时任务
     */
    public void stopAllTasks() {
        if (taskMap.isEmpty()) {
            return;
        }

        taskMap.forEach((taskId, wrapper) -> {
            if (wrapper.future != null) {
                wrapper.future.cancel(false);
            }
        });
        taskMap.clear();
    }

    /**
     * 优雅关闭定时线程池（等待所有任务完成后关闭）
     */
    public void shutdown() {
        stopAllTasks();
        scheduledExecutor.shutdown();
        try {
            if (!scheduledExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
            log.error("定时线程池关闭被中断", e);
        }
    }

    /**
     * 获取当前运行中的任务数量
     */
    public int getRunningTaskCount() {
        return taskMap.size();
    }

}