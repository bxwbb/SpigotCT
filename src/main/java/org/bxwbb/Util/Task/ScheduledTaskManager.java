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
 * 1. 固定间隔任务（核心业务）
 * 2. 指数型动态延迟任务（非重要任务如UI更新，繁忙时延迟指数上升）
 */
public class ScheduledTaskManager {
    // 单例实例（懒汉式，线程安全）
    private static volatile ScheduledTaskManager INSTANCE;

    // 定时线程池（核心线程数1，适配单个/少量定时任务场景）
    private final ScheduledExecutorService scheduledExecutor;

    // 任务缓存：key=任务ID，value=任务包装类（含原始任务+动态参数）
    private final Map<String, DynamicTaskWrapper> taskMap;

    // 指数强度因子（可调节：值越大，繁忙时延迟上升越快，建议1~5）
    private static final double EXPONENT_FACTOR = 2.0;

    // 日志
    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskManager.class);

    // ===================== 动态任务包装类（内部使用） =====================
    /**
     * 动态延迟任务的包装类，存储任务核心参数
     */
    private static class DynamicTaskWrapper {
        final Runnable task;                // 原始任务
        final long minDelayMillis;          // 最小延迟（毫秒）
        final long maxDelayMillis;          // 最大延迟（毫秒）
        ScheduledFuture<?> future;          // 任务Future（用于取消）

        public DynamicTaskWrapper(Runnable task, long minDelayMillis, long maxDelayMillis) {
            this.task = task;
            this.minDelayMillis = minDelayMillis;
            this.maxDelayMillis = maxDelayMillis;
        }
    }

    // 私有构造器（禁止外部实例化）
    private ScheduledTaskManager() {
        // 初始化定时线程池（守护线程，避免阻塞程序退出）
        this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "GlobalScheduledTask-Thread");
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, e) -> log.error("定时任务线程异常 -> ", e));
            return thread;
        });

        // 并发HashMap，保证多线程下任务缓存安全
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

    // ===================== 核心工具方法：指数型延迟计算 =====================
    /**
     * 检测当前程序繁忙度（0.0~1.0），用于动态调整延迟
     * 核心逻辑：仅基于【JVM进程CPU使用率】+【系统负载】计算，跨平台兼容Linux/Mac/Windows
     * 优先级：进程CPU使用率 > 系统负载（进程CPU更精准反映当前程序繁忙度）
     * @return 繁忙度（0=完全空闲，1=极度繁忙，异常兜底返回0.2）
     */
    private double getSystemBusyLevel() {
        // 日志实例（复用当前类日志）
        final Logger log = LoggerFactory.getLogger(this.getClass());
        // 兜底默认值：低繁忙度，避免异常导致动态延迟逻辑失效
        double finalBusyLevel = 0.2;

        try {
            // 1. 初始化核心指标
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            int cpuCoreNum = Runtime.getRuntime().availableProcessors(); // CPU核心数
            double processCpuLoad; // 当前JVM进程CPU使用率（0~1，优先使用）
            double systemLoadNormalized; // 系统负载归一化值（0~1，兜底使用）

            // 2. 反射获取JVM进程CPU使用率（跨平台核心，兼容Linux/Mac/Windows）
            // com.sun.management.OperatingSystemMXBean 为JDK通用内部API，主流JDK均支持
            try {
                Class<?> sunOsBeanClz = Class.forName("com.sun.management.OperatingSystemMXBean");
                // 获取进程CPU使用率方法（返回值0.0~1.0，精准反映当前程序的CPU占用）
                Method getProcessCpuLoadMethod = sunOsBeanClz.getMethod("getProcessCpuLoad");
                // 强转并调用方法
                processCpuLoad = (double) getProcessCpuLoadMethod.invoke(osBean);
                // 边界校验：确保值在0~1之间（避免JVM返回异常值）
                processCpuLoad = Math.max(0.0, Math.min(1.0, processCpuLoad));
            } catch (Exception e) {
                // 异常兜底（极少数JDK不兼容场景）
                log.warn("反射获取进程CPU使用率失败（兼容兜底），原因：{}", e.getMessage());
                processCpuLoad = 0.0;
            }

            // 3. 计算系统负载归一化值（Linux/Mac有效，Windows返回-1，做兜底处理）
            double systemLoadAverage = osBean.getSystemLoadAverage();
            if (systemLoadAverage > 0) {
                // 系统负载归一化：系统负载 / CPU核心数，确保值在0~1之间
                systemLoadNormalized = Math.min(systemLoadAverage / cpuCoreNum, 1.0);
                systemLoadNormalized = Math.max(0.0, systemLoadNormalized);
            } else {
                systemLoadNormalized = 0.0;
            }

            // 4. 综合计算繁忙度（核心权重分配：进程CPU使用率70% + 系统负载30%）
            // 优先级：进程CPU使用率（精准反映当前程序）> 系统负载（反映整个系统）
            if (processCpuLoad > 0) {
                finalBusyLevel = (processCpuLoad * 0.7) + (systemLoadNormalized * 0.3);
            } else {
                // 进程CPU获取失败：直接使用系统负载作为繁忙度
                finalBusyLevel = systemLoadNormalized;
            }

            // 最终边界校验：确保繁忙度严格在0~1之间
            finalBusyLevel = Math.max(0.0, Math.min(1.0, finalBusyLevel));

        } catch (Exception e) {
            // 全局异常兜底：任何错误都返回默认低繁忙度，保证上层逻辑不中断
            log.warn("检测系统繁忙度发生全局异常，使用默认值0.2，原因：{}", e.getMessage(), e);
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

        // 指数型计算核心公式：
        // 1. 先通过指数函数放大繁忙度：exp(EXPONENT_FACTOR * busyLevel) - 1
        // 2. 归一化到0~1区间：(exp(x)-1)/(exp(EXPONENT_FACTOR)-1)
        // 3. 最终延迟 = 最小值 + (最大值-最小值) * 归一化后的指数值
        double exponentValue = Math.exp(EXPONENT_FACTOR * busyLevel) - 1;
        double normalizedExponent = exponentValue / (Math.exp(EXPONENT_FACTOR) - 1);
        long dynamicDelay = (long) (minDelayMillis + (maxDelayMillis - minDelayMillis) * normalizedExponent);

        // 最终钳制在min~max之间（防止计算误差）
        return Math.max(minDelayMillis, Math.min(maxDelayMillis, dynamicDelay));
    }

    // ===================== 对外暴露的API =====================

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
        // 空任务防护
        if (task == null) {
            log.warn("定时任务启动失败：任务为null");
            return null;
        }
        // 非法时间参数防护（间隔必须>0）
        if (intervalMillis <= 0) {
            log.warn("定时任务启动失败：执行间隔必须大于0毫秒 - {}", intervalMillis);
            return null;
        }

        // 生成唯一任务ID
        String taskId = UUID.randomUUID().toString();

        // 包装任务（添加日志和异常捕获）
        Runnable wrappedTask = () -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("固定间隔任务执行异常（任务ID：{}）-> ", taskId, e);
            }
        };

        // 提交任务到线程池
        ScheduledFuture<?> future = scheduledExecutor.scheduleAtFixedRate(
                wrappedTask,
                initialDelayMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS
        );

        // 缓存任务（固定间隔任务也用包装类，统一管理）
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
        // 空任务防护
        if (task == null) {
            log.warn("动态延迟任务启动失败：任务为null");
            return null;
        }
        // 非法参数防护
        if (minDelayMillis < 0 || maxDelayMillis < 0 || minDelayMillis > maxDelayMillis) {
            log.warn("动态延迟任务启动失败：参数非法（min={}, max={}），要求min≥0、max≥0、min≤max",
                    minDelayMillis, maxDelayMillis);
            return null;
        }

        // 生成唯一任务ID
        String taskId = UUID.randomUUID().toString();

        // 创建任务包装类
        DynamicTaskWrapper wrapper = new DynamicTaskWrapper(task, minDelayMillis, maxDelayMillis);
        taskMap.put(taskId, wrapper);

        // 定义递归执行的动态任务
        Runnable dynamicTask = new Runnable() {
            @Override
            public void run() {
                try {
                    // 1. 执行用户传入的任务（非重要任务，异常不影响整体）
                    task.run();

                    // 2. 检查任务是否已被停止（避免重复提交）
                    if (!taskMap.containsKey(taskId) || (wrapper.future != null && wrapper.future.isCancelled())) {
                        log.info("动态延迟任务已停止，不再提交（任务ID：{}）", taskId);
                        return;
                    }

                    // 3. 计算指数型动态延迟时间
                    long delay = calculateDynamicDelay(minDelayMillis, maxDelayMillis);

                    // 4. 延迟指定时间后，再次提交自身（形成循环）
                    // 更新任务Future（用于后续停止）
                    wrapper.future = scheduledExecutor.schedule(this, delay, TimeUnit.MILLISECONDS);

                } catch (Exception e) {
                    log.warn("动态延迟任务执行异常（非重要任务，忽略），任务ID：{}", taskId, e);
                    // 异常后仍尝试继续执行（非重要任务，不终止）
                    if (taskMap.containsKey(taskId) && (wrapper.future == null || !wrapper.future.isCancelled())) {
                        long delay = calculateDynamicDelay(minDelayMillis, maxDelayMillis);
                        wrapper.future = scheduledExecutor.schedule(this, delay, TimeUnit.MILLISECONDS);
                    }
                }
            }
        };

        // 立即提交第一次执行（初始延迟=最小延迟）
        wrapper.future = scheduledExecutor.schedule(dynamicTask, minDelayMillis, TimeUnit.MILLISECONDS);

        log.info("指数型动态延迟任务已启动（任务ID：{}），最小延迟{}毫秒，最大延迟{}毫秒",
                taskId, minDelayMillis, maxDelayMillis);
        return taskId;
    }

    /**
     * 停止指定ID的定时任务（支持固定间隔/动态延迟任务）
     * @param taskId 任务ID（启动任务时返回的字符串）
     * @return true=停止成功，false=任务不存在/已停止
     */
    public boolean stopTask(String taskId) {
        if (taskId == null || !taskMap.containsKey(taskId)) {
            log.warn("停止定时任务失败：任务ID不存在 - {}", taskId);
            return false;
        }

        DynamicTaskWrapper wrapper = taskMap.get(taskId);
        boolean isCancelled = (wrapper.future != null) && wrapper.future.cancel(false); // 允许当前任务执行完成
        if (isCancelled) {
            taskMap.remove(taskId);
        } else {
            log.warn("停止定时任务失败：任务已完成/已停止（任务ID：{}）", taskId);
        }
        return isCancelled;
    }

    /**
     * 停止所有定时任务
     */
    public void stopAllTasks() {
        if (taskMap.isEmpty()) {
            log.warn("无运行中的定时任务，无需停止");
            return;
        }

        taskMap.forEach((taskId, wrapper) -> {
            if (wrapper.future != null) {
                wrapper.future.cancel(false);
                log.info("停止定时任务（任务ID：{}）", taskId);
            }
        });
        log.info("所有定时任务已停止，共停止{}个任务", taskMap.size());
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
                log.warn("定时线程池强制关闭（等待超时）");
            }
        } catch (InterruptedException e) {
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
            log.error("定时线程池关闭被中断", e);
        }
        log.info("定时任务管理器已关闭");
    }

    /**
     * 获取当前运行中的任务数量
     */
    public int getRunningTaskCount() {
        return taskMap.size();
    }
}