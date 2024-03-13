/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.perf;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import io.camunda.zeebe.engine.perf.TestEngine.TestContext;
import io.camunda.zeebe.engine.util.client.ProcessInstanceClient;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.clock.DefaultActorClock;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.camunda.zeebe.test.util.jmh.JMHTestCase;
import io.camunda.zeebe.test.util.junit.JMHTest;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Map;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
      "-XX:+UseParallelGC"
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
public class EngineLargeOnlyTimersPerformanceTest {
  public static final Logger LOG =
      LoggerFactory.getLogger(EngineLargeOnlyTimersPerformanceTest.class.getName());
  private static final String PROCESS_LARGE_TIMERS_MESSAGES =
      "process-large-only-timers";

  private long count;
  private ProcessInstanceClient processInstanceClient;
  private TestContext testContext;
  private TestEngine singlePartitionEngine;
  private TemporaryFolder temporaryFolder;

  @Setup
  public void setup() throws Throwable {
    testContext = createTestContext();

    singlePartitionEngine = TestEngine.createSinglePartitionEngine(testContext);

    setupState(singlePartitionEngine);
  }

  /** Will build up a state for the large state performance test */
  private void setupState(final TestEngine singlePartitionEngine) {

    final ByteArrayOutputStream stream = new ByteArrayOutputStream();
    try (final InputStream bpmnResource =
        EngineLargeOnlyTimersPerformanceTest.class.getResourceAsStream(
            "/only-timer.bpmn")) {
      final byte[] bytes = bpmnResource.readAllBytes();
      try (stream) {
        stream.writeBytes(bytes);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    singlePartitionEngine
        .createDeploymentClient()
        .withXmlResource(stream.toByteArray(), "process-large-only-timers-performance-test.xml")
        .deploy();

    processInstanceClient = singlePartitionEngine.createProcessInstanceClient();

    final int maxInstanceCount = 0;
    LOG.info("Starting {} process instances, please hold the line...", maxInstanceCount);
    for (int i = 0; i < maxInstanceCount; i++) {
      processInstanceClient
          .ofBpmnProcessId(PROCESS_LARGE_TIMERS_MESSAGES)
          .withVariables(Map.of("expireTime", Duration.ofSeconds(3).toString()))
          .create();
      count++;
      RecordingExporter.reset();

      if ((i % 10000) == 0) {
        LOG.info("\t{} process instances already started.", i);
        singlePartitionEngine.reset();
      }
    }

    LOG.info("Started {} process instances.", count);
  }

  private TestContext createTestContext() throws IOException {
    final var autoCloseableRule = new AutoCloseableRule();
    temporaryFolder = new TemporaryFolder();
    temporaryFolder.create();
    LOG.info("Temporary folder for this run: {}", temporaryFolder.getRoot());

    // scheduler
    final var builder =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(1)
            .setIoBoundActorThreadCount(1)
            .setActorClock(new DefaultActorClock());

    final var actorScheduler = builder.build();
    autoCloseableRule.manage(actorScheduler);
    actorScheduler.start();
    return new TestContext(actorScheduler, temporaryFolder, autoCloseableRule);
  }

  @TearDown
  public void tearDown() throws IOException {
    LOG.info("Started {} process instances", count);
    final Path source = new File(temporaryFolder.getRoot(), "stream-1/state/runtime").toPath();
    final Path target = new File("rocksdbfiles/").toPath();
    Files.walkFileTree(source, new SimpleFileVisitor<>() {
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
    testContext.autoCloseableRule().after();
  }

  @Benchmark
  public long measureProcessExecutionTime() {
    final long piKey =
        processInstanceClient
            .ofBpmnProcessId(PROCESS_LARGE_TIMERS_MESSAGES)
            .withVariables(Map.of("expireTime", Duration.ofSeconds(3).toString()))
            .create();

    count++;
    singlePartitionEngine.reset();
    return piKey;
  }

  @JMHTest(value = "measureProcessExecutionTime", isAdditionalProfilersEnabled = true)
  void shouldProcessWithinExpectedDeviation(final JMHTestCase testCase) {
    // given - an expected ops/s score, as measured in CI
    // when running this test locally, you're likely to have a different score
    final var referenceScore = 750;

    // when
    final var assertResult = testCase.run();

    // then
    assertResult
        .isMinimumScoreAtLeast((double) referenceScore / 2, 0.25)
        .isAtLeast(referenceScore, 0.25);
  }
}
