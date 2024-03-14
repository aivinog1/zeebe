/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl;

import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class DefaultZeebeDbFactory {

  public static <ColumnFamilyType extends Enum<ColumnFamilyType>>
      ZeebeDbFactory<ColumnFamilyType> getDefaultFactory() {
    // enable consistency checks for tests
    final var consistencyChecks = new ConsistencyChecksSettings(false, false);
    final Properties columnFamilyProps = new Properties();
    try(final InputStream loadtestPropsStream = DefaultZeebeDbFactory.class.getResourceAsStream("/loadtest-timers.properties")){
      columnFamilyProps.load(loadtestPropsStream);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return new ZeebeRocksDbFactory<>(
        new RocksDbConfiguration()
            .setMemoryLimit(RocksDbConfiguration.DEFAULT_MEMORY_LIMIT)
            .setStatisticsEnabled(true)
            .setIoRateBytesPerSecond(5 * 1024 * 1024)
            .setColumnFamilyOptions(columnFamilyProps)
        , consistencyChecks);
  }
}
