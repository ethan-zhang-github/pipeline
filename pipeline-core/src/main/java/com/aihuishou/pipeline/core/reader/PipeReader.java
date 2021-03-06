package com.aihuishou.pipeline.core.reader;

import com.aihuishou.pipeline.core.context.DataChunk;
import com.aihuishou.pipeline.core.context.TaskContext;

/**
 * 数据读取
 * @param <I> 读取类型
 * @author ethan zhang
 */
@FunctionalInterface
public interface PipeReader<I> {

    DataChunk<I> read(TaskContext<I, ?> context);

    default void initialize(TaskContext<I, ?> context) {}

    default void destroy(TaskContext<I, ?> context) {}

}
