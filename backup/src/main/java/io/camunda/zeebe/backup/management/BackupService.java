/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.management;

import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import java.nio.file.Path;
import java.util.function.Predicate;
import io.opentelemetry.api.OpenTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Backup manager that takes and manages backup asynchronously */
public final class BackupService extends Actor implements BackupManager {
  private static final Logger LOG = LoggerFactory.getLogger(BackupService.class);
  private final String actorName;
  private final int nodeId;
  private final int partitionId;
  private final int numberOfPartitions;
  private final BackupServiceImpl internalBackupManager;
  private final PersistedSnapshotStore snapshotStore;
  private final Path segmentsDirectory;
  private final Predicate<Path> isSegmentsFile;
  private final OpenTelemetry openTelemetry;


  public BackupService(
      final int nodeId,
      final int partitionId,
      final int numberOfPartitions,
      final PersistedSnapshotStore snapshotStore,
      final Predicate<Path> isSegmentsFile,
      final Path segmentsDirectory,
      final OpenTelemetry openTelemetry) {
    // Use a noop backup store until a proper backup store is available
    this(
        nodeId,
        partitionId,
        numberOfPartitions,
        NoopBackupStore.INSTANCE,
        snapshotStore,
        segmentsDirectory,
        isSegmentsFile,
        openTelemetry);
  }

  public BackupService(
      final int nodeId,
      final int partitionId,
      final int numberOfPartitions,
      final BackupStore backupStore,
      final PersistedSnapshotStore snapshotStore,
      final Path segmentsDirectory,
      final Predicate<Path> isSegmentsFile,
      final OpenTelemetry openTelemetry) {
    super(openTelemetry);
    this.nodeId = nodeId;
    this.partitionId = partitionId;
    this.numberOfPartitions = numberOfPartitions;
    this.snapshotStore = snapshotStore;
    this.segmentsDirectory = segmentsDirectory;
    this.isSegmentsFile = isSegmentsFile;
    internalBackupManager = new BackupServiceImpl(backupStore);
    actorName = buildActorName(nodeId, "BackupService", partitionId);
    this.openTelemetry = openTelemetry;
  }

  @Override
  public String getName() {
    return actorName;
  }

  @Override
  protected void onActorClosing() {
    internalBackupManager.close();
  }

  @Override
  public void takeBackup(final long checkpointId, final long checkpointPosition) {
    actor.run(
        () -> {
          final InProgressBackupImpl inProgressBackup =
              new InProgressBackupImpl(
                  snapshotStore,
                  new BackupIdentifierImpl(nodeId, partitionId, checkpointId),
                  checkpointPosition,
                  numberOfPartitions,
                  actor,
                  segmentsDirectory,
                  isSegmentsFile);
          internalBackupManager
              .takeBackup(inProgressBackup, actor)
              .onComplete(
                  (ignore, error) -> {
                    if (error != null) {
                      LOG.warn(
                          "Failed to take backup {} at position {}",
                          inProgressBackup.checkpointId(),
                          inProgressBackup.checkpointPosition(),
                          error);
                    } else {
                      LOG.info(
                          "Backup {} at position {} completed",
                          inProgressBackup.checkpointId(),
                          inProgressBackup.checkpointPosition());
                    }
                  });
        });
  }

  @Override
  public ActorFuture<BackupStatus> getBackupStatus(final long checkpointId) {
    return CompletableActorFuture.completedExceptionally(
        new UnsupportedOperationException("Not implemented"), openTelemetry);
  }

  @Override
  public ActorFuture<Void> deleteBackup(final long checkpointId) {
    return CompletableActorFuture.completedExceptionally(
        new UnsupportedOperationException("Not implemented"), openTelemetry);
  }
}
