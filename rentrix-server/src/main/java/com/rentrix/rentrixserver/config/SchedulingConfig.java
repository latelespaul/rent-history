package com.rentrix.rentrixserver.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * In-process scheduled jobs (D-08), each guarded by
 * {@link com.rentrix.rentrixserver.common.scheduling.JobLock}. {@code SCHEDULING_ENABLED=false}
 * turns them off (the test profile does, so tests run jobs by calling them).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(name = "app.scheduling.enabled", matchIfMissing = true)
@EnableScheduling
public class SchedulingConfig {

}
