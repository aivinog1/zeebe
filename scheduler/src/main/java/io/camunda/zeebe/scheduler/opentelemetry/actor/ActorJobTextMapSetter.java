/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.scheduler.opentelemetry.actor;

import io.camunda.zeebe.scheduler.ActorJob;
import io.opentelemetry.context.propagation.TextMapSetter;

public class ActorJobTextMapSetter implements TextMapSetter<ActorJob> {

  @Override
  public void set(final ActorJob actorJob, final String key, final String value) {
    if (actorJob != null) {
      actorJob.putOpenTelemetryMetaData(key, value);
    }
  }
}
