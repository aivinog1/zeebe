/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.elasticsearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.matchAll;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

@Component
@Conditional(OpensearchCondition.class)
public class TestOpenSearchRepository implements TestSearchRepository {
  @Autowired
  private RichOpenSearchClient richOpenSearchClient;

  @Override
  public <R> List<R> searchAll(String index, Class<R> clazz) throws IOException {
    var requestBuilder = searchRequestBuilder(index).query(matchAll());
    return richOpenSearchClient.doc().searchValues(requestBuilder, clazz);
  }
}
