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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.clock.DefaultActorClock;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.camunda.zeebe.test.util.jmh.JMHTestCase;
import io.camunda.zeebe.test.util.junit.JMHTest;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
      "-XX:+UseShenandoahGC",
    })
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(org.openjdk.jmh.annotations.Scope.Benchmark)
public class EngineLargeStatePerformanceTest {
  public static final Logger LOG =
      LoggerFactory.getLogger(EngineLargeStatePerformanceTest.class.getName());

  private long count;
  private ProcessInstanceClient processInstanceClient;
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
    singlePartitionEngine
        .createDeploymentClient()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", (t) -> t.zeebeJobType("task").done())
                .endEvent()
                .done())
        .deploy();

    processInstanceClient = singlePartitionEngine.createProcessInstanceClient();

    final int maxInstanceCount = 200_000;
    LOG.info("Starting {} process instances, please hold the line...", maxInstanceCount);
    for (int i = 0; i < maxInstanceCount; i++) {
      processInstanceClient.ofBpmnProcessId("process").create();
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
    final File rocksdbLog = new File(temporaryFolder.getRoot(), "stream-1/state/runtime/LOG");
    final File rocksdbLogDest = new File("ROCKSDBLOG");
    Files.copy(rocksdbLog.toPath(), rocksdbLogDest.toPath(), REPLACE_EXISTING);
    final File optionsFile =
        new File(temporaryFolder.getRoot(), "stream-1/state/runtime/OPTIONS-000007");
    final File optionsDestFile = new File("OPTIONS");
    Files.copy(optionsFile.toPath(), optionsDestFile.toPath(), REPLACE_EXISTING);
    testContext.autoCloseableRule().after();
  }

  @Benchmark
  public Record<?> measureProcessExecutionTime() {
    final long piKey = processInstanceClient.ofBpmnProcessId("process").create();

    final Record<JobRecordValue> task =
        RecordingExporter.jobRecords()
            .withIntent(JobIntent.CREATED)
            .withType("task")
            .withProcessInstanceKey(piKey)
            .getFirst();

    count++;
    singlePartitionEngine.reset();
    return task;
  }

  @JMHTest(value = "measureProcessExecutionTime")
  void shouldProcessWithinExpectedDeviation(final JMHTestCase testCase) {
    // given - an expected ops/s score, as measured in CI
    // when running this test locally, you're likely to have a different score
    final var referenceScore = 1000;

    // when
    final var assertResult = testCase.run();

    // then
    assertResult
        .isMinimumScoreAtLeast((double) referenceScore / 2, 0.25)
        .isAtLeast(referenceScore, 0.25);
  }
}
