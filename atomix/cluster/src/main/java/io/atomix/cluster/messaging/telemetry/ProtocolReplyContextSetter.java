/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.cluster.messaging.telemetry;

import io.atomix.cluster.messaging.impl.ProtocolReply;
import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;

public class ProtocolReplyContextSetter implements TextMapSetter<ProtocolReply> {

  @Override
  public void set(
      @Nullable final ProtocolReply protocolReply, final String key, final String value) {
    if (protocolReply != null) {
      protocolReply.metadata().put(key, value);
    }
  }
}
