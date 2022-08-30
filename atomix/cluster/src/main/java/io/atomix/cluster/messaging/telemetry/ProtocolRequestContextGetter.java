/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.cluster.messaging.telemetry;

import io.atomix.cluster.messaging.impl.ProtocolRequest;
import io.opentelemetry.context.propagation.TextMapGetter;
import javax.annotation.Nullable;

public class ProtocolRequestContextGetter implements TextMapGetter<ProtocolRequest> {

  @Override
  public Iterable<String> keys(final ProtocolRequest protocolRequest) {
    return protocolRequest.metadata().keySet();
  }

  @Nullable
  @Override
  public String get(@Nullable final ProtocolRequest protocolRequest, final String key) {
    if (protocolRequest != null) {
      return protocolRequest.metadata().get(key);
    }
    return null;
  }
}
