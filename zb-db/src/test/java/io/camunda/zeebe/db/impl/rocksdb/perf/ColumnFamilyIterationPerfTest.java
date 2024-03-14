/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.rocksdb.perf;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DefaultZeebeDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.camunda.zeebe.test.util.jmh.JMHTestCase;
import io.camunda.zeebe.test.util.junit.JMHTest;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.rules.TemporaryFolder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@Warmup(iterations = 100, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 50, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgs = {
      "-Xmx4g",
      "-Xms4g",
      "-XX:+UnlockDiagnosticVMOptions",
      "-XX:+DebugNonSafepoints",
      "-XX:+AlwaysPreTouch",
//      "-XX:+UseParallelGC"
      //      "-XX:+UseShenandoahGC",
      //      "-XX:+UseZGC",
      //      "-XX:+ZGenerational",
      //      "-Xlog:gc*=debug:file=gc.log",
      //      "-XX:+UnlockCommercialFeatures",
      //      "-XX:StartFlightRecording=disk=true,maxsize=10g,maxage=24h,filename=./recording.jfr",
      //      "-XX:FlightRecorderOptions=repository=./diagnostics/,maxchunksize=50m,stackdepth=1024"
    })
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class ColumnFamilyIterationPerfTest {
  private ColumnFamily<DbLong, DbNil> longKeyColumnFamily;
  private TemporaryFolder temporaryFolder;
  private AutoCloseableRule autoCloseableRule;
  private DbLong columnFamilyKey;
  private ArrayBlockingQueue<Long> deletedKeyStorage;

  @Setup
  public void setup() throws Throwable {
    deletedKeyStorage = new ArrayBlockingQueue<>(1_000_000);
    temporaryFolder = new TemporaryFolder();
    temporaryFolder.create();
    final ZeebeDb<ZbColumnFamilies> zeebeDb =
        DefaultZeebeDbFactory.<ZbColumnFamilies>getDefaultFactory()
            .createDb(temporaryFolder.getRoot());
    autoCloseableRule = new AutoCloseableRule();
    autoCloseableRule.manage(zeebeDb);
    columnFamilyKey = new DbLong();
    longKeyColumnFamily = zeebeDb.createColumnFamily(
            ZbColumnFamilies.TIMER_DUE_DATES,
            zeebeDb.createContext(),
            columnFamilyKey,
            DbNil.INSTANCE);
  }

  @TearDown
  public void tearDown() throws IOException {
    final Path source = temporaryFolder.getRoot().toPath();
    final Path target = new File("rocksdbfiles/").toPath();
    Files.walkFileTree(
        source,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs)
              throws IOException {
            Files.createDirectories(target.resolve(source.relativize(dir).toString()));
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            Files.copy(file, target.resolve(source.relativize(file).toString()), REPLACE_EXISTING);
            return FileVisitResult.CONTINUE;
          }
        });
    autoCloseableRule.after();
  }

  @Benchmark
  public void measureExecutionTime() {
    // create 1000 instances
    for (int i = 0; i < 100; i++) {
      columnFamilyKey.wrapLong(System.nanoTime());
      longKeyColumnFamily.insert(columnFamilyKey, DbNil.INSTANCE);
    }
    // Add them to Queue to delete
    longKeyColumnFamily.whileTrue(
        (key, value) -> {
          final long time = key.getValue();
          if (time / 1_000_000 <= (System.currentTimeMillis() - 4000)) {
            deletedKeyStorage.add(time);
            return true;
          } else {
            return false;
          }
        });
    deletedKeyStorage.forEach(time -> {
      columnFamilyKey.wrapLong(time);
      longKeyColumnFamily.deleteExisting(columnFamilyKey);
    });
  }

  @JMHTest(value = "measureExecutionTime", isAdditionalProfilersEnabled = true)
  void shouldProcessWithinExpectedDeviation(final JMHTestCase testCase) {
    final var referenceScore = 750;

    // when
    final var assertResult = testCase.run();

    // then
    assertResult
        .isMinimumScoreAtLeast((double) referenceScore / 2, 0.25)
        .isAtLeast(referenceScore, 0.25);
  }
}
