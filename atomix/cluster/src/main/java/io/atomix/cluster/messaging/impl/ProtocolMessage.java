/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.cluster.messaging.impl;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/** Base class for internal messages. */
public abstract class ProtocolMessage {

  private final long id;
  private final byte[] payload;
  private final Map<String, String> metadata;

  protected ProtocolMessage(
      final long id, final byte[] payload, final Map<String, String> metadata) {
    this.id = id;
    this.payload = payload;
    this.metadata = metadata;
  }

  public abstract Type type();

  public boolean isRequest() {
    return type() == Type.REQUEST;
  }

  public boolean isReply() {
    return type() == Type.REPLY;
  }

  public long id() {
    return id;
  }

  public byte[] payload() {
    return payload;
  }

  public Map<String, String> metadata() {
    return metadata;
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(id, metadata);
    result = 31 * result + Arrays.hashCode(payload);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProtocolMessage that = (ProtocolMessage) o;
    return id == that.id
        && Arrays.equals(payload, that.payload)
        && Objects.equals(metadata, that.metadata);
  }

  /** Internal message type. */
  public enum Type {
    REQUEST(1),
    REPLY(2);

    private final int id;

    Type(final int id) {
      this.id = id;
    }

    /**
     * Returns the unique message type ID.
     *
     * @return the unique message type ID.
     */
    public int id() {
      return id;
    }

    /**
     * Returns the message type enum associated with the given ID.
     *
     * @param id the type ID.
     * @return the type enum for the given ID.
     */
    public static Type forId(final int id) {
      switch (id) {
        case 1:
          return REQUEST;
        case 2:
          return REPLY;
        default:
          throw new IllegalArgumentException("Unknown status ID " + id);
      }
    }
  }
}
