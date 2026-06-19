package com.moviebooking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@Profile("test")
public class TestAsyncConfig {

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        return new SyncTaskExecutor();
    }
}
