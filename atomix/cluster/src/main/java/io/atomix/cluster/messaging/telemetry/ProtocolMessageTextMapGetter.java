/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.cluster.messaging.telemetry;

import io.atomix.cluster.messaging.impl.ProtocolMessage;
import io.opentelemetry.context.propagation.TextMapGetter;
import javax.annotation.Nullable;

public class ProtocolMessageTextMapGetter implements TextMapGetter<ProtocolMessage> {

  @Override
  public Iterable<String> keys(final ProtocolMessage protocolMessage) {
    return protocolMessage.metadata().keySet();
  }

  @Nullable
  @Override
  public String get(@Nullable final ProtocolMessage protocolMessage, final String key) {
    if (protocolMessage != null) {
      return protocolMessage.metadata().get(key);
    }
    return null;
  }
}
