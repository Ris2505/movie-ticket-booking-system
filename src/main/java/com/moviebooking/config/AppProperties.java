package com.moviebooking.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private int holdDurationMinutes = 5;
    private String holdExpiryCron = "*/30 * * * * *";
    private String reminderCron = "0 0 * * * *";
    private int reminderHoursBeforeShow = 2;
    private String timezone = "Asia/Kolkata";
}
