/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.cluster.messaging.impl;

import static com.google.common.base.Preconditions.checkState;

import io.atomix.utils.net.Address;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

class MessageDecoderV3 extends AbstractMessageDecoder {

  private DecoderState currentState = DecoderState.READ_SENDER_HOST_LENGTH;
  private int senderHostLength;
  private String senderHost;
  private int senderPort;
  private Address senderAddress;
  private ProtocolMessage.Type type;
  private long messageId;
  private int contentLength;
  private byte[] content;
  private int subjectLength;
  private int metadataLength;
  private final List<Pair<String, String>> metadataList = new ArrayList<>();
  private int metadataKeyLengthIndex = 0;
  private int lastMetadataKeyLength = 0;
  private int metadataKeyIndex = 0;
  private int metadataValueLengthIndex = 0;
  private int lastMetadataValueLength = 0;
  private int metadataValueIndex = 0;

  @Override
  @SuppressWarnings({"squid:S128"}) // suppress switch fall through warning
  protected void decode(
      final ChannelHandlerContext context, final ByteBuf buffer, final List<Object> out)
      throws Exception {

    switch (currentState) {
      case READ_SENDER_HOST_LENGTH:
        if (buffer.readableBytes() < Short.BYTES) {
          return;
        }
        senderHostLength = buffer.readShort();
        currentState = DecoderState.READ_SENDER_HOST;
      case READ_SENDER_HOST:
        if (buffer.readableBytes() < senderHostLength) {
          return;
        }
        senderHost = readString(buffer, senderHostLength);
        currentState = DecoderState.READ_SENDER_PORT;
      case READ_SENDER_PORT:
        if (buffer.readableBytes() < Integer.BYTES) {
          return;
        }
        senderPort = buffer.readInt();
        senderAddress = Address.from(senderHost, senderPort);
        currentState = DecoderState.READ_TYPE;
      case READ_TYPE:
        if (buffer.readableBytes() < Byte.BYTES) {
          return;
        }
        type = ProtocolMessage.Type.forId(buffer.readByte());
        currentState = DecoderState.READ_MESSAGE_ID;
      case READ_MESSAGE_ID:
        try {
          messageId = readLong(buffer);
        } catch (final Escape e) {
          return;
        }
        currentState = DecoderState.READ_CONTENT_LENGTH;
      case READ_CONTENT_LENGTH:
        try {
          contentLength = readInt(buffer);
        } catch (final Escape e) {
          return;
        }
        currentState = DecoderState.READ_CONTENT;
      case READ_CONTENT:
        if (buffer.readableBytes() < contentLength) {
          return;
        }
        if (contentLength > 0) {
          // TODO: Perform a sanity check on the size before allocating
          content = new byte[contentLength];
          buffer.readBytes(content);
        } else {
          content = EMPTY_PAYLOAD;
        }
        currentState = DecoderState.READ_METADATA_LENGTH;
      case READ_METADATA_LENGTH:
        try {
          metadataLength = buffer.readShort();
        } catch (final Escape e) {
          return;
        }
        currentState = DecoderState.READ_METADATA;
      case READ_METADATA:
        if (buffer.readableBytes() < Integer.BYTES) {
          return;
        }
        if (metadataLength > 0) {
          final MetadataAlignment metadataAlignment;
          if (metadataKeyIndex < metadataKeyLengthIndex) {
            metadataAlignment = MetadataAlignment.KEY;
          } else if (metadataValueLengthIndex < metadataKeyIndex) {
            metadataAlignment = MetadataAlignment.VALUE_LENGTH;
          } else if (metadataValueIndex < metadataValueLengthIndex) {
            metadataAlignment = MetadataAlignment.VALUE;
          } else {
            metadataAlignment = null;
          }
          if (metadataAlignment != null) {
            switch (metadataAlignment) {
              case KEY -> {
                if (buffer.readableBytes() < lastMetadataKeyLength) {
                  return;
                }
                final String metadataKey = readString(buffer, lastMetadataKeyLength);
                metadataList.add(MutablePair.of(metadataKey, null));
                metadataKeyIndex++;
              }
              case VALUE_LENGTH -> {
                if (buffer.readableBytes() < Integer.BYTES) {
                  return;
                }
                final int valueLength = buffer.readInt();
                lastMetadataValueLength = valueLength;
                metadataValueLengthIndex++;
              }
              case VALUE -> {
                if (buffer.readableBytes() < lastMetadataValueLength) {
                  return;
                }
                final String metadataValue = readString(buffer, lastMetadataValueLength);
                final Pair<String, String> foundPair =
                    Optional.ofNullable(metadataList.get(metadataValueIndex)).orElseThrow();
                foundPair.setValue(metadataValue);
                metadataValueIndex++;
                break;
              }
              default -> checkState(false, "Must not be here");
            }
          }
          if (metadataKeyLengthIndex == metadataValueLengthIndex
              && metadataValueLengthIndex == metadataKeyIndex
              && metadataKeyIndex == metadataValueIndex) {
            for (int i = metadataKeyIndex; i < metadataLength; i++) {
              if (buffer.readableBytes() < Integer.BYTES) {
                return;
              }
              final int keyLength = buffer.readInt();
              lastMetadataKeyLength = keyLength;
              metadataKeyLengthIndex++;
              if (buffer.readableBytes() < keyLength) {
                return;
              }
              final String metadataKey = readString(buffer, keyLength);
              metadataList.add(MutablePair.of(metadataKey, null));
              metadataKeyIndex++;
              if (buffer.readableBytes() < Integer.BYTES) {
                return;
              }
              final int valueLength = buffer.readInt();
              lastMetadataValueLength = valueLength;
              metadataValueLengthIndex++;
              if (buffer.readableBytes() < valueLength) {
                return;
              }
              final String metadataValue = readString(buffer, valueLength);
              final Pair<String, String> foundPair =
                  Optional.ofNullable(metadataList.get(metadataValueIndex)).orElseThrow();
              foundPair.setValue(metadataValue);
              metadataValueIndex++;
            }
          } else {
            checkState(false, "Oops, we must not be here");
          }
        }

        switch (type) {
          case REQUEST:
            currentState = DecoderState.READ_SUBJECT_LENGTH;
            break;
          case REPLY:
            currentState = DecoderState.READ_STATUS;
            break;
          default:
            checkState(false, "Must not be here");
        }
        break;
      default:
        break;
    }

    switch (type) {
      case REQUEST:
        switch (currentState) {
          case READ_SUBJECT_LENGTH:
            if (buffer.readableBytes() < Short.BYTES) {
              return;
            }
            subjectLength = buffer.readShort();
            currentState = DecoderState.READ_SUBJECT;
          case READ_SUBJECT:
            if (buffer.readableBytes() < subjectLength) {
              return;
            }
            final String subject = readString(buffer, subjectLength);
            final Map<String, String> metadata =
                metadataList.stream().collect(Collectors.toMap(Pair::getKey, Pair::getValue));
            final ProtocolRequest message =
                new ProtocolRequest(messageId, senderAddress, subject, content, metadata);
            out.add(message);
            metadataKeyLengthIndex = 0;
            lastMetadataKeyLength = 0;
            metadataKeyIndex = 0;
            metadataValueLengthIndex = 0;
            lastMetadataValueLength = 0;
            metadataValueIndex = 0;
            metadataList.clear();
            currentState = DecoderState.READ_TYPE;
            break;
          default:
            break;
        }
        break;
      case REPLY:
        switch (currentState) {
          case READ_STATUS:
            if (buffer.readableBytes() < Byte.BYTES) {
              return;
            }
            final ProtocolReply.Status status = ProtocolReply.Status.forId(buffer.readByte());
            final Map<String, String> metadataa =
                metadataList.stream().collect(Collectors.toMap(Pair::getKey, Pair::getValue));
            final ProtocolReply message = new ProtocolReply(messageId, content, status, metadataa);
            out.add(message);
            metadataKeyLengthIndex = 0;
            lastMetadataKeyLength = 0;
            metadataKeyIndex = 0;
            metadataValueLengthIndex = 0;
            lastMetadataValueLength = 0;
            metadataValueIndex = 0;
            metadataList.clear();
            currentState = DecoderState.READ_TYPE;
            break;
          default:
            break;
        }
        break;
      default:
        checkState(false, "Must not be here");
    }
  }

  /** V3 decoder state. */
  enum DecoderState {
    READ_TYPE,
    READ_MESSAGE_ID,
    READ_SENDER_HOST_LENGTH,
    READ_SENDER_HOST,
    READ_SENDER_PORT,
    READ_SUBJECT_LENGTH,
    READ_SUBJECT,
    READ_STATUS,
    READ_CONTENT_LENGTH,
    READ_CONTENT,
    READ_METADATA_LENGTH,
    READ_METADATA
  }

  enum MetadataAlignment {
    KEY,
    VALUE_LENGTH,
    VALUE
  }
}
