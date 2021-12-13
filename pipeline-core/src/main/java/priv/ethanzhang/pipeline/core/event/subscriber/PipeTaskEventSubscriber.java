package priv.ethanzhang.pipeline.core.event.subscriber;

import priv.ethanzhang.pipeline.core.event.TaskEvent;
import priv.ethanzhang.pipeline.core.event.TaskLifecycleEvent;
import priv.ethanzhang.pipeline.core.task.PipeTask;
import priv.ethanzhang.pipeline.core.utils.GenericUtil;

/**
 * 流水线任务生命周期事件订阅者
 * @param <E> 事件类型
 */
public abstract class PipeTaskEventSubscriber<E extends TaskLifecycleEvent> implements TaskEventSubscriber {

    private final PipeTask<?, ?> task;

    private final Class<E> eventType;

    public PipeTaskEventSubscriber(PipeTask<?, ?> task) {
        this.task = task;
        this.eventType = GenericUtil.getSuperclassGenericType(this.getClass(), 0);
    }

    public PipeTaskEventSubscriber(PipeTask<?, ?> task, Class<E> eventType) {
        this.task = task;
        this.eventType = eventType;
    }

    @Override
    public boolean supports(TaskEvent event) {
        return eventType.isAssignableFrom(event.getClass()) && task.getTaskId().equals(((TaskLifecycleEvent) event).getTask().getTaskId());
    }

    @SuppressWarnings("unchecked")
    @Override
    public void subscribe(TaskEvent event) {
        subscribeInternal((E) event);
    }

    protected abstract void subscribeInternal(E event);

}
