/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine;

import io.camunda.zeebe.util.ExponentialBackoff;
import java.time.Duration;

public final class EngineConfiguration {

  public static final int DEFAULT_MESSAGES_TTL_CHECKER_BATCH_LIMIT = Integer.MAX_VALUE;
  public static final Duration DEFAULT_MESSAGES_TTL_CHECKER_INTERVAL = Duration.ofMinutes(1);

  public static final int DEFAULT_MAX_ERROR_MESSAGE_SIZE = 10000;

  // This size (in bytes) is used as a buffer when filling an event/command up to the maximum
  // message size.
  public static final int BATCH_SIZE_CALCULATION_BUFFER = 1024 * 8;

  public static final int DEFAULT_DRG_CACHE_CAPACITY = 1000;
  public static final Duration DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL = Duration.ofSeconds(1);
  public static final int DEFAULT_JOBS_TIMEOUT_CHECKER_BATCH_LIMIT = Integer.MAX_VALUE;

  public static final long DEFAULT_TIMER_LIMIT = Long.MAX_VALUE;
  public static final long DEFAULT_TIMER_OVER_LIMIT_BACKOFF_MIN_VALUE =
      Duration.ofSeconds(10).toMillis();
  public static final long DEFAULT_TIMER_OVER_LIMIT_BACKOFF_MAX_VALUE =
      Duration.ofSeconds(60).toMillis();
  public static final double DEFAULT_TIMER_OVER_LIMIT_BACKOFF_FACTOR = 1.6;
  public static final double DEFAULT_TIMER_OVER_LIMIT_JITTER_FACTOR = 0.1;

  public static final ExponentialBackoff DEFAULT_TIMER_OVER_LIMIT_BACKOFF =
      new ExponentialBackoff(
          DEFAULT_TIMER_OVER_LIMIT_BACKOFF_MAX_VALUE,
          DEFAULT_TIMER_OVER_LIMIT_BACKOFF_MIN_VALUE,
          DEFAULT_TIMER_OVER_LIMIT_BACKOFF_FACTOR,
          DEFAULT_TIMER_OVER_LIMIT_JITTER_FACTOR);

  private int messagesTtlCheckerBatchLimit = DEFAULT_MESSAGES_TTL_CHECKER_BATCH_LIMIT;
  private Duration messagesTtlCheckerInterval = DEFAULT_MESSAGES_TTL_CHECKER_INTERVAL;
  private int drgCacheCapacity = DEFAULT_DRG_CACHE_CAPACITY;
  private Duration jobsTimeoutCheckerPollingInterval = DEFAULT_JOBS_TIMEOUT_POLLING_INTERVAL;
  private int jobsTimeoutCheckerBatchLimit = DEFAULT_JOBS_TIMEOUT_CHECKER_BATCH_LIMIT;

  private long timerLimit = DEFAULT_TIMER_LIMIT;
  private ExponentialBackoff timerOverLimitBackoff = DEFAULT_TIMER_OVER_LIMIT_BACKOFF;

  public int getMessagesTtlCheckerBatchLimit() {
    return messagesTtlCheckerBatchLimit;
  }

  public EngineConfiguration setMessagesTtlCheckerBatchLimit(
      final int messagesTtlCheckerBatchLimit) {
    this.messagesTtlCheckerBatchLimit = messagesTtlCheckerBatchLimit;
    return this;
  }

  public Duration getMessagesTtlCheckerInterval() {
    return messagesTtlCheckerInterval;
  }

  public EngineConfiguration setMessagesTtlCheckerInterval(
      final Duration messagesTtlCheckerInterval) {
    this.messagesTtlCheckerInterval = messagesTtlCheckerInterval;
    return this;
  }

  public int getDrgCacheCapacity() {
    return drgCacheCapacity;
  }

  public EngineConfiguration setDrgCacheCapacity(final int drgCacheCapacity) {
    this.drgCacheCapacity = drgCacheCapacity;
    return this;
  }

  public Duration getJobsTimeoutCheckerPollingInterval() {
    return jobsTimeoutCheckerPollingInterval;
  }

  public EngineConfiguration setJobsTimeoutCheckerPollingInterval(
      final Duration jobsTimeoutCheckerPollingInterval) {
    this.jobsTimeoutCheckerPollingInterval = jobsTimeoutCheckerPollingInterval;
    return this;
  }

  public int getJobsTimeoutCheckerBatchLimit() {
    return jobsTimeoutCheckerBatchLimit;
  }

  public EngineConfiguration setJobsTimeoutCheckerBatchLimit(
      final int jobsTimeoutCheckerBatchLimit) {
    this.jobsTimeoutCheckerBatchLimit = jobsTimeoutCheckerBatchLimit;
    return this;
  }

  public long getTimerLimit() {
    return timerLimit;
  }

  public EngineConfiguration setTimerLimit(final long timerLimit) {
    this.timerLimit = timerLimit;
    return this;
  }

  public ExponentialBackoff getTimerOverLimitBackoff() {
    return timerOverLimitBackoff;
  }

  public EngineConfiguration setTimerOverLimitBackoff(
      final ExponentialBackoff timerOverLimitBackoff) {
    this.timerOverLimitBackoff = timerOverLimitBackoff;
    return this;
  }
}
