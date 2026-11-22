package com.entry.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Async} runs on virtual threads (spring.threads.virtual.enabled), used for notification fan-out.
 * Scheduling drives the daily trial reminders.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
