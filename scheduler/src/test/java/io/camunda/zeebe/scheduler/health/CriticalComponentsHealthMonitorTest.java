/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.scheduler.health;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthIssue;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.health.HealthStatus;
import io.opentelemetry.api.OpenTelemetry;
import java.util.HashSet;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.LoggerFactory;

public class CriticalComponentsHealthMonitorTest {

  @Rule public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();
  private CriticalComponentsHealthMonitor monitor;
  private ActorControl actorControl;

  @Before
  public void setup() {
    final Actor testActor =
        new Actor(OpenTelemetry.noop()) {
          @Override
          public String getName() {
            return "test-actor";
          }

          @Override
          protected void onActorStarting() {
            monitor =
                new CriticalComponentsHealthMonitor(
                    "TestMonitor", actor, LoggerFactory.getLogger("test"));
            actorControl = actor;
          }

          @Override
          protected void onActorStarted() {
            monitor.startMonitoring();
          }
        };
    actorSchedulerRule.submitActor(testActor).join();
  }

  @Test
  public void shouldMonitorComponent() {
    // given
    final ControllableComponent component = new ControllableComponent();
    monitor.registerComponent("test", component);

    // when
    waitUntilAllDone();
    assertThat(monitor.getHealthReport().getStatus()).isEqualTo(HealthStatus.HEALTHY);

    component.setUnhealthy();
    waitUntilAllDone();

    // then
    assertThat(monitor.getHealthReport().getStatus()).isEqualTo(HealthStatus.UNHEALTHY);
  }

  @Test
  public void shouldRecover() {
    // given
    final ControllableComponent component = new ControllableComponent();
    monitor.registerComponent("test", component);
    waitUntilAllDone();
    component.setUnhealthy();
    waitUntilAllDone();
    assertThat(monitor.getHealthReport().getStatus()).isEqualTo(HealthStatus.UNHEALTHY);

    // when
    component.setHealthy();
    waitUntilAllDone();

    // then
    assertThat(monitor.getHealthReport().getStatus()).isEqualTo(HealthStatus.HEALTHY);
  }

  @Test
  public void shouldMonitorMultipleComponent() {
    // given
    final ControllableComponent component1 = new ControllableComponent();
    final ControllableComponent component2 = new ControllableComponent();

    monitor.registerComponent("test1", component1);
    monitor.registerComponent("test2", component2);

    waitUntilAllDone();
    assertThat(monitor.getHealthReport().getStatus()).isEqualTo(HealthStatus.HEALTHY);

    // when
    component2.setUnhealthy();
    waitUntilAllDone();

    // then
    assertThat(monitor.getHealthReport().getStatus()).isEqualTo(HealthStatus.UNHEALTHY);

    // when
    component2.setHealthy();
    component1.setUnhealthy();
    waitUntilAllDone();

    // then
    assertThat(monitor.getHealthReport().getStatus()).isEqualTo(HealthStatus.UNHEALTHY);

    // when
    component1.setHealthy();
    waitUntilAllDone();

    // then
    assertThat(monitor.getHealthReport().getStatus()).isEqualTo(HealthStatus.HEALTHY);
  }

  @Test
  public void shouldRemoveComponent() {
    // given
    final ControllableComponent component = new ControllableComponent();
    monitor.registerComponent("test", component);
    Awaitility.await().until(() -> monitor.getHealthReport().getStatus() == HealthStatus.HEALTHY);

    // when
    monitor.removeComponent("test");
    waitUntilAllDone();
    component.setUnhealthy();
    waitUntilAllDone();

    // then
    assertThat(monitor.getHealthReport().getStatus()).isEqualTo(HealthStatus.HEALTHY);
  }

  @Test
  public void shouldMonitorComponentDeath() {
    // given
    final ControllableComponent component1 = new ControllableComponent();
    final ControllableComponent component2 = new ControllableComponent();

    monitor.registerComponent("comp1", component1);
    monitor.registerComponent("comp2", component2);
    waitUntilAllDone();

    // when/then
    component1.setUnhealthy();
    component2.setDead();
    waitUntilAllDone();
    assertThat(monitor.getHealthReport().getStatus()).isEqualTo(HealthStatus.DEAD);

    // when/then
    component1.setHealthy();
    waitUntilAllDone();
    assertThat(monitor.getHealthReport().getStatus()).isEqualTo(HealthStatus.DEAD);
  }

  @Test
  public void shouldTrackRootIssue() {
    // given
    final var issue = HealthIssue.of(new IllegalStateException());
    final ControllableComponent component = new ControllableComponent();
    monitor.registerComponent("component", component);
    waitUntilAllDone();

    // when
    component.setDead(issue);
    waitUntilAllDone();

    // then
    assertThat(monitor.getHealthReport().getIssue().cause().getIssue()).isEqualTo(issue);
  }

  private void waitUntilAllDone() {
    actorControl.call(() -> null).join();
  }

  private static class ControllableComponent implements HealthMonitorable {
    private final Set<FailureListener> failureListeners = new HashSet<>();
    private volatile HealthReport healthReport = HealthReport.healthy(this);

    @Override
    public HealthReport getHealthReport() {
      return healthReport;
    }

    @Override
    public void addFailureListener(final FailureListener failureListener) {
      failureListeners.add(failureListener);
    }

    @Override
    public void removeFailureListener(final FailureListener failureListener) {
      failureListeners.remove(failureListener);
    }

    void setHealthy() {
      if (healthReport.getStatus() != HealthStatus.HEALTHY) {
        failureListeners.forEach(FailureListener::onRecovered);
        healthReport = HealthReport.healthy(this);
      }
    }

    void setUnhealthy() {
      if (healthReport.getStatus() != HealthStatus.UNHEALTHY) {
        healthReport = HealthReport.unhealthy(this).withMessage("manually set to status unhealthy");
        failureListeners.forEach((l) -> l.onFailure(healthReport));
      }
    }

    void setDead() {
      setDead(HealthIssue.of("manually set to status dead"));
    }

    void setDead(final HealthIssue issue) {
      if (healthReport.getStatus() != HealthStatus.DEAD) {
        healthReport = HealthReport.dead(this).withIssue(issue);
        failureListeners.forEach((l) -> l.onUnrecoverableFailure(healthReport));
      }
    }
  }
}
