package com.aihuishou.pipeline.core.reader;

import com.aihuishou.pipeline.core.context.DataChunk;
import com.aihuishou.pipeline.core.context.TaskContext;
import com.aihuishou.pipeline.core.event.TaskWarnningEvent;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.github.rholder.retry.*;
import org.apache.commons.collections4.CollectionUtils;
import com.aihuishou.pipeline.core.exception.TaskBuildException;

import java.io.File;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * excel 读取
 * @param <I> 读取类型
 */
public class EasyExcelReader<I> extends OnceInitializedReader<I> {

    private final File file;

    private final Class<I> clazz;

    private Set<Integer> sheetNos;

    private final AtomicInteger unreadSheetCount = new AtomicInteger(Integer.MAX_VALUE);

    private final BlockingQueue<I> buffer = new LinkedBlockingQueue<>(1 << 10);

    private final AtomicBoolean finished = new AtomicBoolean();

    public EasyExcelReader(File file, Class<I> clazz, int... sheetNos) {
        if (!file.exists()) {
            throw new TaskBuildException(String.format("file [%s] not exists!", file.getAbsolutePath()));
        }
        this.file = file;
        this.clazz = clazz;
        if (sheetNos != null) {
            this.sheetNos = Arrays.stream(sheetNos).boxed().collect(Collectors.toSet());
        }
    }

    @Override
    public DataChunk<I> read(TaskContext<I, ?> context) {
        try {
            while (!finished.get() || !buffer.isEmpty()) {
                I head = buffer.poll(5, TimeUnit.SECONDS);
                if (Objects.nonNull(head)) {
                    List<I> list = new ArrayList<>(buffer.size() + 1);
                    list.add(head);
                    buffer.drainTo(list);
                    return DataChunk.of(list);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return DataChunk.empty();
    }

    @Override
    protected void initializeInternal(TaskContext<I, ?> context) {
        ExcelReader excelReader = EasyExcel.read(file, clazz, new EasyExcelItemReaderListener(context)).build();
        List<ReadSheet> needReadSheet;
        if (CollectionUtils.isEmpty(sheetNos)) {
            needReadSheet = excelReader.excelExecutor().sheetList();
        } else {
            needReadSheet = excelReader.excelExecutor().sheetList().stream().filter(s -> sheetNos.contains(s.getSheetNo())).collect(Collectors.toList());
        }
        unreadSheetCount.set(needReadSheet.size());
        new Thread(() -> {
            try {
                excelReader.read(needReadSheet);
            } finally {
                excelReader.finish();
            }
        }).start();
    }

    private class EasyExcelItemReaderListener extends AnalysisEventListener<I> {

        private final TaskContext<I, ?> taskContext;

        private EasyExcelItemReaderListener(TaskContext<I, ?> context) {
            this.taskContext = context;
        }

        private final Retryer<Object> retryer = RetryerBuilder.newBuilder()
                .retryIfResult(Boolean.FALSE::equals)
                .withStopStrategy(StopStrategies.stopAfterAttempt(10))
                .withWaitStrategy(WaitStrategies.fixedWait(1, TimeUnit.SECONDS))
                .build();

        @Override
        public void invoke(I data, AnalysisContext context) {
            try {
                retryer.call(() -> buffer.offer(data, 5, TimeUnit.SECONDS));
            } catch (ExecutionException | RetryException e) {
                taskContext.getTask().getDispatcher().dispatch(new TaskWarnningEvent(taskContext.getTask(), TaskWarnningEvent.Cause.READER_TO_BUFFER_FAILED, e));
            }
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            if (unreadSheetCount.decrementAndGet() == 0) {
                finished.set(true);
            }
        }

        @Override
        public void onException(Exception exception, AnalysisContext context) throws Exception {
            finished.set(true);
            super.onException(exception, context);
        }

    }

}
