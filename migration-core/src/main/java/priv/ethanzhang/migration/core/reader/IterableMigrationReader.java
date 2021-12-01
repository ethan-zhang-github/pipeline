package priv.ethanzhang.migration.core.reader;

import priv.ethanzhang.migration.core.context.MigrationChunk;
import priv.ethanzhang.migration.core.context.MigrationContext;

/**
 * 迭代读取
 * @param <I> 读取类型
 */
public abstract class IterableMigrationReader<I> implements MigrationReader<I> {

    @Override
    public MigrationChunk<I> read(MigrationContext<I, ?> context) {
        if (!hasMore(context)) {
            return MigrationChunk.empty();
        }
        return readMore(context);
    }

    protected abstract boolean hasMore(MigrationContext<I, ?> context);

    protected abstract MigrationChunk<I> readMore(MigrationContext<I, ?> context);

}