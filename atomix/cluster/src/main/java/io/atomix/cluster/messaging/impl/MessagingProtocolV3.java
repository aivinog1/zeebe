/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.cluster.messaging.impl;

import io.atomix.utils.net.Address;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;

public class MessagingProtocolV3 implements MessagingProtocol {

  private final Address address;

  public MessagingProtocolV3(final Address address) {
    this.address = address;
  }

  @Override
  public ProtocolVersion version() {
    return ProtocolVersion.V3;
  }

  @Override
  public MessageToByteEncoder<Object> newEncoder() {
    return new MessageEncoderV3(address);
  }

  @Override
  public ByteToMessageDecoder newDecoder() {
    return new MessageDecoderV3();
  }
}
