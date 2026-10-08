package com.yu.transferrag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import java.util.concurrent.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.import-tasks.enabled", havingValue = "true")
public class ImportTaskExecutionConfig {
    @Bean(destroyMethod = "shutdownNow")
    public ThreadPoolExecutor evidenceImportExecutor(@Value("${app.import-tasks.queue-capacity:8}") int capacity) {
        if (capacity < 1 || capacity > 64) throw new IllegalArgumentException("Invalid import queue capacity");
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity),
                runnable -> { Thread t = new Thread(runnable, "evidence-import-worker"); t.setDaemon(true); return t; },
                new ThreadPoolExecutor.AbortPolicy());
    }
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2); scheduler.setThreadNamePrefix("import-coordinator-");
        return scheduler;
    }
}
