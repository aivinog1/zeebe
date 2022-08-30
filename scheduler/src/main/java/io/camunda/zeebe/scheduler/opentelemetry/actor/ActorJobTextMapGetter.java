/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.scheduler.opentelemetry.actor;

import io.camunda.zeebe.scheduler.ActorJob;
import io.opentelemetry.context.propagation.TextMapGetter;

public class ActorJobTextMapGetter implements TextMapGetter<ActorJob> {

  @Override
  public Iterable<String> keys(final ActorJob actorJob) {
    return actorJob.getOpenTelemetryMetaData().keySet().stream().toList();
  }

  @Override
  public String get(final ActorJob actorJob, final String key) {
    if (actorJob != null) {
      return actorJob.getOpenTelemetryMetaData().get(key);
    }

    return null;
  }
}
