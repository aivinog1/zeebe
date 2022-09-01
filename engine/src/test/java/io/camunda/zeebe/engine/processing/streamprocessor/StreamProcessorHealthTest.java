/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.api.EmptyProcessingResult;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.engine.util.StreamPlatform;
import io.camunda.zeebe.engine.util.StreamPlatformExtension;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.streamprocessor.StreamProcessor;
import java.util.concurrent.atomic.AtomicBoolean;
import io.opentelemetry.api.OpenTelemetry;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(StreamPlatformExtension.class)
public class StreamProcessorHealthTest {

  private static final ProcessInstanceRecord PROCESS_INSTANCE_RECORD = Records.processInstance(1);

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;

  private StreamProcessor streamProcessor;

  @Test
  void shouldBeHealthyOnStart() {
    // when
    streamProcessor = streamPlatform.startStreamProcessor();

    // then
    Awaitility.await("wait to become healthy again")
        .until(() -> streamProcessor.getHealthReport().isHealthy());
  }

  @Test
  void shouldMarkUnhealthyWhenLoopInErrorHandling() {
    // given
    streamProcessor = streamPlatform.startStreamProcessor();

    final var mockProcessor = streamPlatform.getDefaultRecordProcessor();
    when(mockProcessor.process(any(), any())).thenThrow(new RuntimeException("expected"));
    when(mockProcessor.onProcessingError(any(), any(), any()))
        .thenThrow(new RuntimeException("expected"));

    // when
    // since processing fails we will write error event
    // we want to fail error even transaction
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD));

    // then
    Awaitility.await("wait to become unhealthy")
        .until(() -> streamProcessor.getHealthReport().isUnhealthy());
  }

  @Test
  void shouldBecomeHealthyWhenErrorIsResolved() {
    // given
    streamProcessor = streamPlatform.startStreamProcessor();
    final var shouldFail = new AtomicBoolean(true);

    final var mockProcessor = streamPlatform.getDefaultRecordProcessor();
    when(mockProcessor.process(any(), any())).thenThrow(new RuntimeException("expected"));
    when(mockProcessor.onProcessingError(any(), any(), any()))
        .thenAnswer(
            invocationOnMock -> {
              if (shouldFail.get()) {
                throw new RuntimeException("expected");
              }
              return EmptyProcessingResult.INSTANCE;
            });
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD));
    Awaitility.await("wait to become unhealthy")
        .until(() -> streamProcessor.getHealthReport().isUnhealthy());

    // when
    shouldFail.set(false);

    // then
    Awaitility.await("wait to become healthy again")
        .until(() -> streamProcessor.getHealthReport().isHealthy());
  }
}
