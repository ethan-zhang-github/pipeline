package com.aihuishou.pipeline.core.utils;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * 批处理工具类
 * @author ethan zhang
 */
public class BatchUtil {

    /**
     * 并行处理集合中的元素
     */
    public static <T> void process(List<T> source, Consumer<T> consumer, Executor executor) {
        processAsync(source, consumer, executor).join();
    }

    /**
     * 并行处理集合中的元素，返回异步 future
     */
    public static <T> CompletableFuture<Void> processAsync(List<T> source, Consumer<T> consumer, Executor executor) {
        return CompletableFuture.allOf(source.stream().map(t -> CompletableFuture.runAsync(() -> consumer.accept(t), executor)).toArray(CompletableFuture[]::new));
    }

    /**
     * 并行处理集合中的元素，聚合结果后返回
     */
    public static <T, R> List<R> process(List<T> source, Function<T, R> function, Executor executor) {
        return processAsync(source, function, executor).join();
    }

    /**
     * 并行处理集合中的元素，聚合结果后返回异步 future
     */
    public static <T, R> CompletableFuture<List<R>> processAsync(List<T> source, Function<T, R> function, Executor executor) {
        List<CompletableFuture<R>> futures = source.stream().map(t -> CompletableFuture.supplyAsync(() -> function.apply(t), executor)).collect(Collectors.toList());
        return merge(futures);
    }

    /**
     * 分割集合，并行处理
     */
    public static <T> void partitionAndProcess(List<T> source, int chunkSize, Consumer<List<T>> consumer, Executor executor) {
        partitionAndProcessAsync(source, chunkSize, consumer, executor).join();
    }

    /**
     * 分割集合，并行处理，返回异步 future
     */
    public static <T> CompletableFuture<Void> partitionAndProcessAsync(List<T> source, int chunkSize, Consumer<List<T>> consumer, Executor executor) {
        return CompletableFuture.allOf(Lists.partition(source, chunkSize).stream().map(chunk -> CompletableFuture.runAsync(() -> consumer.accept(chunk), executor)).toArray(CompletableFuture[]::new));
    }

    /**
     * 分割集合，并行处理，聚合结果后返回
     */
    public static <T, R> List<R> partitionAndProcess(List<T> source, int chunkSize, Function<List<T>, List<R>> function, Executor executor) {
        return partitionAndProcessAsync(source, chunkSize, function, executor).join();
    }

    /**
     * 分割集合，并行处理，聚合结果后返回异步 future
     */
    public static <T, R> CompletableFuture<List<R>> partitionAndProcessAsync(List<T> source, int chunkSize, Function<List<T>, List<R>> function, Executor executor) {
        List<CompletableFuture<List<R>>> futures = Lists.partition(source, chunkSize).stream().map(chunk -> CompletableFuture.supplyAsync(() -> function.apply(chunk), executor)).collect(Collectors.toList());
        return mergeList(futures);
    }

    public static <T> CompletableFuture<List<T>> mergeList(Collection<CompletableFuture<List<T>>> source) {
        return merge(source, ArrayList::new, List::addAll, List::addAll);
    }

    public static <T> CompletableFuture<List<T>> merge(Collection<CompletableFuture<T>> source) {
        return merge(source, Collectors.toList());
    }

    public static <T, A, R> CompletableFuture<R> merge(Collection<CompletableFuture<T>> source, Collector<? super T, A, R> collector) {
        return CompletableFuture.allOf(source.toArray(new CompletableFuture[0])).thenApply(v -> source.stream().map(CompletableFuture::join).collect(collector));
    }

    public static <T, A, R> CompletableFuture<R> merge(Collection<CompletableFuture<T>> source,
                                                       Supplier<R> supplier,
                                                       BiConsumer<R, ? super T> accumulator,
                                                       BiConsumer<R, R> combiner) {
        return CompletableFuture.allOf(source.toArray(new CompletableFuture[0])).thenApply(v -> source.stream().map(CompletableFuture::join).collect(supplier, accumulator, combiner));
    }

    public static <T> CompletableFuture<T> merge(Collection<CompletableFuture<T>> source, BinaryOperator<T> accumulator) {
        return CompletableFuture.allOf(source.toArray(new CompletableFuture[0])).thenApply(v -> source.stream().map(CompletableFuture::join).filter(Objects::nonNull).reduce(accumulator).orElse(null));
    }

}
