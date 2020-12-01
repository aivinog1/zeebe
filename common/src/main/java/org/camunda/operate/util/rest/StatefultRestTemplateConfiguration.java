/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util.rest;

import java.util.function.BiFunction;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StatefultRestTemplateConfiguration {

  @Value("${server.servlet.context-path:/}")
  private String contextPath;

  @Bean
  public BiFunction<String, Integer, StatefulRestTemplate> statefulRestTemplateFactory() {
    return (host, port) -> new StatefulRestTemplate(host, port, contextPath);
  }

}
