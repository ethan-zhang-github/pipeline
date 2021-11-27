package priv.ethanzhang.task;

import priv.ethanzhang.context.MigrationParameter;
import priv.ethanzhang.processor.MigrationProcessor;
import priv.ethanzhang.reader.MigrationReader;
import priv.ethanzhang.writer.MigrationWriter;

public abstract class AbstractMigrationTaskBuilder<I, O> {

    protected String taskId;

    protected MigrationReader<I> reader;

    protected MigrationProcessor<I, O> processor;

    protected MigrationWriter<O> writer;

    protected MigrationParameter parameter;

    public AbstractMigrationTaskBuilder<I, O> taskId(String taskId) {
        this.taskId = taskId;
        return this;
    }

    public AbstractMigrationTaskBuilder<I, O> reader(MigrationReader<I> reader) {
        this.reader = reader;
        return this;
    }

    public AbstractMigrationTaskBuilder<I, O> processor(MigrationProcessor<I, O> processor) {
        this.processor = processor;
        return this;
    }

    public AbstractMigrationTaskBuilder<I, O> writer(MigrationWriter<O> writer) {
        this.writer = writer;
        return this;
    }

    public AbstractMigrationTaskBuilder<I, O> parameter(MigrationParameter parameter) {
        this.parameter = parameter;
        return this;
    }

    public MigrationTask<I, O> build() {
        MigrationTask<I, O> task = new MigrationTask<>();
        task.setTaskId(taskId);
        task.setReader(reader);
        task.setProcessor(processor);
        task.setWriter(writer);
        customBuild(task);
        return task;
    }

    protected abstract void customBuild(MigrationTask<I, O> task);
    
}
