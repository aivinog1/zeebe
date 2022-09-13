/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.admin.backup;

import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient.RequestStub;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerError;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerErrorResponse;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupStatusResponse;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import io.camunda.zeebe.protocol.record.ErrorCode;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class BackupQueryStub
    implements RequestStub<BackupStatusRequest, BrokerResponse<BackupStatusResponse>> {

  private final Map<Integer, Function<BackupStatusRequest, BrokerResponse<BackupStatusResponse>>>
      responses = new HashMap<>();

  @Override
  public void registerWith(final StubbedBrokerClient gateway) {
    gateway.registerHandler(BackupStatusRequest.class, this);
  }

  @Override
  public BrokerResponse<BackupStatusResponse> handle(final BackupStatusRequest request)
      throws Exception {
    return responses
        .getOrDefault(request.getPartitionId(), r -> new BrokerResponse<>(getCompletedStatus(r)))
        .apply(request);
  }

  private BackupStatusResponse getCompletedStatus(final BackupStatusRequest request) {
    return new BackupStatusResponse()
        .setBackupId(request.getBackupId())
        .setStatus(BackupStatusCode.COMPLETED)
        .setSnapshotId("sid")
        .setFailureReason("")
        .setCheckpointPosition(100)
        .setBrokerId(1)
        .setPartitionId(request.getPartitionId());
  }

  private BackupStatusResponse getFailedStatus(final BackupStatusRequest request) {
    return new BackupStatusResponse()
        .setBackupId(request.getBackupId())
        .setStatus(BackupStatusCode.FAILED)
        .setFailureReason("FAILED")
        .setBrokerId(1)
        .setPartitionId(request.getPartitionId());
  }

  private BackupStatusResponse getInProgressStatus(final BackupStatusRequest request) {
    return new BackupStatusResponse()
        .setBackupId(request.getBackupId())
        .setStatus(BackupStatusCode.IN_PROGRESS)
        .setSnapshotId("sid")
        .setFailureReason("")
        .setCheckpointPosition(100)
        .setBrokerId(1)
        .setPartitionId(request.getPartitionId());
  }

  private BackupStatusResponse getDoesNotExistStatus(final BackupStatusRequest request) {
    return new BackupStatusResponse()
        .setBackupId(request.getBackupId())
        .setStatus(BackupStatusCode.DOES_NOT_EXIST)
        .setFailureReason("");
  }

  public BackupQueryStub withErrorResponseFor(final int partitionId) {
    responses.put(
        partitionId,
        r -> new BrokerErrorResponse<>(new BrokerError(ErrorCode.INTERNAL_ERROR, "ERROR")));
    return this;
  }

  public BackupQueryStub withFailedResponseFor(final int partitionId) {
    responses.put(partitionId, request -> new BrokerResponse<>(getFailedStatus(request)));
    return this;
  }

  public BackupQueryStub withInProgressResponseFor(final int partitionId) {
    responses.put(partitionId, request -> new BrokerResponse<>(getInProgressStatus(request)));
    return this;
  }

  public BackupQueryStub withDoesNotExistFor(final int partitionId) {
    responses.put(partitionId, request -> new BrokerResponse<>(getDoesNotExistStatus(request)));
    return this;
  }
}
