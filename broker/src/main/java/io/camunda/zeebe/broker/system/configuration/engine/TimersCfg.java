/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import io.camunda.zeebe.engine.EngineConfiguration;
import java.util.Objects;

public final class TimersCfg implements ConfigurationEntry {

  private long limit = EngineConfiguration.DEFAULT_TIMER_LIMIT;
  private long backoffMinValue = EngineConfiguration.DEFAULT_TIMER_OVER_LIMIT_BACKOFF_MIN_VALUE;
  private long backoffMaxValue = EngineConfiguration.DEFAULT_TIMER_OVER_LIMIT_BACKOFF_MAX_VALUE;
  private double backoffFactor = EngineConfiguration.DEFAULT_TIMER_OVER_LIMIT_BACKOFF_FACTOR;
  private double backoffJitterFactor = EngineConfiguration.DEFAULT_TIMER_OVER_LIMIT_JITTER_FACTOR;

  public long getLimit() {
    return limit;
  }

  public void setLimit(final long limit) {
    this.limit = limit;
  }

  public long getBackoffMinValue() {
    return backoffMinValue;
  }

  public void setBackoffMinValue(final long backoffMinValue) {
    this.backoffMinValue = backoffMinValue;
  }

  public long getBackoffMaxValue() {
    return backoffMaxValue;
  }

  public void setBackoffMaxValue(final long backoffMaxValue) {
    this.backoffMaxValue = backoffMaxValue;
  }

  public double getBackoffFactor() {
    return backoffFactor;
  }

  public void setBackoffFactor(final double backoffFactor) {
    this.backoffFactor = backoffFactor;
  }

  public double getBackoffJitterFactor() {
    return backoffJitterFactor;
  }

  public void setBackoffJitterFactor(final double backoffJitterFactor) {
    this.backoffJitterFactor = backoffJitterFactor;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TimersCfg timersCfg = (TimersCfg) o;
    return limit == timersCfg.limit
        && backoffMinValue == timersCfg.backoffMinValue
        && backoffMaxValue == timersCfg.backoffMaxValue
        && Double.compare(backoffFactor, timersCfg.backoffFactor) == 0
        && Double.compare(backoffJitterFactor, timersCfg.backoffJitterFactor) == 0;
  }

  @Override
  public String toString() {
    return "TimersCfg{"
        + "limit="
        + limit
        + ", backoffMinValue="
        + backoffMinValue
        + ", backoffMaxValue="
        + backoffMaxValue
        + ", backoffFactor="
        + backoffFactor
        + ", backoffJitterFactor="
        + backoffJitterFactor
        + '}';
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        limit, backoffMinValue, backoffMaxValue, backoffFactor, backoffJitterFactor);
  }
}
