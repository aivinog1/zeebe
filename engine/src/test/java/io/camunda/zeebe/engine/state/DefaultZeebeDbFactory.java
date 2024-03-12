/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state;

import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

public final class DefaultZeebeDbFactory {

  public static ZeebeDbFactory<ZbColumnFamilies> defaultFactory() {
    // enable consistency checks for tests
    final var consistencyChecks = new ConsistencyChecksSettings(false, false);
    return new ZeebeRocksDbFactory<>(
        new RocksDbConfiguration()
            .setMemoryLimit(RocksDbConfiguration.DEFAULT_MEMORY_LIMIT * 2)
            .setStatisticsEnabled(true)
//            .setIoRateBytesPerSecond(5 * 1000 * 1024)
        , consistencyChecks);
  }
}
