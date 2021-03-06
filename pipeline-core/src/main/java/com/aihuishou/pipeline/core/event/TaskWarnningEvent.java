package com.aihuishou.pipeline.core.event;

import lombok.Getter;
import com.aihuishou.pipeline.core.task.PipeTask;

/**
 * 任务警告事件
 * @author ethan zhang
 */
@Getter
public class TaskWarnningEvent extends TaskLifecycleEvent {

    private final Cause cause;

    private final Throwable throwable;

    public TaskWarnningEvent(PipeTask<?, ?> task, Cause cause, Throwable throwable) {
        super(task);
        this.cause = cause;
        this.throwable = throwable;
    }

    public enum Cause {

        READER_FAILED,
        READER_TO_BUFFER_FAILED,
        PROCESSOR_FAILED,
        PROCESSOR_TO_BUFFER_FAILED,
        WRITER_FAILED

    }

    @Override
    public String toString() {
        return String.format("TaskFailedEvent occured, taskId: %s, timestamp: %s, cause: %s, throwable: %s",
                task.getTaskId(), timestamp, cause, throwable.getMessage());
    }

}
