import React, {Component, Fragment} from 'react';
import PropTypes from 'prop-types';

import {isEqual, sortBy, isEmpty} from 'lodash';

import withSharedState from 'modules/components/withSharedState';
import SplitPane from 'modules/components/SplitPane';
import Diagram from 'modules/components/Diagram';
import {DEFAULT_FILTER, PAGE_TITLE} from 'modules/constants';
import {
  fetchWorkflowInstanceBySelection,
  fetchWorkflowInstancesCount,
  fetchGroupedWorkflowInstances,
  fetchWorkflowInstancesStatistics
} from 'modules/api/instances';

import {fetchWorkflowXML} from 'modules/api/diagram';
import {
  parseFilterForRequest,
  getFilterQueryString,
  getFilterWithWorkflowIds,
  getWorkflowByVersion
} from 'modules/utils/filter';

import {getNodesFromXML} from 'modules/utils/bpmn';

import {getSelectionById} from 'modules/utils/selection';

import Header from '../Header';
import ListView from './ListView';
import Filters from './Filters';
import Selections from './Selections';

import {
  parseQueryString,
  getPayload,
  decodeFields,
  getEmptyDiagramMessage
} from './service';
import * as Styled from './styled.js';

class Instances extends Component {
  static propTypes = {
    getStateLocally: PropTypes.func.isRequired,
    storeStateLocally: PropTypes.func.isRequired,
    location: PropTypes.object.isRequired,
    history: PropTypes.object.isRequired
  };

  constructor(props) {
    super(props);
    const {
      filterCount,
      instancesInSelectionsCount,
      rollingSelectionIndex,
      selectionCount,
      selections
    } = props.getStateLocally();

    this.state = {
      activityIds: [],
      filter: {},
      filterCount: filterCount || 0,
      instancesInSelectionsCount: instancesInSelectionsCount || 0,
      openSelection: null,
      rollingSelectionIndex: rollingSelectionIndex || 0,
      selection: {all: false, ids: [], excludeIds: []},
      selectionCount: selectionCount || 0,
      selections: selections || [],
      workflow: {},
      groupedWorkflowInstances: {},
      statistics: []
    };
  }

  async componentDidMount() {
    document.title = PAGE_TITLE.INSTANCES;
    const groupedWorkflows = await fetchGroupedWorkflowInstances();
    this.setGroupedWorkflowInstances(groupedWorkflows);

    //we read and clean the filter values from the url
    await this.cleanFilterByWorkflowData();

    this.setFilterFromUrl();
  }

  async componentDidUpdate(prevProps, prevState) {
    if (prevProps.location.search !== this.props.location.search) {
      //we read clean the filter values from the url
      await this.cleanFilterByWorkflowData();

      this.setFilterFromUrl();

      // fetch new statistics only if filter changes for the same diagram
      // if the workflow changes, statistics are fetched onFlowNodesDetailsReady
      if (
        !isEqual(prevState.filter, this.state.filter) &&
        isEqual(prevState.workflow, this.state.workflow)
      ) {
        this.fetchDiagramStatistics();
      }
    }
  }

  fetchDiagramStatistics = async () => {
    let filter = Object.assign({}, this.state.filter);

    if (isEmpty(filter) || !filter.workflow) {
      return;
    }

    const filterWithWorkflowIds = getFilterWithWorkflowIds(
      filter,
      this.state.groupedWorkflowInstances
    );

    const statistics = await fetchWorkflowInstancesStatistics({
      queries: [parseFilterForRequest(filterWithWorkflowIds)]
    });

    this.setState({statistics});
  };

  cleanFilterByWorkflowData = async () => {
    let {filter} = parseQueryString(this.props.location.search);
    const noWorkflowValues = {
      workflow: '',
      version: '',
      activityId: ''
    };

    // if filter has no workflow data reset all workflow related state
    if (filter && !Boolean(filter.workflow)) {
      this.setState({
        workflow: {},
        activityIds: [],
        statistics: []
      });
    }

    if (filter && (filter.workflow || filter.version || filter.activityId)) {
      let isWorkflowValid =
        filter.workflow && this.state.groupedWorkflowInstances[filter.workflow];
      let isWorkflowIdValid = true;

      if (!isWorkflowValid || (isWorkflowValid && !filter.version)) {
        // stop filter validation process
        // clean workflow information from the filter
        this.setFilterInURL({
          ...filter,
          ...noWorkflowValues
        });
      }

      if (isWorkflowValid) {
        // we have to check workflow & version
        const filterWithWorkflowIds = getFilterWithWorkflowIds(
          filter,
          this.state.groupedWorkflowInstances
        );

        // no valid combination of workflow + version was found
        if (!Boolean(filterWithWorkflowIds.workflowIds)) {
          // clean workflow information from the filter
          this.setFilterInURL({
            ...filter,
            ...noWorkflowValues
          });

          isWorkflowIdValid = false;
        }

        if (isWorkflowIdValid) {
          let activityIds = [];

          if (filter.version === 'all') {
            if (filter.activityId) {
              this.setFilterInURL({...filter, ...{activityId: ''}});
            }
          } else {
            // fetch xml to check validity of activity id
            const xml = await fetchWorkflowXML(
              filterWithWorkflowIds.workflowIds[0]
            );

            // check activity node in xml
            const nodes = await getNodesFromXML(xml);

            if (filter.activityId) {
              //activityId is not valid and we remove it from url
              if (!nodes[filterWithWorkflowIds.activityId]) {
                this.setFilterInURL({...filter, ...{activityId: ''}});
              }
            }

            // we set this.state.activityIds
            // this allows Filter to prefill FlowNode value
            for (let node in nodes) {
              if (nodes[node].$type === 'bpmn:ServiceTask') {
                activityIds.push({
                  value: nodes[node].id,
                  label: nodes[node].name || 'Unnamed task'
                });
              }
            }
          }

          this.setState({
            activityIds: sortBy(activityIds, item => item.label.toLowerCase()),
            workflow: getWorkflowByVersion(
              this.state.groupedWorkflowInstances[filter.workflow],
              filter.version
            )
          });
        }
      }
    }
  };

  setGroupedWorkflowInstances = workflows => {
    const groupedWorkflowInstances = workflows.reduce((obj, value) => {
      obj[value.bpmnProcessId] = {
        ...value
      };

      return obj;
    }, {});

    this.setState({groupedWorkflowInstances});
  };

  handleStateChange = change => {
    this.setState(change);
  };

  addSelectionToList = selection => {
    const {
      rollingSelectionIndex,
      instancesInSelectionsCount,
      selectionCount
    } = this.state;

    const currentSelectionIndex = rollingSelectionIndex + 1;

    // Add Id for each selection
    this.setState(
      prevState => ({
        selections: [
          {
            selectionId: currentSelectionIndex,
            ...selection
          },
          ...prevState.selections
        ],
        rollingSelectionIndex: currentSelectionIndex,
        instancesInSelectionsCount:
          instancesInSelectionsCount + selection.totalCount,
        selectionCount: selectionCount + 1,
        openSelection: currentSelectionIndex,
        selection: {all: false, ids: [], excludeIds: []}
      }),
      () => {
        const {
          selections,
          rollingSelectionIndex,
          instancesInSelectionsCount,
          selectionCount
        } = this.state;

        this.props.storeStateLocally({
          selections,
          rollingSelectionIndex,
          instancesInSelectionsCount,
          selectionCount
        });
      }
    );
  };

  handleAddNewSelection = async () => {
    const payload = getPayload({state: this.state});
    const instancesDetails = await fetchWorkflowInstanceBySelection(payload);
    this.addSelectionToList({...payload, ...instancesDetails});
  };

  handleAddToSelectionById = async selectionId => {
    const {selections, instancesInSelectionsCount} = this.state;
    const selectiondata = getSelectionById(selections, selectionId);
    const payload = getPayload({state: this.state, selectionId});
    const previousTotalCount = selections[selectiondata.index].totalCount;

    const instancesDetails = await fetchWorkflowInstanceBySelection(payload);

    const newSelection = {
      ...selections[selectiondata.index],
      ...payload,
      ...instancesDetails
    };

    selections[selectiondata.index] = newSelection;

    this.setState(
      {
        selections,
        instancesInSelectionsCount:
          instancesInSelectionsCount -
          previousTotalCount +
          newSelection.totalCount,
        selection: {all: false, ids: [], excludeIds: []},
        openSelection: selectionId
      },
      () => {
        const {instancesInSelectionsCount, selections} = this.state;

        this.props.storeStateLocally({
          instancesInSelectionsCount,
          selections
        });
      }
    );
  };

  setFilterFromUrl = () => {
    let {filter} = parseQueryString(this.props.location.search);

    // filter from URL was missing or invalid
    if (!filter) {
      // set default filter selection
      filter = DEFAULT_FILTER;
      this.setFilterInURL(filter);
    }

    // write current filter selection to local storage
    this.props.storeStateLocally({filter: filter});

    this.setState({filter: {...decodeFields(filter)}}, () => {
      this.handleFilterCount();
    });
  };

  handleFilterCount = async () => {
    const filterCount = await fetchWorkflowInstancesCount(
      parseFilterForRequest(
        getFilterWithWorkflowIds(
          this.state.filter,
          this.state.groupedWorkflowInstances
        )
      )
    );

    this.setState({
      filterCount
    });

    // save filterCount to localStorage
    this.props.storeStateLocally({filterCount});
  };

  handleFilterChange = async newFilter => {
    let hasNewValue = false;

    for (let key in newFilter) {
      if (this.state.filter[key] !== newFilter[key]) {
        hasNewValue = true;
        break;
      }
    }

    // only change the URL if a new value for a field is provided
    if (hasNewValue) {
      const filter = {...this.state.filter, ...newFilter};
      this.setFilterInURL(filter);
      this.resetSelections();
    }
  };

  setFilterInURL = filter => {
    this.props.history.push({
      pathname: this.props.location.pathname,
      search: getFilterQueryString(filter)
    });
  };

  handleFilterReset = () => {
    this.setFilterInURL(DEFAULT_FILTER);

    // reset diagram
    this.setState({
      workflow: null
    });

    // reset filter in local storage
    this.props.storeStateLocally({filter: DEFAULT_FILTER});

    this.resetSelections();
  };

  resetSelections = () => {
    this.setState({selection: {all: false, ids: [], excludeIds: []}});
  };

  handleFlowNodeSelection = flowNodeId => {
    this.handleFilterChange({activityId: flowNodeId});
  };

  render() {
    const currentWorkflow = this.state.groupedWorkflowInstances[
      this.state.filter.workflow
    ];
    const workflowName = !isEmpty(currentWorkflow)
      ? currentWorkflow.name || currentWorkflow.id
      : 'Workflow';

    return (
      <Fragment>
        <Header
          active="instances"
          filter={this.state.filter}
          filterCount={this.state.filterCount}
          instancesInSelectionsCount={this.state.instancesInSelectionsCount}
          selectionCount={this.state.selectionCount}
        />
        <Styled.Instances>
          <Styled.Content>
            <Styled.Filters>
              <Filters
                filter={this.state.filter}
                filterCount={this.state.filterCount}
                activityIds={this.state.activityIds}
                onFilterReset={this.handleFilterReset}
                onFilterChange={this.handleFilterChange}
                groupedWorkflowInstances={this.state.groupedWorkflowInstances}
              />
            </Styled.Filters>

            <Styled.Center>
              <Styled.Pane>
                <Styled.PaneHeader>{workflowName}</Styled.PaneHeader>
                <SplitPane.Pane.Body>
                  {!this.state.filter.workflow && (
                    <Styled.EmptyMessageWrapper>
                      <Styled.DiagramEmptyMessage
                        message={`There is no Workflow selected.\n
To see a diagram, select a Workflow in the Filters panel.`}
                        data-test="data-test-noWorkflowMessage"
                      />
                    </Styled.EmptyMessageWrapper>
                  )}
                  {this.state.filter.version === 'all' && (
                    <Styled.EmptyMessageWrapper>
                      <Styled.DiagramEmptyMessage
                        message={getEmptyDiagramMessage(workflowName)}
                        data-test="data-test-allVersionsMessage"
                      />
                    </Styled.EmptyMessageWrapper>
                  )}
                  {!isEmpty(this.state.workflow) && (
                    <Diagram
                      workflowId={this.state.workflow.id}
                      onFlowNodesDetailsReady={this.fetchDiagramStatistics}
                      flowNodesStatisticsOverlay={this.state.statistics}
                      selectedFlowNode={this.state.filter.activityId}
                      onFlowNodeSelected={this.handleFlowNodeSelection}
                      selectableFlowNodes={this.state.activityIds.map(
                        item => item.value
                      )}
                    />
                  )}
                </SplitPane.Pane.Body>
              </Styled.Pane>

              <ListView
                openSelection={this.state.openSelection}
                filterCount={this.state.filterCount}
                onUpdateSelection={change => {
                  this.handleStateChange({selection: {...change}});
                }}
                selection={this.state.selection}
                selections={this.state.selections}
                filter={getFilterWithWorkflowIds(
                  this.state.filter,
                  this.state.groupedWorkflowInstances
                )}
                errorMessage={this.state.errorMessage}
                onAddNewSelection={this.handleAddNewSelection}
                onAddToSpecificSelection={this.handleAddToSelectionById}
                onAddToOpenSelection={() =>
                  this.handleAddToSelectionById(this.state.openSelection)
                }
              />
            </Styled.Center>
          </Styled.Content>
          <Selections
            openSelection={this.state.openSelection}
            selections={this.state.selections}
            rollingSelectionIndex={this.state.rollingSelectionIndex}
            selectionCount={this.state.selectionCount}
            instancesInSelectionsCount={this.state.instancesInSelectionsCount}
            filter={getFilterWithWorkflowIds(
              this.state.filter,
              this.state.groupedWorkflowInstances
            )}
            storeStateLocally={this.props.storeStateLocally}
            onStateChange={this.handleStateChange}
          />
        </Styled.Instances>
      </Fragment>
    );
  }
}

const WrappedInstances = withSharedState(Instances);
WrappedInstances.WrappedComponent = Instances;

export default WrappedInstances;
