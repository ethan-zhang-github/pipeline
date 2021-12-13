package priv.ethanzhang.pipeline.core.event;

import lombok.Getter;
import priv.ethanzhang.pipeline.core.task.PipeTask;

import java.util.Objects;

/**
 * 任务生命周期事件
 * @author ethan zhang
 */
@Getter
public abstract class TaskLifecycleEvent extends TaskEvent {

    private final PipeTask<?, ?> task;

    public TaskLifecycleEvent(PipeTask<?, ?> task) {
        Objects.requireNonNull(task);
        this.task = task;
    }

}
