package priv.ethanzhang.migration.core.executor;

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import priv.ethanzhang.migration.core.annotation.MigrationConfigAttributes;
import priv.ethanzhang.migration.core.buffer.MigrationBuffer;
import priv.ethanzhang.migration.core.context.MigrationChunk;
import priv.ethanzhang.migration.core.context.MigrationContext;
import priv.ethanzhang.migration.core.event.MigrationTaskFailedEvent;
import priv.ethanzhang.migration.core.event.MigrationTaskFinishedEvent;
import priv.ethanzhang.migration.core.event.MigrationTaskWarnningEvent;
import priv.ethanzhang.migration.core.processor.MigrationProcessor;
import priv.ethanzhang.migration.core.reader.MigrationReader;
import priv.ethanzhang.migration.core.task.MigrationTask;
import priv.ethanzhang.migration.core.writer.MigrationWriter;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static priv.ethanzhang.migration.core.context.MigrationState.*;

/**
 * 本地任务执行器
 */
@Getter
public class LocalMigrationTaskExecutor<I, O> extends AbstractMigrationTaskExecutor<I, O> {

    private final ListeningExecutorService executor;

    private ListenableFuture<?> readerTask;

    private ListenableFuture<?> processorTask;

    private ListenableFuture<?> writerTask;

    public LocalMigrationTaskExecutor(ExecutorService executor) {
        this.executor = MoreExecutors.listeningDecorator(executor);
    }

    @Override
    protected void executeReader(MigrationTask<I, O> task) {
        MigrationContext<I, O> context = task.getContext();
        context.setReaderState(RUNNING);
        readerTask =  executor.submit(() -> {
            MigrationReader<I> reader = task.getReader();
            MigrationBuffer<I> readBuffer = context.getReadBuffer();
            MigrationConfigAttributes attributes = MigrationConfigAttributes.fromClass(reader.getClass());
            Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
                    .retryIfResult(Boolean.FALSE::equals)
                    .withStopStrategy(StopStrategies.stopAfterAttempt(attributes.getMaxProduceRetryTimes()))
                    .build();
            while (context.getReaderState() == RUNNING) {
                if (Thread.currentThread().isInterrupted()) {
                    context.setReaderState(TERMINATED);
                    return;
                }
                MigrationChunk<I> chunk;
                try {
                    chunk = reader.read(context);
                } catch (Exception e) {
                    Set<Class<? extends Throwable>> interruptFor = attributes.getInterruptFor();
                    if (interruptFor.stream().anyMatch(t -> t.isAssignableFrom(e.getClass()))) {
                        context.setReaderState(FAILED);
                        task.getDispatcher().dispatch(new MigrationTaskFailedEvent(task, MigrationTaskFailedEvent.Cause.READER_FAILED, e));
                        return;
                    } else {
                        task.getDispatcher().dispatch(new MigrationTaskWarnningEvent(task, MigrationTaskWarnningEvent.Cause.READER_FAILED, e));
                        continue;
                    }
                }
                if (chunk.isEmpty()) {
                    context.setReaderState(TERMINATED);
                    return;
                } else {
                    for (I i : chunk) {
                        try {
                            if (retryer.call(() -> readBuffer.tryProduce(i, attributes.getMaxProduceWaitSeconds(), TimeUnit.SECONDS))) {
                                context.incrReadCount(1);
                            }
                        } catch (ExecutionException | RetryException e) {
                            task.getDispatcher().dispatch(new MigrationTaskWarnningEvent(task, MigrationTaskWarnningEvent.Cause.READER_TO_BUFFER_FAILED, e));
                        }
                    }
                }
            }
        });
    }

    @Override
    protected void executeProcessor(MigrationTask<I, O> task) {
        MigrationContext<I, O> context = task.getContext();
        context.setProcessorState(RUNNING);
        processorTask = executor.submit(() -> {
            MigrationProcessor<I, O> processor = task.getProcessor();
            MigrationBuffer<I> readBuffer = context.getReadBuffer();
            MigrationBuffer<O> writeBuffer = context.getWriteBuffer();
            MigrationConfigAttributes attributes = MigrationConfigAttributes.fromClass(processor.getClass());
            Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
                    .retryIfResult(Boolean.FALSE::equals)
                    .withStopStrategy(StopStrategies.stopAfterAttempt(attributes.getMaxProduceRetryTimes()))
                    .build();
            while (context.getReaderState() == RUNNING || !readBuffer.isEmpty()) {
                if (Thread.currentThread().isInterrupted()) {
                    context.setProcessorState(TERMINATED);
                    return;
                }
                MigrationChunk<O> output;
                try {
                    List<I> input = readBuffer.consumeIfPossible(attributes.getMaxConsumeCount());
                    if (CollectionUtils.isEmpty(input)) {
                        Thread.yield();
                        continue;
                    } else {
                        output = processor.process(context, MigrationChunk.ofList(input));
                    }
                } catch (Exception e) {
                    Set<Class<? extends Throwable>> interruptFor = attributes.getInterruptFor();
                    if (interruptFor.stream().anyMatch(t -> t.isAssignableFrom(e.getClass()))) {
                        context.setProcessorState(FAILED);
                        task.getDispatcher().dispatch(new MigrationTaskFailedEvent(task, MigrationTaskFailedEvent.Cause.PROCESSOR_FAILED, e));
                        return;
                    } else {
                        task.getDispatcher().dispatch(new MigrationTaskWarnningEvent(task, MigrationTaskWarnningEvent.Cause.PROCESSOR_FAILED, e));
                        continue;
                    }
                }
                if (output.isNotEmpty()) {
                    for (O o : output) {
                        try {
                            if (retryer.call(() -> writeBuffer.tryProduce(o, attributes.getMaxProduceWaitSeconds(), TimeUnit.SECONDS))) {
                                context.incrProcessedCount(1);
                            }
                        } catch (ExecutionException | RetryException e) {
                            task.getDispatcher().dispatch(new MigrationTaskWarnningEvent(task, MigrationTaskWarnningEvent.Cause.PROCESSOR_TO_BUFFER_FAILED, e));
                        }
                    }
                }
            }
            context.setProcessorState(context.getReaderState());
        });
    }

    @Override
    protected void executeWriter(MigrationTask<I, O> task) {
        MigrationContext<I, O> context = task.getContext();
        context.setWriterState(RUNNING);
        writerTask = executor.submit(() -> {
            MigrationWriter<O> writer = task.getWriter();
            MigrationBuffer<O> writeBuffer = context.getWriteBuffer();
            MigrationConfigAttributes attributes = MigrationConfigAttributes.fromClass(writer.getClass());
            while (context.getProcessorState() == RUNNING || !writeBuffer.isEmpty()) {
                if (Thread.currentThread().isInterrupted()) {
                    context.setWriterState(TERMINATED);
                    return;
                }
                try {
                    List<O> output = writeBuffer.consumeIfPossible(writeBuffer.size());
                    if (CollectionUtils.isEmpty(output)) {
                        Thread.yield();
                    } else {
                        context.incrWrittenCount(writer.write(context, MigrationChunk.ofList(output)));
                    }
                } catch (Exception e) {
                    Set<Class<? extends Throwable>> interruptFor = attributes.getInterruptFor();
                    if (interruptFor.stream().anyMatch(t -> t.isAssignableFrom(e.getClass()))) {
                        context.setWriterState(FAILED);
                        task.getDispatcher().dispatch(new MigrationTaskFailedEvent(task, MigrationTaskFailedEvent.Cause.WRITER_FAILED, e));
                        return;
                    } else {
                        task.getDispatcher().dispatch(new MigrationTaskWarnningEvent(task, MigrationTaskWarnningEvent.Cause.WRITER_FAILED, e));
                    }
                }
            }
            context.setWriterState(context.getProcessorState());
            if (context.getReaderState() == TERMINATED && context.getProcessorState() == TERMINATED && context.getWriterState() == TERMINATED) {
                task.getDispatcher().dispatch(new MigrationTaskFinishedEvent(task));
            }
        });
    }

    @Override
    protected void stopReader(MigrationTask<I, O> task) {
        MigrationContext<I, O> context = task.getContext();
        if (readerTask != null && context.getReaderState() == RUNNING) {
            context.setReaderState(STOPPING);
        }
    }

    @Override
    protected void stopProcessor(MigrationTask<I, O> task) {
        MigrationContext<I, O> context = task.getContext();
        if (processorTask != null && context.getProcessorState() == RUNNING) {
            context.setProcessorState(STOPPING);
        }
    }

    @Override
    protected void stopWriter(MigrationTask<I, O> task) {
        MigrationContext<I, O> context = task.getContext();
        if (writerTask != null && context.getWriterState() == RUNNING) {
            context.setWriterState(STOPPING);
        }
    }

    @Override
    protected void shutdownReader(MigrationTask<I, O> task) {
        MigrationContext<I, O> context = task.getContext();
        if (readerTask != null) {
            context.setReaderState(TERMINATED);
            readerTask.cancel(true);
        }
    }

    @Override
    protected void shutdownProcessor(MigrationTask<I, O> task) {
        MigrationContext<I, O> context = task.getContext();
        if (processorTask != null) {
            context.setProcessorState(TERMINATED);
            processorTask.cancel(true);
        }
    }

    @Override
    protected void shutdownWriter(MigrationTask<I, O> task) {
        MigrationContext<I, O> context = task.getContext();
        if (writerTask != null) {
            context.setWriterState(TERMINATED);
            writerTask.cancel(true);
        }
    }

}
