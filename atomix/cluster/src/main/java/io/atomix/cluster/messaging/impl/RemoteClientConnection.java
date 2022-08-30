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

import io.atomix.cluster.messaging.telemetry.ProtocolReplyContextGetter;
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
import java.util.concurrent.CompletableFuture;

/** Client-side Netty remote connection. */
final class RemoteClientConnection extends AbstractClientConnection {
  private final Channel channel;
  private final TextMapSetter<ProtocolRequest> protocolRequestTextMapSetter =
      new ProtocolRequestContextSetter();
  private final TextMapGetter<ProtocolReply> protocolReplyTextMapGetter =
      new ProtocolReplyContextGetter();
  private final OpenTelemetry openTelemetry;

  RemoteClientConnection(final Channel channel, final OpenTelemetry openTelemetry) {
    this.channel = channel;
    this.openTelemetry = openTelemetry;
  }

  @Override
  public CompletableFuture<Void> sendAsync(final ProtocolRequest message) {
    final Tracer tracer =
        openTelemetry.getTracerProvider().get("zeebe-atomix-cluster", VersionUtil.getVersion());
    final CompletableFuture<Void> future = new CompletableFuture<>();

    final Span span =
        tracer
            .spanBuilder("ProtocolRequest")
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute("message", message.toString())
            .setAttribute("type", "async")
            .startSpan();
    final Scope scope = span.makeCurrent();
    openTelemetry
        .getPropagators()
        .getTextMapPropagator()
        .inject(Context.current(), message, protocolRequestTextMapSetter);
    channel
        .writeAndFlush(message)
        .addListener(
            channelFuture -> {
              if (!channelFuture.isSuccess()) {
                future.completeExceptionally(channelFuture.cause());
                span.setStatus(StatusCode.ERROR);
              } else {
                future.complete(null);
                span.setStatus(StatusCode.OK);
              }
              span.addEvent("REQUEST_SENT");
              span.end();
              scope.close();
            });
    return future;
  }

  @Override
  public CompletableFuture<byte[]> sendAndReceive(final ProtocolRequest message) {
    final CompletableFuture<byte[]> responseFuture = awaitResponseForRequestWithId(message.id());
    final Tracer tracer =
        openTelemetry.getTracerProvider().get("zeebe-atomix-cluster", VersionUtil.getVersion());
    final Span span =
        tracer
            .spanBuilder("ProtocolRequest")
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute("message", message.toString())
            .setAttribute("type", "sync")
            .startSpan();

    try (final Scope ignored = span.makeCurrent()) {
      openTelemetry
          .getPropagators()
          .getTextMapPropagator()
          .inject(Context.current(), message, protocolRequestTextMapSetter);
      span.addEvent("REQUEST_SENDING");
      channel
          .writeAndFlush(message)
          .addListener(
              channelFuture -> {
                try (final Scope scope = span.makeCurrent()) {
                  if (!channelFuture.isSuccess()) {
                    responseFuture.completeExceptionally(channelFuture.cause());
                    span.setStatus(StatusCode.ERROR);
                  } else {
                    span.setStatus(StatusCode.OK);
                  }
                  span.addEvent("REQUEST_SENT");
                } finally {
                  span.end();
                }
              });
    }
    return responseFuture;
  }

  @Override
  public void dispatch(final ProtocolReply message) {
    try (final Scope ignored =
        openTelemetry
            .getPropagators()
            .getTextMapPropagator()
            .extract(Context.current(), message, protocolReplyTextMapGetter)
            .makeCurrent()) {
      final Span span =
          openTelemetry
              .getTracerProvider()
              .get("zeebe-atomix-cluster", VersionUtil.getVersion())
              .spanBuilder("ProtocolReply")
              .setSpanKind(SpanKind.SERVER)
              .setAttribute("message", message.toString())
              .startSpan();
      try (final Scope scope = span.makeCurrent()) {
        span.addEvent("REPLY_RECEIVED");
        try {
          super.dispatch(message);
          span.addEvent("REPLY_PROCESSED");
        } finally {
          span.end();
        }
      }
    }
  }

  @Override
  public String toString() {
    return "RemoteClientConnection{channel=" + channel + "}";
  }
}
