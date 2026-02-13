package org.bxwbb.Util.Task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 可控线程池：封装ExecutorService，管理所有可控任务，支持批量控制
 * 重写优化点：
 * 1. 补充TaskState枚举定义，解决原代码依赖缺失问题
 * 2. 优化线程池创建逻辑，支持自定义线程工厂
 * 3. 完善任务状态管理，适配ControllableThreadTask的状态查询
 * 4. 增强异常处理和日志输出
 * 5. 优化资源释放逻辑，避免内存泄漏
 * 6. 补充空值校验和参数合法性检查
 */
public class ControllableThreadPool {
    // 日志实例
    private static final Logger log = LoggerFactory.getLogger(ControllableThreadPool.class);

    // 核心线程池（可自定义配置）
    private final ExecutorService executor;
    // 已提交的任务集合（线程安全）
    private final Map<String, ControllableTask<?>> taskMap = new ConcurrentHashMap<>();
    // 任务ID生成器
    private final AtomicInteger taskIdGenerator = new AtomicInteger(0);

    // ========== 任务状态枚举（补充原代码缺失的依赖） ==========
    public enum TaskState {
        RUNNING,    // 运行中
        PAUSED,     // 已暂停
        CANCELLED,  // 已取消
        COMPLETED,  // 完成（成功）
        FAILED      // 失败（异常）
    }

    // ========== 构造方法 ==========
    /**
     * 默认构造：核心线程数=CPU核心数，最大线程数=CPU核心数*2，队列容量=100
     */
    public ControllableThreadPool() {
        int cpuCore = Runtime.getRuntime().availableProcessors();
        this.executor = new ThreadPoolExecutor(
                cpuCore,
                cpuCore * 2,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new NamedThreadFactory("controllable-pool-"),
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：调用者执行
        );
        log.info("默认可控线程池初始化完成，核心线程数：{}，最大线程数：{}", cpuCore, cpuCore * 2);
    }

    /**
     * 自定义线程池配置
     * @param corePoolSize 核心线程数
     * @param maximumPoolSize 最大线程数
     * @param keepAliveTime 空闲线程存活时间
     * @param unit 时间单位
     * @param workQueue 任务队列
     */
    public ControllableThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                  TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        // 参数合法性校验
        if (corePoolSize < 0) throw new IllegalArgumentException("核心线程数不能为负数");
        if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize)
            throw new IllegalArgumentException("最大线程数必须大于0且不小于核心线程数");
        if (keepAliveTime < 0) throw new IllegalArgumentException("空闲时间不能为负数");
        if (workQueue == null) throw new NullPointerException("任务队列不能为null");

        this.executor = new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                workQueue,
                new NamedThreadFactory("controllable-pool-"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        log.info("自定义可控线程池初始化完成，核心线程数：{}，最大线程数：{}，空闲存活时间：{} {}",
                corePoolSize, maximumPoolSize, keepAliveTime, unit);
    }

    /**
     * 全自定义构造（支持自定义线程工厂和拒绝策略）
     * @param corePoolSize 核心线程数
     * @param maximumPoolSize 最大线程数
     * @param keepAliveTime 空闲线程存活时间
     * @param unit 时间单位
     * @param workQueue 任务队列
     * @param threadFactory 线程工厂
     * @param handler 拒绝策略
     */
    public ControllableThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                  TimeUnit unit, BlockingQueue<Runnable> workQueue,
                                  ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        // 参数合法性校验
        if (corePoolSize < 0) throw new IllegalArgumentException("核心线程数不能为负数");
        if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize)
            throw new IllegalArgumentException("最大线程数必须大于0且不小于核心线程数");
        if (keepAliveTime < 0) throw new IllegalArgumentException("空闲时间不能为负数");
        if (workQueue == null) throw new NullPointerException("任务队列不能为null");
        if (threadFactory == null) throw new NullPointerException("线程工厂不能为null");
        if (handler == null) throw new NullPointerException("拒绝策略不能为null");

        this.executor = new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                workQueue,
                threadFactory,
                handler
        );
        log.info("全自定义可控线程池初始化完成，核心线程数：{}，最大线程数：{}",
                corePoolSize, maximumPoolSize);
    }

    // ========== 任务提交 ==========
    /**
     * 提交可控任务，自动生成任务ID
     * @param task 可控任务
     * @return 任务ID（用于后续控制）
     * @throws NullPointerException 任务为null时抛出
     */
    public <T> String submit(ControllableTask<T> task) {
        if (task == null) throw new NullPointerException("任务不能为null");
        String taskId = "task-" + taskIdGenerator.incrementAndGet();
        return submit(taskId, task);
    }

    /**
     * 提交可控任务，指定任务ID
     * @param taskId 自定义任务ID
     * @param task 可控任务
     * @return 任务ID
     * @throws IllegalArgumentException 任务ID已存在时抛出
     * @throws NullPointerException 任务或任务ID为null时抛出
     */
    public <T> String submit(String taskId, ControllableTask<T> task) {
        if (taskId == null || taskId.trim().isEmpty())
            throw new NullPointerException("任务ID不能为null或空");
        if (task == null) throw new NullPointerException("任务不能为null");
        if (taskMap.containsKey(taskId)) {
            throw new IllegalArgumentException("任务ID已存在：" + taskId);
        }

        // 提交任务到线程池
        executor.submit(() -> {
            try {
                task.execute();
            } catch (Exception e) {
                log.error("任务执行异常，任务ID：{}", taskId, e);
            }
        });

        taskMap.put(taskId, task);
        return taskId;
    }

    // ========== 任务控制 ==========
    /**
     * 暂停指定任务
     * @param taskId 任务ID
     * @return 是否暂停成功（任务不存在/已暂停/已取消时返回false）
     */
    public boolean pauseTask(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) return false;

        ControllableTask<?> task = taskMap.get(taskId);
        if (task == null) {
            log.warn("暂停任务失败，任务不存在，任务ID：{}", taskId);
            return false;
        }

        try {
            task.pause();
            // 校验暂停状态（适配ControllableThreadTask的状态查询）
            boolean paused = task instanceof ControllableThreadTask &&
                    ((ControllableThreadTask<?>) task).isTaskPaused();
            if (paused) {
                log.info("任务暂停成功，任务ID：{}", taskId);
            } else {
                log.warn("任务暂停失败，任务可能已取消或未运行，任务ID：{}", taskId);
            }
            return paused;
        } catch (Exception e) {
            log.error("暂停任务异常，任务ID：{}", taskId, e);
            return false;
        }
    }

    /**
     * 恢复指定任务
     * @param taskId 任务ID
     * @return 是否恢复成功（任务不存在/未暂停/已取消时返回false）
     */
    public boolean resumeTask(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) return false;

        ControllableTask<?> task = taskMap.get(taskId);
        if (task == null) {
            log.warn("恢复任务失败，任务不存在，任务ID：{}", taskId);
            return false;
        }

        try {
            task.resume();
            // 校验恢复状态
            boolean resumed = task instanceof ControllableThreadTask &&
                    !((ControllableThreadTask<?>) task).isTaskPaused();
            if (resumed) {
                log.info("任务恢复成功，任务ID：{}", taskId);
            } else {
                log.warn("任务恢复失败，任务可能已取消或未暂停，任务ID：{}", taskId);
            }
            return resumed;
        } catch (Exception e) {
            log.error("恢复任务异常，任务ID：{}", taskId, e);
            return false;
        }
    }

    /**
     * 取消指定任务
     * @param taskId 任务ID
     * @return 是否取消成功（任务不存在时返回false）
     */
    public boolean cancelTask(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) return false;

        ControllableTask<?> task = taskMap.get(taskId);
        if (task == null) {
            log.warn("取消任务失败，任务不存在，任务ID：{}", taskId);
            return false;
        }

        try {
            task.cancel();
            // 校验取消状态
            boolean cancelled = task instanceof ControllableThreadTask &&
                    ((ControllableThreadTask<?>) task).isTaskCancelled();
            if (cancelled) {
                taskMap.remove(taskId); // 取消后移除任务（避免内存泄漏）
            } else {
                log.warn("任务取消失败，任务可能已完成，任务ID：{}", taskId);
            }
            return cancelled;
        } catch (Exception e) {
            log.error("取消任务异常，任务ID：{}", taskId, e);
            return false;
        }
    }

    /**
     * 批量暂停所有运行中的任务
     * @return 暂停成功的任务ID列表
     */
    public List<String> pauseAllTasks() {
        List<String> pausedIds = taskMap.keySet().stream()
                .filter(this::pauseTask)
                .collect(Collectors.toList());
        log.info("批量暂停任务完成，成功暂停{}个任务，任务ID列表：{}", pausedIds.size(), pausedIds);
        return pausedIds;
    }

    /**
     * 批量恢复所有暂停的任务
     * @return 恢复成功的任务ID列表
     */
    public List<String> resumeAllTasks() {
        List<String> resumedIds = taskMap.keySet().stream()
                .filter(this::resumeTask)
                .collect(Collectors.toList());
        log.info("批量恢复任务完成，成功恢复{}个任务，任务ID列表：{}", resumedIds.size(), resumedIds);
        return resumedIds;
    }

    /**
     * 批量取消所有任务
     * @return 取消成功的任务ID列表
     */
    public List<String> cancelAllTasks() {
        List<String> cancelledIds = taskMap.keySet().stream()
                .filter(this::cancelTask)
                .collect(Collectors.toList());
        // 清空已取消的任务（双重保障）
        cancelledIds.forEach(taskMap::remove);
        log.info("批量取消任务完成，成功取消{}个任务，任务ID列表：{}", cancelledIds.size(), cancelledIds);
        return cancelledIds;
    }

    // ========== 任务查询 ==========
    /**
     * 获取指定任务的状态
     * @param taskId 任务ID
     * @return 任务状态（null=任务不存在）
     */
    public TaskState getTaskState(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) return null;

        ControllableTask<?> task = taskMap.get(taskId);
        if (task == null) return null;

        // 适配ControllableThreadTask的状态查询
        if (task instanceof ControllableThreadTask<?> threadTask) {
            if (threadTask.isTaskCancelled()) {
                return TaskState.CANCELLED;
            } else if (threadTask.isTaskPaused()) {
                return TaskState.PAUSED;
            } else if (threadTask.isTaskRunning()) {
                return TaskState.RUNNING;
            } else {
                // 任务已完成：判断是成功还是失败
                return threadTask.getException() == null ? TaskState.COMPLETED : TaskState.FAILED;
            }
        }

        // 通用任务状态（默认返回运行中，子类可扩展）
        return TaskState.RUNNING;
    }

    /**
     * 获取指定任务的结果
     * @param taskId 任务ID
     * @return 任务结果（null=任务未完成/失败/取消/不存在）
     */
    @SuppressWarnings("unchecked")
    public <T> T getTaskResult(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) return null;

        ControllableTask<T> task = (ControllableTask<T>) taskMap.get(taskId);
        if (task == null) {
            log.warn("获取任务结果失败，任务不存在，任务ID：{}", taskId);
            return null;
        }

        // 任务已取消/失败时返回null
        TaskState state = getTaskState(taskId);
        if (state == TaskState.CANCELLED || state == TaskState.FAILED) {
            log.warn("任务未完成，无法获取结果，任务ID：{}，状态：{}", taskId, state);
            return null;
        }

        return task.getResult();
    }

    /**
     * 获取所有任务的ID和状态
     * @return 任务ID-状态映射（线程安全）
     */
    public Map<String, TaskState> getAllTaskStates() {
        return taskMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> getTaskState(e.getKey()),
                        (oldVal, newVal) -> newVal, // 解决key冲突（理论上不会发生）
                        LinkedHashMap::new           // 保持插入顺序
                ));
    }

    /**
     * 获取线程池当前状态信息
     * @return 线程池状态描述
     */
    public String getThreadPoolStatus() {
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) executor;
        return String.format(
                "线程池状态：活跃线程数=%d，核心线程数=%d，最大线程数=%d，任务队列大小=%d，已完成任务数=%d，总任务数=%d",
                tpe.getActiveCount(),
                tpe.getCorePoolSize(),
                tpe.getMaximumPoolSize(),
                tpe.getQueue().size(),
                tpe.getCompletedTaskCount(),
                tpe.getTaskCount()
        );
    }

    // ========== 线程池管理 ==========
    /**
     * 优雅关闭线程池（等待已提交任务完成）
     * @return 线程池是否成功关闭
     */
    public boolean shutdown() {
        log.info("开始优雅关闭可控线程池，当前{}", getThreadPoolStatus());
        // 先取消所有任务
        cancelAllTasks();
        // 关闭线程池
        executor.shutdown();
        try {
            // 等待线程池关闭（最多等待1分钟）
            boolean terminated = executor.awaitTermination(1, TimeUnit.MINUTES);
            if (terminated) {
                log.info("线程池优雅关闭成功");
            } else {
                log.warn("线程池优雅关闭超时，强制关闭中...");
                executor.shutdownNow();
            }
            // 清空任务映射
            taskMap.clear();
            return terminated;
        } catch (InterruptedException e) {
            log.error("线程池关闭过程被中断", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 强制关闭线程池（立即中断所有任务）
     * @param timeout 等待时间
     * @param unit 时间单位
     * @return 是否在超时前关闭成功
     * @throws InterruptedException 中断异常
     */
    public boolean shutdownNow(long timeout, TimeUnit unit) throws InterruptedException {
        if (timeout < 0) throw new IllegalArgumentException("超时时间不能为负数");
        if (unit == null) throw new NullPointerException("时间单位不能为null");

        log.info("开始强制关闭可控线程池，超时时间：{} {}", timeout, unit);
        // 先取消所有任务
        cancelAllTasks();
        // 强制关闭线程池
        List<Runnable> remainingTasks = executor.shutdownNow();
        log.warn("强制关闭线程池，中断{}个未执行的任务", remainingTasks.size());
        // 等待线程池终止
        boolean terminated = executor.awaitTermination(timeout, unit);
        // 清空任务映射
        taskMap.clear();
        if (terminated) {
            log.info("线程池强制关闭成功");
        } else {
            log.error("线程池强制关闭超时");
        }
        return terminated;
    }

    // ========== 自定义线程工厂（命名线程，方便调试） ==========
    private static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger threadNum = new AtomicInteger(0);

        public NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, prefix + threadNum.incrementAndGet());
            thread.setDaemon(false); // 非守护线程，保证任务执行
            thread.setPriority(Thread.NORM_PRIORITY);
            // 设置未捕获异常处理器
            thread.setUncaughtExceptionHandler((t, e) ->
                    log.error("线程执行未捕获异常，线程名：{}", t.getName(), e));
            return thread;
        }
    }
}