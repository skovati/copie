/**
 * GraphQL Query and Mutation strings.
 */
const gql = {
  CREATE_MISSION_MODEL: `#graphql
    mutation CreateMissionModel($model: mission_model_insert_input!) {
      insert_mission_model_one(object: $model) {
        id
      }
    }
  `,

  DELETE_MISSION_MODEL: `#graphql
    mutation DeleteModel($id: Int!) {
      delete_mission_model_by_pk(id: $id) {
        id
      }
    }
  `,

  GET_RESOURCE_TYPES: `#graphql
    query GetResourceTypes($missionModelId: Int!) {
      resource_type(where: {model_id: {_eq: $missionModelId}}, order_by: {name: asc}) {
        name
        schema
      }
    }
  `,

  GET_ACTIVITY_TYPES: `#graphql
    query GetActivityTypes($missionModelId: Int!) {
      activity_type(where: {model_id: {_eq: $missionModelId}}, order_by: {name: asc}) {
        name
        parameters
      }
    }
  `,

  GET_TOPIC_EVENTS:`#graphql
  query GetTopicsEvents($datasetId: Int!) {
    topic(where: {dataset_id: {_eq: $datasetId}}) {
      name
      value_schema
      events {
        causal_time
        real_time
        topic_index
        transaction_index
        value
      }
    }
  }
  `,

  GET_PROFILES:`#graphql
    query GetProfiles($datasetId: Int!){
      profile(where: {dataset_id: {_eq: $datasetId}}) {
        name
        profile_segments {
          dynamics
          is_gap
          start_offset
        }
    }
  }
  `,

  SCHEDULE: `#graphql
    query Schedule($specificationId: Int!) {
      schedule(specificationId: $specificationId){
        reason
        status
        analysisId
        datasetId
      }
    }
  `,

  SIMULATE: `#graphql
  query Simulate($plan_id: Int!) {
    simulate(planId: $plan_id){
      status
      reason
      simulationDatasetId
    }
  }`,

  CREATE_PLAN: `#graphql
    mutation CreatePlan($plan: plan_insert_input!) {
      insert_plan_one(object: $plan) {
        id
        revision
      }
    }
  `,

  CREATE_ACTIVITY_DIRECTIVE: `#graphql
    mutation CreateActivityDirective($activityDirectiveInsertInput: activity_directive_insert_input!) {
      createActivityDirective: insert_activity_directive_one(object: $activityDirectiveInsertInput) {
        id
      }
    }
  `,

  CREATE_SCHEDULING_GOAL: `#graphql
    mutation CreateSchedulingGoal($goal: scheduling_goal_insert_input!) {
      insert_scheduling_goal_one(object: $goal) {
        author
        created_date
        definition
        description
        id
        last_modified_by
        model_id
        modified_date
        name
        revision
      }
    }
  `,

  INSERT_SCHEDULING_SPECIFICATION: `#graphql
  mutation MakeSchedulingSpec($scheduling_spec: scheduling_specification_insert_input!) {
  insert_scheduling_specification_one(object: $scheduling_spec) {
    id
  }
  }`,

  GET_PLAN_REVISION: `#graphql
    query GetPlanRevision($id: Int!) {
      plan: plan_by_pk(id: $id) {
        revision
      }
    }
  `,

  GET_SIMULATION_DATASET: `#graphql
  query GetSimulationDataset($id: Int!) {
    simulationDataset: simulation_dataset_by_pk(id: $id) {
      canceled
      simulation_start_time
      simulation_end_time
      simulated_activities {
        activity_directive { id }
        duration
        start_time
        start_offset
      }
    }
  }
  `,

  GET_SIMULATION_DATASET_BY_DATASET_ID: `#graphql
    query GetSimulationDataset($id: Int!) {
      simulation_dataset(where: {dataset_id: {_eq: $id}}) {
        canceled
        simulation_start_time
        simulation_end_time
        simulated_activities {
          activity_directive { id }
          duration
          start_time
          start_offset
        }
      }
    }
  `,

  CREATE_SCHEDULING_SPEC_GOAL: `#graphql
    mutation CreateSchedulingSpecGoal($spec_goal: scheduling_specification_goals_insert_input!) {
      insert_scheduling_specification_goals_one(object: $spec_goal) {
        goal_id
        priority
        specification_id
      }
    }
  `,

  GET_SCHEDULING_DSL_TYPESCRIPT: `#graphql
    query GetSchedulingDslTypeScript($missionModelId: Int!) {
      schedulingDslTypescript(missionModelId: $missionModelId) {
        reason
        status
        typescriptFiles {
          filePath
          content
        }
      }
    }
  `,

  DELETE_PLAN: `#graphql
    mutation DeletePlan($id: Int!) {
      deletePlan: delete_plan_by_pk(id: $id) {
        id
      }
      deleteSchedulingSpec: delete_scheduling_specification(where: { plan_id: { _eq: $id } }) {
        returning {
          id
        }
      }
      deleteSimulation: delete_simulation(where: { plan_id: { _eq: $id } }) {
        returning {
          id
        }
      }
    }
  `,

  GET_PLAN: `#graphql
    query GetPlan($id: Int!) {
      plan: plan_by_pk(id: $id) {
        activity_directives {
          arguments
          id
          startOffset: start_offset
          type
        }
        constraints {
          definition
          description
          id
          model_id
          name
          plan_id
        }
        duration
        id
        model: mission_model {
          activityTypes: activity_types {
            name
            parameters
          }
          constraints {
            definition
            description
            id
            model_id
            name
            plan_id
          }
          id
          parameters {
            parameters
          }
        }
        name
        revision
        scheduling_specifications {
          id
        }
        simulations {
          arguments
          id
          simulation_start_time
          simulation_end_time
          template: simulation_template {
            arguments
            description
            id
          }
        }
        startTime: start_time
      }
    }
  `,

  INSERT_SIMULATION_TEMPLATE: `#graphql
    mutation CreateSimulationTemplate($simulationTemplateInsertInput: simulation_template_insert_input!) {
      insert_simulation_template_one(object: $simulationTemplateInsertInput) {
        id
      }
    }
  `,

  ASSIGN_TEMPLATE_TO_SIMULATION: `#graphql
    mutation AssignTemplateToSimulation($simulation_id: Int!, $simulation_template_id: Int!) {
      update_simulation_by_pk(pk_columns: {id: $simulation_id}, _set: {simulation_template_id: $simulation_template_id}) {
        simulation_template_id
      }
    }
  `,

  GET_SIMULATION_ID: `#graphql
    query getSimulationId($plan_id: Int!) {
      simulation: simulation(where: {plan_id: {_eq: $plan_id}}) {
        id
      }
    }
  `,

  UPDATE_SIMULATION_BOUNDS: `#graphql
    mutation updateSimulationBounds($plan_id: Int!, $simulation_start_time: timestamptz!, $simulation_end_time: timestamptz!) {
      update_simulation(where: {plan_id: {_eq: $plan_id}},
      _set: {
        simulation_start_time: $simulation_start_time,
        simulation_end_time: $simulation_end_time}) {
        affected_rows
       }
    }
  `,

  ADD_EXTERNAL_DATASET: `#graphql
    mutation addExternalDataset($plan_id: Int!, $dataset_start: String!, $profile_set: ProfileSet!) {
      addExternalDataset(
        planId: $plan_id
        datasetStart: $dataset_start
        profileSet: $profile_set
      ) {
        datasetId
      }
    }
  `,

  EXTEND_EXTERNAL_DATASET: `#graphql
  mutation extendExternalDataset($dataset_id: Int!, $profile_set: ProfileSet!) {
    extendExternalDataset(
      datasetId: $dataset_id
      profileSet: $profile_set
    ) {
      datasetId
    }
  }
  `,

  GET_EXTERNAL_DATASET: `#graphql
    query getExtProfile($plan_id: Int!, $dataset_id: Int!) {
      plan_dataset_by_pk(plan_id:$plan_id, dataset_id:$dataset_id) {
        offset_from_plan_start
        dataset {
          profiles(distinct_on:[], order_by: { name: asc }) {
            profile_segments(distinct_on: []) {
              start_offset
              dynamics
            }
          }
        }
      }
    }
  `,

  DELETE_EXTERNAL_DATASET: `#graphql
  mutation deleteExtProfile($plan_id: Int!, $dataset_id: Int!) {
    delete_plan_dataset_by_pk(plan_id:$plan_id, dataset_id:$dataset_id) {
      dataset_id
    }
  }
  `
};

export default gql;
