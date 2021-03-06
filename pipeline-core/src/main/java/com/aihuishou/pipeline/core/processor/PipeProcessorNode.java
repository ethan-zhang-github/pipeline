package com.aihuishou.pipeline.core.processor;

import com.aihuishou.pipeline.core.buffer.DataBuffer;
import com.aihuishou.pipeline.core.context.TaskStateHolder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * 数据处理链节点
 * @param <I> 输入类型
 * @param <O> 输出类型
 * @author ethan zhang
 */
@Getter
@Setter(AccessLevel.PRIVATE)
public class PipeProcessorNode<I, O> {

    /**
     * 当前节点的 processor
     */
    private PipeProcessor<? super I, ? extends O> processor;

    /**
     * 当前节点状态
     */
    private TaskStateHolder state;

    /**
     * 写出缓冲区
     */
    private DataBuffer<O> buffer;

    private PipeProcessorNode() {}

    public static <I, O> Builder<I, O> builder() {
        return new Builder<>();
    }

    public static class Builder<I, O> {

        private final PipeProcessorNode<I, O> node = new PipeProcessorNode<>();

        public Builder<I, O> processor(PipeProcessor<? super I, ? extends O> processor) {
            node.setProcessor(processor);
            return Builder.this;
        }

        public Builder<I, O> state(TaskStateHolder state) {
            node.setState(state);
            return Builder.this;
        }

        public Builder<I, O> buffer(DataBuffer<O> buffer) {
            node.setBuffer(buffer);
            return Builder.this;
        }

        public PipeProcessorNode<I, O> build() {
            return node;
        }

    }

}
