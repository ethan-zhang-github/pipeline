package com.aihuishou.pipeline.core.config;

import com.aihuishou.pipeline.core.reporter.LoggerTaskReporter;
import com.aihuishou.pipeline.core.reporter.TaskReporter;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 全局配置
 * @author ethan zhang
 */
public interface GlobalConfig {

    LocalRegistry LOCAL_REGISTRY = new LocalRegistry();

    LocalDispatcher LOCAL_DISPATCHER = new LocalDispatcher();

    Reader READER = new Reader();

    Processor PROCESSOR = new Processor();

    Writer WRITER = new Writer();

    Buffer BUFFER = new Buffer();

    Reporter REPORTER = new Reporter();

    @Getter
    @Setter
    class Reader {

        private Reader() {}

        private int produceRetryTimes = 10;

        private int produceWaitSeconds = 5;

    }

    @Getter
    @Setter
    class Processor {

        private Processor() {}

        private int produceRetryTimes = 10;

        private int produceWaitSeconds = 5;

        private int maxConsumeCount = 100;

    }

    @Getter
    @Setter
    class Writer {

        private Writer() {}

        private int maxConsumeCount = 100;

    }

    @Getter
    @Setter
    class Buffer {

        private int bufferSize = 1 << 10;

    }

    @Getter
    @Setter
    class Reporter {

        private Duration reportPeriod = Duration.ofMinutes(1);

        private Supplier<TaskReporter> defaultReporter = () -> LoggerTaskReporter.INSTANCE;

    }

    @Getter
    @Setter
    class LocalRegistry {

        private LocalRegistry() {}

        /**
         * 任务注册表初始大小
         */
        private int initialCapacity = 16;

        /**
         * 任务注册表最大容量（超过则触发任务淘汰）
         */
        private int maximumSize = Integer.MAX_VALUE;

        /**
         * 任务最大过期时间
         */
        private Duration timeout = Duration.ofDays(30);

    }

    @Getter
    @Setter
    class LocalDispatcher {

        private int bufferSize = 1 << 10;

    }

}
