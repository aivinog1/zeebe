/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rest} from 'msw';
import {screen, waitFor} from '@testing-library/react';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {modificationsStore} from 'modules/stores/modifications';
import {mockServer} from 'modules/mock-server/node';
import {open} from 'modules/mocks/diagrams';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {initializeStores, renderPopover} from './mocks';

describe('Modification Dropdown - Multi Scopes', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(open('multipleInstanceSubProcess.bpmn')))
      )
    );
  });

  afterEach(() => {
    flowNodeSelectionStore.reset();
    processInstanceDetailsStore.reset();
    modificationsStore.reset();
    processInstanceDetailsStatisticsStore.reset();
    processInstanceDetailsDiagramStore.reset();
  });

  it('should support add modification for task with multiple scopes', async () => {
    mockServer.use(
      rest.get(
        'http://localhost/api/process-instances/:processId/statistics',
        (_, res, ctx) =>
          res.once(
            ctx.json([
              {
                activityId: 'OuterSubProcess',
                active: 1,
                incidents: 0,
              },
              {
                activityId: 'InnerSubProcess',
                active: 1,
                incidents: 0,
              },
              {
                activityId: 'TaskB',
                active: 10,
                incidents: 0,
              },
            ])
          )
      )
    );

    initializeStores();
    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel
      ).not.toBeNull()
    );
    modificationsStore.enableModificationMode();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'TaskB',
    });

    expect(screen.getByText(/Flow Node Modifications/)).toBeInTheDocument();
    expect(await screen.findByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.getByText(/Add/)).toBeInTheDocument();
  });

  it('should not support add modification for task with multiple inner parent scopes', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:processId/statistics', (_, res, ctx) =>
        res.once(
          ctx.json([
            {
              activityId: 'OuterSubProcess',
              active: 1,
              incidents: 0,
            },
            {
              activityId: 'InnerSubProcess',
              active: 10,
              incidents: 0,
            },
            {
              activityId: 'TaskB',
              active: 1,
              incidents: 0,
            },
          ])
        )
      )
    );

    initializeStores();
    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel
      ).not.toBeNull()
    );
    modificationsStore.enableModificationMode();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'TaskB',
    });

    expect(screen.getByText(/Flow Node Modifications/)).toBeInTheDocument();
    expect(await screen.findByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
  });

  it('should not support add modification for task with multiple outer parent scopes', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:processId/statistics', (_, res, ctx) =>
        res.once(
          ctx.json([
            {
              activityId: 'OuterSubProcess',
              active: 10,
              incidents: 0,
            },
            {
              activityId: 'InnerSubProcess',
              active: 1,
              incidents: 0,
            },
            {
              activityId: 'TaskB',
              active: 1,
              incidents: 0,
            },
          ])
        )
      )
    );

    initializeStores();
    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel
      ).not.toBeNull()
    );
    modificationsStore.enableModificationMode();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'TaskB',
    });

    expect(screen.getByText(/Flow Node Modifications/)).toBeInTheDocument();
    expect(await screen.findByText(/Cancel/)).toBeInTheDocument();
    expect(screen.getByText(/Move/)).toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
  });

  it('should render no modifications available', async () => {
    mockServer.use(
      rest.get(
        'http://localhost/api/process-instances/:processId/statistics',
        (_, res, ctx) =>
          res.once(
            ctx.json([
              {
                activityId: 'OuterSubProcess',
                active: 1,
                incidents: 0,
              },
              {
                activityId: 'InnerSubProcess',
                active: 10,
                incidents: 0,
              },
              {
                activityId: 'TaskB',
                active: 1,
                incidents: 0,
              },
            ])
          )
      )
    );

    initializeStores();
    renderPopover();

    await waitFor(() =>
      expect(
        processInstanceDetailsDiagramStore.state.diagramModel
      ).not.toBeNull()
    );
    modificationsStore.enableModificationMode();

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'CANCEL_TOKEN',
        flowNode: {id: 'TaskB', name: 'Task B'},
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
      },
    });

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'TaskB',
    });

    expect(screen.getByText(/Flow Node Modifications/)).toBeInTheDocument();
    expect(
      await screen.findByText(/No modifications available/)
    ).toBeInTheDocument();
    expect(screen.queryByText(/Cancel/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Move/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Add/)).not.toBeInTheDocument();
  });
});
