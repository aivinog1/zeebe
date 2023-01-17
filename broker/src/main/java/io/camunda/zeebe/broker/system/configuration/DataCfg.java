/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.util.unit.DataSize;

public final class DataCfg implements ConfigurationEntry {

  public static final String DEFAULT_DIRECTORY = "data";
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private static final DataSize DEFAULT_DATA_SIZE = DataSize.ofMegabytes(128);
  private static final boolean DEFAULT_DISK_USAGE_MONITORING_ENABLED = true;
  private static final double DEFAULT_DISK_USAGE_REPLICATION_WATERMARK = 0.99;
  private static final double DEFAULT_DISK_USAGE_COMMAND_WATERMARK = 0.97;
  private static final Duration DEFAULT_DISK_USAGE_MONITORING_DELAY = Duration.ofSeconds(1);

  private String directory = DEFAULT_DIRECTORY;

  private DataSize logSegmentSize = DEFAULT_DATA_SIZE;

  private Duration snapshotPeriod = Duration.ofMinutes(5);

  private int logIndexDensity = 100;

  private boolean diskUsageMonitoringEnabled = DEFAULT_DISK_USAGE_MONITORING_ENABLED;
  private double diskUsageReplicationWatermark = DEFAULT_DISK_USAGE_REPLICATION_WATERMARK;
  private double diskUsageCommandWatermark = DEFAULT_DISK_USAGE_COMMAND_WATERMARK;
  private Duration diskUsageMonitoringInterval = DEFAULT_DISK_USAGE_MONITORING_DELAY;
  private DiskCfg disk = new DiskCfg();
  private BackupStoreCfg backup = new BackupStoreCfg();

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    directory = ConfigurationUtil.toAbsolutePath(directory, brokerBase);

    backup.init(globalConfig, brokerBase);

    overrideDiskConfig();
    disk.init(globalConfig, brokerBase);
  }

  private void overrideDiskConfig() {
    // For backward compatibility, if the old disk watermarks are configured use those values
    // instead of the new ones
    if (diskUsageMonitoringEnabled != DEFAULT_DISK_USAGE_MONITORING_ENABLED) {
      LOG.warn(
          "Configuration parameter data.diskUsageMonitoringEnabled is deprecated. Use data.disk.enableMonitoring instead.");
      disk.setEnableMonitoring(diskUsageMonitoringEnabled);
    }
    if (!diskUsageMonitoringInterval.equals(DEFAULT_DISK_USAGE_MONITORING_DELAY)) {
      LOG.warn(
          "Configuration parameter data.diskUsageMonitoringInterval is deprecated. Use data.disk.monitoringInterval instead.");
      disk.setMonitoringInterval(diskUsageMonitoringInterval);
    }
    if (diskUsageCommandWatermark != DEFAULT_DISK_USAGE_COMMAND_WATERMARK) {
      LOG.warn(
          "Configuration parameter data.diskUsageCommandWatermark is deprecated. Use data.disk.freeSpace.processing instead.");
      disk.getFreeSpace().setProcessing((1 - diskUsageCommandWatermark) * 100 + "%");
    }
    if (diskUsageReplicationWatermark != DEFAULT_DISK_USAGE_REPLICATION_WATERMARK) {
      LOG.warn(
          "Configuration parameter data.diskUsageReplicationWatermark is deprecated. Use data.disk.freeSpace.replication instead.");
      disk.getFreeSpace().setReplication((1 - diskUsageReplicationWatermark) * 100 + "%");
    }
  }

  public String getDirectory() {
    return directory;
  }

  public void setDirectory(final String directory) {
    this.directory = directory;
  }

  public long getLogSegmentSizeInBytes() {
    return Optional.ofNullable(logSegmentSize).orElse(DEFAULT_DATA_SIZE).toBytes();
  }

  public DataSize getLogSegmentSize() {
    return logSegmentSize;
  }

  public void setLogSegmentSize(final DataSize logSegmentSize) {
    this.logSegmentSize = logSegmentSize;
  }

  public Duration getSnapshotPeriod() {
    return snapshotPeriod;
  }

  public void setSnapshotPeriod(final Duration snapshotPeriod) {
    this.snapshotPeriod = snapshotPeriod;
  }

  public int getLogIndexDensity() {
    return logIndexDensity;
  }

  public void setLogIndexDensity(final int logIndexDensity) {
    this.logIndexDensity = logIndexDensity;
  }

  public boolean isDiskUsageMonitoringEnabled() {
    return disk.isEnableMonitoring();
  }

  public void setDiskUsageMonitoringEnabled(final boolean diskUsageMonitoringEnabled) {
    this.diskUsageMonitoringEnabled = diskUsageMonitoringEnabled;
  }

  public void setDiskUsageCommandWatermark(final double diskUsageCommandWatermark) {
    this.diskUsageCommandWatermark = diskUsageCommandWatermark;
  }

  public void setDiskUsageReplicationWatermark(final double diskUsageReplicationWatermark) {
    this.diskUsageReplicationWatermark = diskUsageReplicationWatermark;
  }

  public void setDiskUsageMonitoringInterval(final Duration diskUsageMonitoringInterval) {
    this.diskUsageMonitoringInterval = diskUsageMonitoringInterval;
  }

  public DiskCfg getDisk() {
    return disk;
  }

  public void setDisk(final DiskCfg disk) {
    this.disk = disk;
  }

  public BackupStoreCfg getBackup() {
    return backup;
  }

  public void setBackup(final BackupStoreCfg backup) {
    this.backup = backup;
  }

  @Override
  public String toString() {
    return "DataCfg{"
        + "directory='"
        + directory
        + '\''
        + ", logSegmentSize="
        + logSegmentSize
        + ", snapshotPeriod="
        + snapshotPeriod
        + ", logIndexDensity="
        + logIndexDensity
        + ", diskUsageMonitoringEnabled="
        + diskUsageMonitoringEnabled
        + ", diskUsageReplicationWatermark="
        + diskUsageReplicationWatermark
        + ", diskUsageCommandWatermark="
        + diskUsageCommandWatermark
        + ", diskUsageMonitoringInterval="
        + diskUsageMonitoringInterval
        + ", disk="
        + disk
        + ", backup="
        + backup
        + '}';
  }
}
