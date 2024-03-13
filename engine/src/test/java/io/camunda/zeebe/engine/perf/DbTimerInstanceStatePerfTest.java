/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.perf;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.engine.perf.TestEngine.TestContext;
import io.camunda.zeebe.engine.processing.timer.DueDateTimerChecker;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.state.instance.DbElementInstanceState;
import io.camunda.zeebe.engine.state.instance.DbTimerInstanceState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.engine.state.variable.DbVariableState;
import io.camunda.zeebe.engine.util.client.ProcessInstanceClient;
import io.camunda.zeebe.engine.util.client.TimerClient;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.ActorScheduler.ActorSchedulerBuilder;
import io.camunda.zeebe.scheduler.clock.DefaultActorClock;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.camunda.zeebe.stream.impl.BufferedTaskResultBuilder;
import io.camunda.zeebe.stream.impl.ExtendedProcessingScheduleServiceImpl;
import io.camunda.zeebe.stream.impl.StreamProcessorBuilder;
import io.camunda.zeebe.stream.impl.StreamProcessorContext;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.camunda.zeebe.test.util.jmh.JMHTestCase;
import io.camunda.zeebe.test.util.junit.JMHTest;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.FeatureFlags;
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
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
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
public class DbTimerInstanceStatePerfTest {
  public static final Logger LOG =
      LoggerFactory.getLogger(DbTimerInstanceStatePerfTest.class.getName());
  private static final String PROCESS_LARGE_TIMERS_MESSAGES_BPMN_PROCESS_ID =
      "process-large-timers-messages";

  private long count;
  private TimerClient timerClient;
  private TestEngine.TestContext testContext;
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
    timerClient = singlePartitionEngine.createTimerClient();

    final int maxInstanceCount = 0;
    LOG.info("Starting {} process instances, please hold the line...", maxInstanceCount);
    for (int i = 0; i < maxInstanceCount; i++) {
      timerClient.withDueDate(System.currentTimeMillis() + 3000).withProcessInstanceKey(count).withElementInstanceKey(count).withTargetElementId(String.valueOf(count)).withRepetitions(1).withProcessDefinitionKey(count).createTimer();
      timerClient.triggerTimer();
      count++;
      RecordingExporter.reset();

      if ((i % 10000) == 0) {
        LOG.info("\t{} process instances already started.", i);
        singlePartitionEngine.reset();
      }
    }

    LOG.info("Started {} process instances.", count);
  }

  private TestEngine.TestContext createTestContext() throws IOException {
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
  public Record<?> measureProcessExecutionTime() {
    final Record<TimerRecordValue> timerRecordValueRecord = timerClient.withDueDate(
        System.currentTimeMillis() + 3000).withElementInstanceKey(count).withProcessInstanceKey(count).withTargetElementId(String.valueOf(count)).withRepetitions(1).withProcessDefinitionKey(count).createTimer();
    timerClient.triggerTimer();

    final Record<TimerRecordValue> message = RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withElementInstanceKey(timerRecordValueRecord.getValue().getElementInstanceKey())
        .getFirst();

    count++;
    singlePartitionEngine.reset();
    return message;
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

