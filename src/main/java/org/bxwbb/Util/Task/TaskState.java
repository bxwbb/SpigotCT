package org.bxwbb.Util.Task;

/**
 * 可控任务的状态枚举
 */
public enum TaskState {
    INITIALIZED,  // 初始化（未提交）
    RUNNING,      // 运行中
    PAUSED,       // 已暂停
    CANCELLED,    // 已取消
    COMPLETED     // 已完成
}