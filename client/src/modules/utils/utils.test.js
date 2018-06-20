import * as utils from './utils';

const {ACTIVE, INCIDENT, COMPLETED, CANCELED} = utils.INSTANCE_STATE;

const active = {state: ACTIVE, incidents: []};
const activeWithIncidents = {
  state: ACTIVE,
  incidents: [
    {
      id: '4295776400',
      errorType: 'IO_MAPPING_ERROR',
      errorMessage:
        'Could not apply output mappings: Task was completed without payload',
      state: ACTIVE,
      activityId: 'taskA'
    }
  ]
};
const completed = {state: COMPLETED, incidents: []};
const canceled = {state: CANCELED, incidents: []};

describe('utils', () => {
  describe('getActiveIncident', () => {
    it('should return null if there is no incident', () => {
      expect(utils.getActiveIncident([])).toBe(null);
    });

    it('should return an object if an instance has incidents', () => {
      expect(utils.getActiveIncident(activeWithIncidents.incidents)).toBe(
        activeWithIncidents.incidents[0]
      );
    });
  });

  describe('getInstanceState', () => {
    it('should return the state for a completed instance', () => {
      const state = utils.getInstanceState(completed);

      expect(state).toEqual(COMPLETED);
    });

    it('should return the state for a canceled instance', () => {
      const state = utils.getInstanceState(canceled);

      expect(state).toEqual(CANCELED);
    });

    it('should return the state for an active, incident free instance', () => {
      const state = utils.getInstanceState(active);

      expect(state).toEqual(ACTIVE);
    });

    it('should return difrent state for an active instance with incidents', () => {
      const state = utils.getInstanceState(activeWithIncidents);

      expect(state).not.toEqual(activeWithIncidents.state);
      expect(state).toEqual(INCIDENT);
    });
  });

  describe('getIncidentMessage', () => {
    it('should return undefined for an instance with no incidents', () => {
      const message = utils.getIncidentMessage({incidents: active.incidents});

      expect(message).toEqual(undefined);
    });

    it('should return a string for an instance with incidents', () => {
      const message = utils.getIncidentMessage({
        incidents: activeWithIncidents.incidents
      });

      expect(message).toBe(activeWithIncidents.incidents[0].errorMessage);
    });
  });
});
