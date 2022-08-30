/*
 * Copyright 2018-present Open Networking Foundation
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

import io.atomix.cluster.messaging.telemetry.ProtocolReplyContextSetter;
import io.atomix.cluster.messaging.telemetry.ProtocolRequestContextGetter;
import io.atomix.cluster.messaging.telemetry.ProtocolRequestContextSetter;
import io.camunda.zeebe.util.VersionUtil;
import io.netty.channel.Channel;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.Optional;

/** Remote server connection manages messaging on the server side of a Netty connection. */
final class RemoteServerConnection extends AbstractServerConnection {
  private static final byte[] EMPTY_PAYLOAD = new byte[0];
  private final TextMapSetter<ProtocolReply> protocolReplyTextMapSetter =
      new ProtocolReplyContextSetter();
  private final TextMapGetter<ProtocolRequest> protocolRequestTextMapGetter =
      new ProtocolRequestContextGetter();
  private final TextMapSetter<ProtocolRequest> protocolRequestTextMapSetter =
      new ProtocolRequestContextSetter();
  private final OpenTelemetry openTelemetry;

  private final Channel channel;

  RemoteServerConnection(
      final HandlerRegistry handlers, final Channel channel, final OpenTelemetry openTelemetry) {
    super(handlers);
    this.channel = channel;
    this.openTelemetry = openTelemetry;
  }

  @Override
  public void reply(
      final ProtocolRequest message,
      final ProtocolReply.Status status,
      final Optional<byte[]> payload) {
    try (final Scope current =
        openTelemetry
            .getPropagators()
            .getTextMapPropagator()
            .extract(Context.current(), message, protocolRequestTextMapGetter)
            .makeCurrent()) {
      final ProtocolReply response =
          new ProtocolReply(
              message.id(), payload.orElse(EMPTY_PAYLOAD), status, message.metadata());
      final Tracer tracer =
          openTelemetry.getTracerProvider().get("zeebe-atomix-cluster", VersionUtil.getVersion());
      final Span span =
          tracer
              .spanBuilder("ProtocolReply")
              .setSpanKind(SpanKind.CLIENT)
              .setAttribute("message", message.toString())
              .setParent(Context.current())
              .startSpan();
      try (final Scope ignored = span.makeCurrent()) {
        openTelemetry
            .getPropagators()
            .getTextMapPropagator()
            .inject(Context.current(), response, protocolReplyTextMapSetter);
        span.addEvent("REPLY_SENDING");
        channel
            .writeAndFlush(response)
            .addListener(
                future -> {
                  try (final Scope scope = span.makeCurrent()) {
                    if (!future.isSuccess()) {
                      span.setStatus(StatusCode.ERROR);
                    } else {
                      span.setStatus(StatusCode.OK);
                    }
                    span.addEvent("REPLY_SENT");
                  } finally {
                    span.end();
                  }
                });
      }
    }
  }

  @Override
  public void dispatch(final ProtocolRequest message) {
    try (final Scope ignored =
        openTelemetry
            .getPropagators()
            .getTextMapPropagator()
            .extract(Context.current(), message, protocolRequestTextMapGetter)
            .makeCurrent()) {
      final Span span =
          openTelemetry
              .getTracerProvider()
              .get("zeebe-atomix-cluster", VersionUtil.getVersion())
              .spanBuilder("ProtocolRequest")
              .setSpanKind(SpanKind.SERVER)
              .setAttribute("message", message.toString())
              .startSpan();
      try (final Scope scope = span.makeCurrent()) {
        span.addEvent("REQUEST_RECEIVED");
        try {
          openTelemetry
              .getPropagators()
              .getTextMapPropagator()
              .inject(Context.current(), message, protocolRequestTextMapSetter);
          super.dispatch(message);
        } finally {
          span.addEvent("REQUEST_PROCESSED");
          span.end();
        }
      }
    }
  }
}
