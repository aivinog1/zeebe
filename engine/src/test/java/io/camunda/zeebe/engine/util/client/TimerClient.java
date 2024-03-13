/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.function.Function;

public class TimerClient {

  private static final Function<Long, Record<TimerRecordValue>> SUCCESS_CREATE_EXPECTATION =
      (position) ->
          RecordingExporter.timerRecords(TimerIntent.CREATED)
              .withPosition(position)
              .getFirst();

  private static final Function<Long, Record<TimerRecordValue>> SUCCESS_TRIGGERED_EXPECTATION =
      (position) ->
          RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
              .withPosition(position)
              .getFirst();
  private final CommandWriter commandWriter;
  private final TimerRecord timerRecord = new TimerRecord();
  private final Function<Long, Record<TimerRecordValue>> createExpectation = SUCCESS_CREATE_EXPECTATION;
  private final Function<Long, Record<TimerRecordValue>> triggeredExpectation = SUCCESS_TRIGGERED_EXPECTATION;

  public TimerClient(CommandWriter commandWriter) {
    this.commandWriter = commandWriter;
  }

  public TimerClient withDueDate(long dueDate) {
    timerRecord.setDueDate(dueDate);
    return this;
  }

  public TimerClient withElementInstanceKey(long elementInstanceKey) {
    timerRecord.setElementInstanceKey(elementInstanceKey);
    return this;
  }

  public TimerClient withProcessInstanceKey(long processInstanceKey) {
    timerRecord.setProcessInstanceKey(processInstanceKey);
    return this;
  }

  public TimerClient withTargetElementId(String targetElementId) {
    timerRecord.setTargetElementId(BufferUtil.wrapString(targetElementId));
    return this;
  }

  public TimerClient withRepetitions(final int repetitions) {
    timerRecord.setRepetitions(repetitions);
    return this;
  }

  public TimerClient withProcessDefinitionKey(final long processDefinitionKey) {
    timerRecord.setProcessDefinitionKey(processDefinitionKey);
    return this;
  }

  public Record<TimerRecordValue> createTimer() {
    final long position = commandWriter.writeCommand(TimerIntent.CREATED, timerRecord);
    return createExpectation.apply(position);
  }

  public Record<TimerRecordValue> triggerTimer() {
    final long position = commandWriter.writeCommand(TimerIntent.TRIGGERED, timerRecord);
    return triggeredExpectation.apply(position);
  }
}
