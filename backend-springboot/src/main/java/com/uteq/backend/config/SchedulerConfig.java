package com.uteq.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class SchedulerConfig {

    @Bean
    /**
     * Handles task scheduler.
     *
     * @return task scheduler with the resulting state after the operation
     */
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("sgb-backup-scheduler-");
        scheduler.setThreadPriority(Thread.NORM_PRIORITY);
        scheduler.setDaemon(true);
        return scheduler;
    }
}