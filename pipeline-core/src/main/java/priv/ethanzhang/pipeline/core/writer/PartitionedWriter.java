package priv.ethanzhang.pipeline.core.writer;

import priv.ethanzhang.pipeline.core.context.DataChunk;
import priv.ethanzhang.pipeline.core.context.TaskContext;

public abstract class PartitionedWriter<O> implements PipeWriter<O> {

    private final int size;

    public PartitionedWriter(int size) {
        this.size = size;
    }

    @Override
    public int write(TaskContext<?, O> context, DataChunk<O> output) {
        return output.partition(size).stream().mapToInt(i -> writeInternal(context, i)).sum();
    }

    protected abstract int writeInternal(TaskContext<?, O> context, DataChunk<O> output);

}
