package priv.ethanzhang.migration.core.manager;

import lombok.extern.slf4j.Slf4j;
import priv.ethanzhang.migration.core.event.*;
import priv.ethanzhang.migration.core.task.MigrationTask;

import java.util.Map;

/**
 * 本地任务管理器
 */
@Slf4j
public class LocalMigrationTaskManager implements MigrationTaskManager {

    public static final LocalMigrationTaskManager INSTANCE = new LocalMigrationTaskManager();

    private MigrationTaskRegistry registry;

    private LocalReporterScheduler reporterScheduler;

    private LocalMigrationTaskManager() {
        initialize();
    }

    @Override
    public void initialize() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutDown));
        registry = new InMemoryMigrationTaskRegistry();
        reporterScheduler = new LocalReporterScheduler(registry);
        reporterScheduler.startAsync();
        LocalMigrationEventDispatcher.INSTANCE.addSubsriber(new LocalMigrationTaskManagerSubscriber());
    }

    @Override
    public void shutDown() {
        Map<String, MigrationTask<?, ?>> migrationTaskMap = registry.getAll();
        migrationTaskMap.forEach(((taskId, task) -> {
            task.shutDown();
            task.getDispatcher().clearEventStream(taskId);
            log.warn("Task [{}] has been shut down because local migration task manager has been shut down!", taskId);
        }));
        registry.clear();
        reporterScheduler.stopAsync();
    }

    private class LocalMigrationTaskManagerSubscriber implements MigrationEventSubscriber<MigrationTaskLifecycleEvent> {

        @Override
        public void subscribe(MigrationTaskLifecycleEvent event) {
            MigrationTask<?, ?> task = event.getTask();
            if (event instanceof MigrationTaskStartedEvent) {
                log.info("Task [{}] started...", task.getTaskId());
                registry.register(task);
            }
            if (event instanceof MigrationTaskShutdownEvent) {
                log.info("Task [{}] has been shutdown...", task.getTaskId());
                task.getDispatcher().clearEventStream(task.getTaskId());
                registry.unregister(task);
            }
            if (event instanceof MigrationTaskFinishedEvent) {
                log.info("Task [{}] finished...", task.getTaskId());
                task.getDispatcher().clearEventStream(task.getTaskId());
                registry.unregister(task);
            }
            if (event instanceof MigrationTaskFailedEvent) {
                log.error("Task [{}] failed...", task.getTaskId());
                task.getDispatcher().clearEventStream(task.getTaskId());
                registry.unregister(task);
            }
        }

    }

}