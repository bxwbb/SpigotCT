package org.bxwbb.Util.Task;

/**
 * 可控制任务抽象父类（定义取消、暂停、恢复核心行为）
 * @param <V> 任务返回值类型
 */
public abstract class ControllableTask<V> {
    // 任务执行结果
    private V result;
    // 任务执行异常
    private Exception exception;

    /**
     * 执行任务
     */
    public abstract V execute() throws Exception;

    /**
     * 取消任务
     */
    public abstract void cancel();

    /**
     * 暂停任务
     */
    public abstract void pause();

    /**
     * 恢复任务
     */
    public abstract void resume();

    // ===================== 结果/异常管理 =====================
    protected void setResult(V result) {
        this.result = result;
    }

    protected void setException(Exception exception) {
        this.exception = exception;
    }

    public V getResult() {
        return result;
    }

    public Exception getException() {
        return exception;
    }
}