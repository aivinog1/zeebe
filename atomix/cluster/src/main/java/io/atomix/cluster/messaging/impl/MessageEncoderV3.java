/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.cluster.messaging.impl;

import io.atomix.utils.net.Address;
import io.netty.buffer.ByteBuf;

class MessageEncoderV3 extends MessageEncoderV2 {

  public MessageEncoderV3(final Address address) {
    super(address);
  }

  @Override
  protected void encodeMessage(final ProtocolMessage message, final ByteBuf buffer) {
    super.encodeMessage(message, buffer);
    writeStringStringMap(buffer, message.metadata());
  }
}
