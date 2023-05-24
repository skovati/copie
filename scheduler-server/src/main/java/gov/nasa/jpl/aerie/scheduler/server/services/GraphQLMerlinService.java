package gov.nasa.jpl.aerie.scheduler.server.services;

import com.impossibl.postgres.api.data.Interval;
import gov.nasa.jpl.aerie.json.BasicParsers;
import gov.nasa.jpl.aerie.json.JsonParser;
import gov.nasa.jpl.aerie.merlin.driver.ActivityDirective;
import gov.nasa.jpl.aerie.merlin.driver.ActivityDirectiveId;
import gov.nasa.jpl.aerie.merlin.driver.SimulatedActivity;
import gov.nasa.jpl.aerie.merlin.driver.SimulatedActivityId;
import gov.nasa.jpl.aerie.merlin.driver.SimulationResults;
import gov.nasa.jpl.aerie.merlin.driver.UnfinishedActivity;
import gov.nasa.jpl.aerie.merlin.driver.engine.ProfileSegment;
import gov.nasa.jpl.aerie.merlin.driver.timeline.EventGraph;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.DurationType;
import gov.nasa.jpl.aerie.merlin.protocol.types.InstantiationException;
import gov.nasa.jpl.aerie.merlin.protocol.types.RealDynamics;
import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;
import gov.nasa.jpl.aerie.merlin.protocol.types.ValueSchema;
import gov.nasa.jpl.aerie.scheduler.model.Plan;
import gov.nasa.jpl.aerie.scheduler.model.PlanningHorizon;
import gov.nasa.jpl.aerie.scheduler.model.Problem;
import gov.nasa.jpl.aerie.scheduler.model.SchedulingActivityDirective;
import gov.nasa.jpl.aerie.scheduler.model.SchedulingActivityDirectiveId;
import gov.nasa.jpl.aerie.scheduler.server.exceptions.NoSuchMissionModelException;
import gov.nasa.jpl.aerie.scheduler.server.exceptions.NoSuchPlanException;
import gov.nasa.jpl.aerie.scheduler.server.http.EventGraphFlattener;
import gov.nasa.jpl.aerie.scheduler.server.http.InvalidEntityException;
import gov.nasa.jpl.aerie.scheduler.server.http.InvalidJsonException;
import gov.nasa.jpl.aerie.scheduler.server.models.ActivityAttributesRecord;
import gov.nasa.jpl.aerie.scheduler.server.models.DatasetId;
import gov.nasa.jpl.aerie.scheduler.server.models.GoalId;
import gov.nasa.jpl.aerie.scheduler.server.models.MerlinPlan;
import gov.nasa.jpl.aerie.scheduler.server.models.MissionModelId;
import gov.nasa.jpl.aerie.scheduler.server.models.PlanId;
import gov.nasa.jpl.aerie.scheduler.server.models.PlanMetadata;
import gov.nasa.jpl.aerie.scheduler.server.models.ProfileSet;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static gov.nasa.jpl.aerie.merlin.driver.json.SerializedValueJsonParser.serializedValueP;
import static gov.nasa.jpl.aerie.merlin.driver.json.ValueSchemaJsonParser.valueSchemaP;
import static gov.nasa.jpl.aerie.merlin.protocol.types.Duration.MICROSECOND;
import static gov.nasa.jpl.aerie.scheduler.server.graphql.GraphQLParsers.activityAttributesP;
import static gov.nasa.jpl.aerie.scheduler.server.graphql.GraphQLParsers.discreteProfileTypeP;
import static gov.nasa.jpl.aerie.scheduler.server.graphql.GraphQLParsers.parseGraphQLInterval;
import static gov.nasa.jpl.aerie.scheduler.server.graphql.GraphQLParsers.parseGraphQLTimestamp;
import static gov.nasa.jpl.aerie.scheduler.server.graphql.GraphQLParsers.realDynamicsP;
import static gov.nasa.jpl.aerie.scheduler.server.graphql.GraphQLParsers.realProfileTypeP;
import static gov.nasa.jpl.aerie.scheduler.server.graphql.GraphQLParsers.simulationArgumentsP;

/**
 * {@inheritDoc}
 *
 * @param merlinGraphqlURI endpoint of the merlin graphql service that should be used to access all plan data
 */
public record GraphQLMerlinService(URI merlinGraphqlURI, String hasuraGraphQlAdminSecret) implements PlanService.OwnerRole,
    MissionModelService
{

  /**
   * timeout for http graphql requests issued to aerie
   */
  private static final java.time.Duration httpTimeout = java.time.Duration.ofSeconds(60);

  /**
   * dispatch the given graphql request to aerie and collect the results
   *
   * absorbs any io errors and returns an empty response object in order to keep exception
   * signature of callers cleanly matching the MerlinService interface
   *
   * @param gqlStr the graphQL query or mutation to send to aerie
   * @return the json response returned by aerie, or an empty optional in case of io errors
   */
  protected Optional<JsonObject> postRequest(final String gqlStr) throws IOException, PlanServiceException {
    try {
      //TODO: (mem optimization) use streams here to avoid several copies of strings
      final var reqBody = Json.createObjectBuilder().add("query", gqlStr).build();
      final var httpReq = HttpRequest
          .newBuilder().uri(merlinGraphqlURI).timeout(httpTimeout)
          .header("Content-Type", "application/json")
          .header("Accept", "application/json")
          .header("Origin", merlinGraphqlURI.toString())
          .header("x-hasura-admin-secret", hasuraGraphQlAdminSecret)
          .POST(HttpRequest.BodyPublishers.ofString(reqBody.toString()))
          .build();
      //TODO: (net optimization) gzip compress the request body if large enough (eg for createAllActs)
      final var httpResp = HttpClient
          .newHttpClient().send(httpReq, HttpResponse.BodyHandlers.ofInputStream());
      if (httpResp.statusCode() != 200) {
        //TODO: how severely to error out if aerie cannot be reached or has a 500 error or json is garbled etc etc?
        return Optional.empty();
      }
      final var respBody = Json.createReader(httpResp.body()).readObject();
      if (respBody.containsKey("errors")) {
        throw new PlanServiceException(respBody.toString());
      }
      return Optional.of(respBody);
    } catch (final InterruptedException e) {
      //TODO: maybe retry if interrupted? but depends on semantics (eg don't duplicate mutation if not idempotent)
      return Optional.empty();
    } catch (final JsonException e) { // or also JsonParsingException
      throw new IOException("json parse error on graphql response:" + e.getMessage(), e);
    }
  }

  protected Optional<JsonObject> postRequest(final String query, final JsonObject variables) throws IOException, PlanServiceException {
    try {
      //TODO: (mem optimization) use streams here to avoid several copies of strings
      final var reqBody = Json
          .createObjectBuilder()
          .add("query", query)
          .add("variables", variables)
          .build();
      final var httpReq = HttpRequest
          .newBuilder().uri(merlinGraphqlURI).timeout(httpTimeout)
          .header("Content-Type", "application/json")
          .header("Accept", "application/json")
          .header("Origin", merlinGraphqlURI.toString())
          .header("x-hasura-admin-secret", hasuraGraphQlAdminSecret)
          .POST(HttpRequest.BodyPublishers.ofString(reqBody.toString()))
          .build();
      //TODO: (net optimization) gzip compress the request body if large enough (eg for createAllActs)
      final var httpResp = HttpClient
          .newHttpClient().send(httpReq, HttpResponse.BodyHandlers.ofInputStream());
      if (httpResp.statusCode() != 200) {
        //TODO: how severely to error out if aerie cannot be reached or has a 500 error or json is garbled etc etc?
        return Optional.empty();
      }
      final var respBody = Json.createReader(httpResp.body()).readObject();
      if (respBody.containsKey("errors")) {
        throw new PlanServiceException(respBody.toString());
      }
      return Optional.of(respBody);
    } catch (final InterruptedException e) {
      //TODO: maybe retry if interrupted? but depends on semantics (eg don't duplicate mutation if not idempotent)
      return Optional.empty();
    } catch (final JsonException e) { // or also JsonParsingException
      throw new IOException("json parse error on graphql response:" + e.getMessage(), e);
    }
  }

  //TODO: maybe use fancy aerie typed json parsers/serializers, ala BasicParsers.productP use in MerlinParsers
  //TODO: or upgrade to gson or similar modern library with registered object mappings

  /**
   * {@inheritDoc}
   */
  @Override
  public long getPlanRevision(final PlanId planId) throws IOException, NoSuchPlanException, PlanServiceException {
    final var query = """
        query GetPlanRevision($id: Int!) {
          plan_by_pk(id: $id) {
            revision
          }
        }
        """;
    final var variables = Json.createObjectBuilder().add("id", planId.id()).build();

    final var response = postRequest(query, variables).orElseThrow(() -> new NoSuchPlanException(planId));
    try {
      return response.getJsonObject("data").getJsonObject("plan_by_pk").getJsonNumber("revision").longValueExact();
    } catch (ClassCastException | ArithmeticException e) {
      throw new NoSuchPlanException(planId);
    }
  }

  /**
   * {@inheritDoc}
   *
   * retrieves the metadata via a single atomic graphql query
   */
  @Override
  public PlanMetadata getPlanMetadata(final PlanId planId)
  throws IOException, NoSuchPlanException, PlanServiceException
  {
    final var request = (
        "query getPlanMetadata { "
        + "plan_by_pk( id: %s ) { "
        + "  id revision start_time duration "
        + "  mission_model { "
        + "    id name version "
        + "    uploaded_file { name } "
        + "  } "
        + "  simulations(limit:1, order_by:{revision:desc} ) { arguments }"
        + "} }"
    ).formatted(planId.id());
    final var response = postRequest(request).orElseThrow(() -> new NoSuchPlanException(planId));
    try {
      //TODO: elevate and then leverage existing MerlinParsers (after updating them to match current db!)
      final var plan = response.getJsonObject("data").getJsonObject("plan_by_pk");
      final long planPK = plan.getJsonNumber("id").longValue();
      final long planRev = plan.getJsonNumber("revision").longValue();
      final var startTime = parseGraphQLTimestamp(plan.getString("start_time"));
      final var duration = parseGraphQLInterval(plan.getString("duration"));

      final var model = plan.getJsonObject("mission_model");
      final var modelId = model.getJsonNumber("id").longValue();
      final var modelName = model.getString("name");
      final var modelVersion = model.getString("version");

      final var file = model.getJsonObject("uploaded_file");
      final var modelPath = Path.of(file.getString("name"));
      //NB: not using the "path" field because it is just a hex-encoded duplicate of the name field anyway
      //NB: the name includes the .jar extension

      //TODO: how to know right model config for scheduling? for now choosing latest sim setup (see query above)
      var modelConfiguration = Map.<String, SerializedValue>of();
      final var sims = plan.getJsonArray("simulations");
      if (!sims.isEmpty()) {
        final var args = sims.getJsonObject(0).getJsonObject("arguments");
        modelConfiguration = BasicParsers
            .mapP(serializedValueP).parse(args)
            .getSuccessOrThrow((reason) -> new InvalidJsonException(new InvalidEntityException(List.of(reason))));
      }

      final var endTime = (Instant) duration.addTo(startTime.toInstant());
      final var horizon = new PlanningHorizon(startTime.toInstant(), endTime);

      return new PlanMetadata(
          new PlanId(planPK),
          planRev,
          horizon,
          modelId,
          modelPath,
          modelName,
          modelVersion,
          modelConfiguration);
    } catch (ClassCastException | ArithmeticException | InvalidJsonException e) {
      //TODO: better error reporting upward to service response (NSPEx doesn't allow passing e as cause)
      throw new NoSuchPlanException(planId);
    }
  }

  /**
   * {@inheritDoc}
   * @return
   */
  @Override
  public MerlinPlan getPlanActivityDirectives(final PlanMetadata planMetadata, final Problem problem)
  throws IOException, NoSuchPlanException, PlanServiceException, InvalidJsonException, InstantiationException
  {
    final var merlinPlan = new MerlinPlan();
    final var request =
        "query { plan_by_pk(id:%d) { activity_directives { id start_offset type arguments anchor_id anchored_to_start } duration start_time }} ".formatted(
            planMetadata.planId().id());
    final var response = postRequest(request).orElseThrow(() -> new NoSuchPlanException(planMetadata.planId()));
    final var jsonplan = response.getJsonObject("data").getJsonObject("plan_by_pk");
    final var activityDirectives = jsonplan.getJsonArray("activity_directives");
    for (int i = 0; i < activityDirectives.size(); i++) {
      final var jsonActivity = activityDirectives.getJsonObject(i);
      final var type = activityDirectives.getJsonObject(i).getString("type");
      final var start = jsonActivity.getString("start_offset");
      final Integer anchorId = jsonActivity.isNull("anchor_id") ? null : jsonActivity.getInt("anchor_id");
      final boolean anchoredToStart = jsonActivity.getBoolean("anchored_to_start");
      final var arguments = jsonActivity.getJsonObject("arguments");
      final var deserializedArguments = BasicParsers
          .mapP(serializedValueP)
          .parse(arguments)
          .getSuccessOrThrow((reason) -> new InvalidJsonException(new InvalidEntityException(List.of(reason))));
      final var effectiveArguments = problem
          .getActivityType(type)
          .getSpecType()
          .getInputType()
          .getEffectiveArguments(deserializedArguments);
      final var merlinActivity = new ActivityDirective(
          Duration.of(parseGraphQLInterval(start).getDuration().toNanos() / 1000, Duration.MICROSECONDS),
          type,
          effectiveArguments,
          (anchorId != null) ? new ActivityDirectiveId(anchorId) : null,
          anchoredToStart);
      final var actPK = new ActivityDirectiveId(jsonActivity.getJsonNumber("id").longValue());
      merlinPlan.addActivity(actPK, merlinActivity);
    }
    return merlinPlan;
  }

  /**
   * generate a name for the next created plan container using current timestamp
   *
   * currently, does not actually verify that the name is unique within aerie database
   *
   * @return a name for the next created plan container
   */
  public String getNextPlanName() {
    //TODO: (defensive) should rely on database to generate a new unique name to avoid user collisions
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");
    return "scheduled_plan_" + dtf.format(LocalDateTime.now());
  }

  /**
   * {@inheritDoc}
   * @return
   */
  @Override
  public Pair<PlanId, Map<SchedulingActivityDirective, ActivityDirectiveId>> createNewPlanWithActivityDirectives(
      final PlanMetadata planMetadata,
      final Plan plan,
      final Map<SchedulingActivityDirective, GoalId> activityToGoalId
  )
  throws IOException, NoSuchPlanException, PlanServiceException
  {
    final var planName = getNextPlanName();
    final var planId = createEmptyPlan(
        planName, planMetadata.modelId(),
        planMetadata.horizon().getStartInstant(), planMetadata.horizon().getEndAerie());
    final Map<SchedulingActivityDirective, ActivityDirectiveId> activityToId = createAllPlanActivityDirectives(planId, plan, activityToGoalId);

    return Pair.of(planId, activityToId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public PlanId createEmptyPlan(final String name, final long modelId, final Instant startTime, final Duration duration)
  throws IOException, NoSuchPlanException, PlanServiceException
  {
    final var requestFormat = (
        "mutation createEmptyPlan { insert_plan_one( object: { "
        + "name: %s model_id: %d start_time: %s duration: %s "
        + "} ) { id } }");
    //TODO: resolve inconsistency in plan duration versus activity duration formats in merlin
    //NB: the duration format for creating plans is different than that for activity instances (microseconds)
    final var durStr = "\"" + duration.in(Duration.SECOND) + "\"";
    final var request = requestFormat.formatted(
        serializeForGql(name), modelId, serializeForGql(startTime.toString()), durStr);

    final var response = postRequest(request).orElseThrow(() -> new NoSuchPlanException(null));
    try {
      return new PlanId(
          response
              .getJsonObject("data")
              .getJsonObject("insert_plan_one")
              .getJsonNumber("id")
              .longValueExact());
    } catch (ClassCastException | ArithmeticException e) {
      throw new NoSuchPlanException(null);
    }
  }

  /**
   * {@inheritDoc}
   * @return
   */
  @Override
  public Map<SchedulingActivityDirective, ActivityDirectiveId> updatePlanActivityDirectives(
      final PlanId planId,
      final Map<SchedulingActivityDirectiveId, ActivityDirectiveId> idsFromInitialPlan,
      final MerlinPlan initialPlan,
      final Plan plan,
      final Map<SchedulingActivityDirective, GoalId> activityToGoalId
  )
  throws IOException, NoSuchPlanException, PlanServiceException
  {
    final var ids = new HashMap<SchedulingActivityDirective, ActivityDirectiveId>();
    //creation are done in batch as that's what the scheduler does the most
    final var toAdd = new ArrayList<SchedulingActivityDirective>();
    for (final var activity : plan.getActivities()) {
      if(activity.getParentActivity().isPresent()) continue; // Skip generated activities
      final var idActFromInitialPlan = idsFromInitialPlan.get(activity.getId());
      if (idActFromInitialPlan != null) {
        //add duration to parameters if controllable
        if (activity.getType().getDurationType() instanceof DurationType.Controllable durationType){
          if (!activity.arguments().containsKey(durationType.parameterName())){
            activity.addArgument(durationType.parameterName(), SerializedValue.of(activity.duration().in(Duration.MICROSECONDS)));
          }
        }
        final var actFromInitialPlan = initialPlan.getActivityById(idActFromInitialPlan);
        //if act was present in initial plan
        final var activityDirectiveFromSchedulingDirective = new ActivityDirective(
            activity.startOffset(),
            activity.type().getName(),
            activity.arguments(),
            (activity.anchorId() != null ? new ActivityDirectiveId(-activity.anchorId().id()) : null),
            activity.anchoredToStart()
        );
        final var activityDirectiveId = idsFromInitialPlan.get(activity.getId());
        if (!activityDirectiveFromSchedulingDirective.equals(actFromInitialPlan.get())) {
          throw new PlanServiceException("The scheduler should not be updating activity instances");
          //updateActivityDirective(planId, schedulerActIntoMerlinAct, activityDirectiveId, activityToGoalId.get(activity));
        }
        ids.put(activity, activityDirectiveId);
      } else {
        //act was not present in initial plan, create new activity
        toAdd.add(activity);
      }
    }
    final var actsFromNewPlan = plan.getActivitiesById();
    for (final var idActInInitialPlan : idsFromInitialPlan.entrySet()) {
      if (!actsFromNewPlan.containsKey(idActInInitialPlan.getKey())) {
        throw new PlanServiceException("The scheduler should not be deleting activity instances");
        //deleteActivityDirective(idActInInitialPlan.getValue());
      }
    }

    //Create
    ids.putAll(createActivityDirectives(planId, toAdd, activityToGoalId));

    return ids;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void ensurePlanExists(final PlanId planId) throws IOException, NoSuchPlanException, PlanServiceException {
    final Supplier<NoSuchPlanException> exceptionFactory = () -> new NoSuchPlanException(planId);
    final var request = "query ensurePlanExists { plan_by_pk( id: %s ) { id } }"
        .formatted(planId.id());
    final var response = postRequest(request).orElseThrow(exceptionFactory);
    try {
      final var id =
          new PlanId(
              response
              .getJsonObject("data")
              .getJsonObject("plan_by_pk")
              .getJsonNumber("id")
              .longValueExact());
      if (!id.equals(planId)) {
        throw exceptionFactory.get();
      }
    } catch (ClassCastException | ArithmeticException e) {
      //TODO: better error reporting upward to service response (NSPEx doesn't allow passing e as cause)
      throw exceptionFactory.get();
    }
  }

  /**
   * {@inheritDoc}
   */
  //TODO: (error cleanup) more diverse exceptions for failed operations
  @Override
  public void clearPlanActivityDirectives(final PlanId planId) throws IOException, NoSuchPlanException, PlanServiceException {
    ensurePlanExists(planId);
    final var request = (
        "mutation clearPlanActivities {"
        + "  delete_activity_directive(where: { plan_id: { _eq: %d } }) {"
        + "    affected_rows"
        + "  }"
        + "}"
    ).formatted(planId.id());
    final var response = postRequest(request).orElseThrow(() -> new NoSuchPlanException(planId));
    try {
      response.getJsonObject("data").getJsonObject("delete_activity").getJsonNumber("affected_rows").longValueExact();
    } catch (ClassCastException | ArithmeticException e) {
      throw new NoSuchPlanException(planId);
    }
  }

  /**
   * {@inheritDoc}
   * @return
   */
  @Override
  public Map<SchedulingActivityDirective, ActivityDirectiveId> createAllPlanActivityDirectives(
      final PlanId planId,
      final Plan plan,
      final Map<SchedulingActivityDirective, GoalId> activityToGoalId
  )
  throws IOException, NoSuchPlanException, PlanServiceException
  {
    return createActivityDirectives(planId, plan.getActivitiesByTime(), activityToGoalId);
  }

  public Map<SchedulingActivityDirective, ActivityDirectiveId> createActivityDirectives(
      final PlanId planId,
      final List<SchedulingActivityDirective> orderedActivities,
      final Map<SchedulingActivityDirective, GoalId> activityToGoalId
  )
  throws IOException, NoSuchPlanException, PlanServiceException
  {
    ensurePlanExists(planId);
    final var query = """
        mutation createAllPlanActivityDirectives($activities: [activity_directive_insert_input!]!) {
          insert_activity_directive(objects: $activities) {
            returning {
              id
            }
            affected_rows
          }
        }
        """;

    //assemble the entire mutation request body
    //TODO: (optimization) could use a lazy evaluating stream of strings to avoid large set of strings in memory
    //TODO: (defensive) should sanitize all strings uses as keys/values to avoid injection attacks

    final var insertionObjects = Json.createArrayBuilder();
    for (final var act : orderedActivities) {
      var insertionObject = Json
          .createObjectBuilder()
          .add("plan_id", planId.id())
          .add("type", act.getType().getName())
          .add("start_offset", act.startOffset().toString());

      //add duration to parameters if controllable
      final var insertionObjectArguments = Json.createObjectBuilder();
      if(act.getType().getDurationType() instanceof DurationType.Controllable durationType){
        if(!act.arguments().containsKey(durationType.parameterName())){
          insertionObjectArguments.add(durationType.parameterName(), act.duration().in(Duration.MICROSECOND));
        }
      }

      final var goalId = activityToGoalId.get(act);
      if (goalId != null) {
        insertionObject.add("source_scheduling_goal_id", goalId.id());
      }

      for (final var arg : act.arguments().entrySet()) {
        //serializedValueP is safe to use here because only unparsing. otherwise subject to int/double typing confusion
        insertionObjectArguments.add(arg.getKey(), serializedValueP.unparse(arg.getValue()));
      }
      insertionObject.add("arguments", insertionObjectArguments.build());
      insertionObjects.add(insertionObject.build());
    }

    final var arguments = Json
        .createObjectBuilder()
        .add("activities", insertionObjects.build())
        .build();

    final var response = postRequest(query, arguments).orElseThrow(() -> new NoSuchPlanException(planId));

    final Map<SchedulingActivityDirective, ActivityDirectiveId> instanceToInstanceId = new HashMap<>();
    try {
      final var numCreated = response
          .getJsonObject("data").getJsonObject("insert_activity_directive").getJsonNumber("affected_rows").longValueExact();
      if (numCreated != orderedActivities.size()) {
        throw new NoSuchPlanException(planId);
      }
      var ids = response
          .getJsonObject("data").getJsonObject("insert_activity_directive").getJsonArray("returning");
      //make sure we associate the right id with the right activity
      for(int i = 0; i < ids.size(); i++) {
        instanceToInstanceId.put(orderedActivities.get(i), new ActivityDirectiveId(ids.getJsonObject(i).getInt("id")));
      }
    } catch (ClassCastException | ArithmeticException e) {
      throw new NoSuchPlanException(planId);
    }
    return instanceToInstanceId;
  }

  @Override
  public MissionModelTypes getMissionModelTypes(final PlanId planId)
  throws IOException, MissionModelServiceException
  {
    final var request = """
        query GetActivityTypesForPlan {
          plan_by_pk(id: %d) {
            mission_model {
              id
              activity_types {
                name
                parameters
                presets {
                  name
                  arguments
                }
              }
            }
          }
        }
        """.formatted(planId.id());
    final JsonObject response;
    try {
      response = postRequest(request).get();
    } catch (PlanServiceException e) {
      throw new MissionModelServiceException("Failed to get mission model types for plan id %s".formatted(planId), e);
    }
    final var activityTypesJsonArray =
        response.getJsonObject("data")
                .getJsonObject("plan_by_pk")
                .getJsonObject("mission_model")
                .getJsonArray("activity_types");
    final var activityTypes = parseActivityTypes(activityTypesJsonArray);

    final var missionModelId = new MissionModelId(response.getJsonObject("data")
                                       .getJsonObject("plan_by_pk")
                                       .getJsonObject("mission_model")
                                       .getInt("id"));

    return new MissionModelTypes(activityTypes, getResourceTypes(missionModelId));
  }

  private static List<ActivityType> parseActivityTypes(final JsonArray activityTypesJsonArray) {
    final var activityTypes = new ArrayList<ActivityType>();
    for (final var activityTypeJson : activityTypesJsonArray) {
      final var parametersJson = activityTypeJson.asJsonObject().getJsonObject("parameters");
      final var parameters = new HashMap<String, ValueSchema>();
      for (final var parameterJson : parametersJson.entrySet()) {
        parameters.put(
            parameterJson.getKey(),
            valueSchemaP
                .parse(
                    parameterJson
                        .getValue()
                        .asJsonObject()
                        .getJsonObject("schema"))
                .getSuccessOrThrow());
      }
      final var presetsJsonArray = activityTypeJson.asJsonObject().getJsonArray("presets");
      final var presets = new HashMap<String, Map<String, SerializedValue>>();
      for (final var presetJson: presetsJsonArray) {
        final var argumentsJson = presetJson.asJsonObject().getJsonObject("arguments");
        final var arguments = new HashMap<String, SerializedValue>();
        for (final var argumentJson: argumentsJson.entrySet()) {
          arguments.put(
              argumentJson.getKey(),
              serializedValueP.parse(argumentJson.getValue()).getSuccessOrThrow()
          );
        }
        presets.put(
            presetJson.asJsonObject().getString("name"),
            arguments
        );
      }
      activityTypes.add(new ActivityType(activityTypeJson.asJsonObject().getString("name"), parameters, presets));
    }
    return activityTypes;
  }

  @Override
  public MissionModelTypes getMissionModelTypes(final MissionModelId missionModelId)
  throws IOException, MissionModelServiceException, NoSuchMissionModelException
  {
    final var request = """
        query GetActivityTypesFromMissionModel{
          mission_model_by_pk(id:%d){
            activity_types{
              name
              parameters
              presets {
                name
                arguments
              }
            }
          }
        }
        """.formatted(missionModelId.id());
    final JsonObject response;
    try {
      response = postRequest(request).get();
    } catch (PlanServiceException e) {
      throw new MissionModelServiceException("Failed to get mission model types for model id %s".formatted(missionModelId), e);
    }
    final var data = response.getJsonObject("data");
    if (data.get("mission_model_by_pk").getValueType().equals(JsonValue.ValueType.NULL)) throw new NoSuchMissionModelException(missionModelId);
    final var activityTypesJsonArray = data
        .getJsonObject("mission_model_by_pk")
        .getJsonArray("activity_types");
    final var activityTypes = parseActivityTypes(activityTypesJsonArray);

    return new MissionModelTypes(activityTypes, getResourceTypes(missionModelId));
  }

  public Collection<ResourceType> getResourceTypes(final MissionModelId missionModelId)
  throws IOException, MissionModelServiceException
  {
    final var request = """
        query GetResourceTypes {
           resourceTypes(missionModelId: "%d") {
             name
             schema
           }
         }
        """.formatted(missionModelId.id());
    final JsonObject response;
    try {
      response = postRequest(request).get();
    } catch (PlanServiceException e) {
      throw new MissionModelServiceException("Failed to get mission model types for model id %s".formatted(missionModelId), e);
    }
    final var data = response.getJsonObject("data");
    final var resourceTypesJsonArray = data.getJsonArray("resourceTypes");

    final var resourceTypes = new ArrayList<ResourceType>();

    for (final var jsonValue : resourceTypesJsonArray) {
      final var jsonObject = jsonValue.asJsonObject();
      final var name = jsonObject.getString("name");
      final var schema = jsonObject.getJsonObject("schema");

      resourceTypes.add(new ResourceType(name, valueSchemaP.parse(schema).getSuccessOrThrow()));
    }

    return resourceTypes;
  }


public SimulationId getSimulationId(PlanId planId) throws PlanServiceException, IOException {
    final var request = """
        query {
          simulation(where: {plan_id: {_eq: %d}}) {
            id
          }
        }
        """.formatted(planId.id());
  final JsonObject response;
  response = postRequest(request).get();
  final var data = response.getJsonObject("data");
  final var simulationId = data.getJsonArray("simulation").get(0).asJsonObject().getInt("id");
  return new SimulationId(simulationId);
}

  private record SimulationId(long id){}

  private record ProfileRecord(
      long id,
      long datasetId,
      String name,
      Pair<String, ValueSchema> type,
      Duration duration
  ) {}

  private record SpanRecord(
      String type,
      Instant start,
      Optional<Duration> duration,
      Optional<Long> parentId,
      List<Long> childIds,
      ActivityAttributesRecord attributes
  ) {}

  public DatasetId storeSimulationResults(final PlanMetadata planMetadata,
                                          final SimulationResults results,
                                          final Map<ActivityDirectiveId, ActivityDirectiveId> simulationActivityDirectiveIdToMerlinActivityDirectiveId) throws PlanServiceException, IOException
  {
    final var simulationId = getSimulationId(planMetadata.planId());
    final var datasetIds = createSimulationDataset(simulationId, planMetadata);
    final var profileSet = ProfileSet.of(results.realProfiles, results.discreteProfiles);
    final var profileRecords = postResourceProfiles(
        datasetIds.datasetId(),
        profileSet.realProfiles(),
        profileSet.discreteProfiles());
    postProfileSegments(datasetIds.datasetId(), profileRecords, profileSet);
    postActivities(datasetIds.datasetId(), results.simulatedActivities, results.unfinishedActivities, results.startTime, simulationActivityDirectiveIdToMerlinActivityDirectiveId);
    insertSimulationTopics(datasetIds.datasetId(), results.topics);
    insertSimulationEvents(datasetIds.datasetId(), results.events);
    setSimulationDatasetStatus(datasetIds.simulationDatasetId(), SimulationStateRecord.success());
    return datasetIds.datasetId();
  }

  private SimulationId createSimulation(final PlanId planId, final Map<String, SerializedValue> arguments)
  throws PlanServiceException, IOException
  {
    final var request = """
        mutation {
          insert_simulation_one(object: {plan_id: %d, arguments: %s}) {
            id
            revision
          }
        }""".formatted(
            planId.id(),
            simulationArgumentsP.unparse(arguments)
    );
    final JsonObject response;
    response = postRequest(request).get();
    final var data = response.getJsonObject("data");
    final var simulationId = data.getJsonObject("insert_simulation_one").getInt("id");
    return new SimulationId(simulationId);
  }


  private void setSimulationDatasetStatus(SimulationDatasetId id, SimulationStateRecord state)
  throws PlanServiceException, IOException
  {
    final var request = """
        mutation {
          update_simulation_dataset(where: {id: {_eq: %d}}, _set: {status: %s}) {
            affected_rows
          }
        }
        """.formatted(
        id.id(),
        state.status().label
    );
    final JsonObject response;
    response = postRequest(request).get();
    final var data = response.getJsonObject("data");
    final var affected = data.getJsonObject("update_simulation_dataset").getInt("affected_rows");
    if(affected != 1){
      throw new PlanServiceException("Unable to modify the status of simulation dataset with id %d".formatted(id.id()));
    }
  }

  public record SimulationDatasetId(int id){}

  public record DatasetIds(DatasetId datasetId, SimulationDatasetId simulationDatasetId){}

  private DatasetIds createSimulationDataset(SimulationId simulationId, PlanMetadata planMetadata)
  throws PlanServiceException, IOException
  {
    final var request = """
        mutation {
          insert_simulation_dataset_one(object: {simulation_id: %d, simulation_start_time:"%s", simulation_end_time:"%s", arguments:{}, status: %s}) {
            id
            dataset_id
          }
        }
        """.formatted(
            simulationId.id(),
            planMetadata.horizon().getStartInstant(),
            planMetadata.horizon().getEndInstant(),
            SimulationStateRecord.Status.INCOMPLETE.label
    );
    final JsonObject response;
    response = postRequest(request).get();
    final var data = response.getJsonObject("data");
    final var datasetId = data.getJsonObject("insert_simulation_dataset_one").getInt("dataset_id");
    final var simulationDatasetId = data.getJsonObject("insert_simulation_dataset_one").getInt("id");
    return new DatasetIds(new DatasetId(datasetId), new SimulationDatasetId(simulationDatasetId));
  }

  private static <T> Duration sumDurations(final List<ProfileSegment<Optional<T>>> segments) {
    return segments.stream().reduce(
        Duration.ZERO,
        (acc, pair) -> acc.plus(pair.extent()),
        Duration::plus
    );
  }
  private HashMap<String, ProfileRecord> postResourceProfiles(DatasetId datasetId,
                                                              final Map<String, Pair<ValueSchema, List<ProfileSegment<Optional<RealDynamics>>>>> realProfiles,
                                                              final Map<String, Pair<ValueSchema, List<ProfileSegment<Optional<SerializedValue>>>>> discreteProfiles)
  throws PlanServiceException, IOException
  {
    final var req = """
        mutation($profiles: [profile_insert_input!]!) {
          insert_profile(objects: $profiles){
            returning {
              id
              name
            }
          }
        }""";
    final var allProfiles = Json.createArrayBuilder();
    final var resourceNames = new ArrayList<String>();
    final var resourceTypes = new ArrayList<Pair<String, ValueSchema>>();
    final var durations = new ArrayList<Duration>();
    for (final var entry : realProfiles.entrySet()) {
      final var resource = entry.getKey();
      final var schema = entry.getValue().getLeft();
      final var realResourceType = Pair.of("real", schema);
      final var segments = entry.getValue().getRight();
      final var duration = sumDurations(segments);
      resourceNames.add(resource);
      resourceTypes.add(realResourceType);
      durations.add(duration);
      allProfiles.add(Json.createObjectBuilder()
          .add("dataset_id", datasetId.id())
          .add("duration", graphQLIntervalFromDuration(duration).toString())
          .add("name", resource)
          .add("type",realProfileTypeP.unparse(realResourceType))
          .build()
      );
    }
    for (final var entry : discreteProfiles.entrySet()) {
      final var resource = entry.getKey();
      final var schema = entry.getValue().getLeft();
      final var resourceType = Pair.of("discrete", schema);
      final var segments = entry.getValue().getRight();
      final var duration = sumDurations(segments);
      resourceNames.add(resource);
      resourceTypes.add(resourceType);
      durations.add(duration);
      allProfiles.add(Json.createObjectBuilder()
                      .add("dataset_id", datasetId.id())
                      .add("duration", graphQLIntervalFromDuration(duration).toString())
                      .add("name", resource)
                      .add("type",discreteProfileTypeP.unparse(resourceType))
                      .build()
      );
    }
    final var arguments = Json.createObjectBuilder()
                              .add("profiles", allProfiles.build())
                              .build();
    final JsonObject response;
    response = postRequest(req, arguments).get();
    final var data = response.getJsonObject("data").getJsonObject("insert_profile").getJsonArray("returning");
    final var profileRecords = new HashMap<String, ProfileRecord>(resourceNames.size());
    for (int i = 0; i < resourceNames.size(); i++) {
      final var dataReturned = data.get(i);
      final var resource = resourceNames.get(i);
      final var type = resourceTypes.get(i);
      final var duration = durations.get(i);
      final var id = dataReturned.asJsonObject().getInt("id");
      final var nameResourceReturned = dataReturned.asJsonObject().getString("name");
      if(!nameResourceReturned.equals(resource)){
        throw new PlanServiceException("Resource do not match");
      }
      profileRecords.put(resource, new ProfileRecord(
          id,
          datasetId.id(),
          resource,
          type,
          duration
      ));
    }
    return profileRecords;
  }

  public com.impossibl.postgres.api.data.Interval graphQLIntervalFromDuration(Duration duration){
    return Interval.of(java.time.Duration.ofNanos(duration.in(MICROSECOND)*1000));
  }

  public com.impossibl.postgres.api.data.Interval graphQLIntervalFromDuration(Instant instant1, Instant instant2){
    return Interval.of(java.time.Duration.between(instant1, instant2));
  }

  private void postProfileSegments(
      final DatasetId datasetId,
      final Map<String, ProfileRecord> records,
      final ProfileSet profileSet
  ) throws PlanServiceException, IOException
  {
    final var realProfiles = profileSet.realProfiles();
    final var discreteProfiles = profileSet.discreteProfiles();
    for (final var entry : records.entrySet()) {
      final ProfileRecord record =  entry.getValue();
      final var resource =  entry.getKey();
      switch (record.type().getLeft()) {
        case "real" -> postRealProfileSegments(
            datasetId,
            record,
            realProfiles.get(resource).getRight());
        case "discrete" -> postDiscreteProfileSegments(
            datasetId,
            record,
            discreteProfiles.get(resource).getRight());
        default -> throw new Error("Unrecognized profile type " + record.type().getLeft());
      }
    }
  }

  private <Dynamics> void postProfileSegment(
      final DatasetId datasetId,
      final ProfileRecord profileRecord,
      final List<ProfileSegment<Optional<Dynamics>>> segments,
      final JsonParser<Dynamics> dynamicsP
  ) throws PlanServiceException, IOException
  {
    final var req = """
        mutation($profileSegments:[profile_segment_insert_input!]!) {
          insert_profile_segment(objects: $profileSegments) {
            affected_rows
          }
        }
        """;
    final var profiles = Json.createArrayBuilder();
    var accumulatedOffset = Duration.ZERO;
    for (final var pair : segments) {
      final var duration = pair.extent();
      final var dynamics = pair.dynamics();

      final String stringDynamics;
      final boolean stringIsGap;
      if(dynamics.isPresent()){
        stringDynamics = serializeDynamics(dynamics.get(), dynamicsP);
        stringIsGap = false;
      } else{
        stringDynamics = null;
        stringIsGap = true;
      }
      profiles.add(Json.createObjectBuilder()
          .add("dataset_id", datasetId.id())
          .add("profile_id", profileRecord.id())
          .add("start_offset", graphQLIntervalFromDuration(accumulatedOffset).toString())
          .add("is_gap", stringIsGap)
          .add("dynamics", stringDynamics)
          .build());
      accumulatedOffset = Duration.add(accumulatedOffset, duration);
    }

    final var arguments = Json.createObjectBuilder()
                              .add("profileSegments", profiles)
                              .build();

    final JsonObject response;
    try {
      response = postRequest(req, arguments).get();
    } catch (PlanServiceException e) {
      throw new PlanServiceException(e.toString());
    }
    final var affected_rows = response.getJsonObject("data").getJsonObject("insert_profile_segment").getInt("affected_rows");
    if(affected_rows!=segments.size()) {
      throw new PlanServiceException("not the same size");
    }
}
  private <Dynamics> String serializeDynamics(final Dynamics dynamics, final JsonParser<Dynamics> dynamicsP) {
    return dynamicsP.unparse(dynamics).toString();
  }
  private void postRealProfileSegments(final DatasetId datasetId,
                                              final ProfileRecord profileRecord,
                                              final List<ProfileSegment<Optional<RealDynamics>>> segments)
  throws PlanServiceException, IOException
  {
    postProfileSegment(datasetId, profileRecord, segments, realDynamicsP);
  }

  private void postDiscreteProfileSegments(final DatasetId datasetId,
                                                  final ProfileRecord profileRecord,
                                                  final List<ProfileSegment<Optional<SerializedValue>>> segments)
  throws PlanServiceException, IOException
  {
    postProfileSegment(datasetId, profileRecord, segments, serializedValueP);
  }

  private void insertSimulationTopics(
      DatasetId datasetId,
      final List<Triple<Integer, String, ValueSchema>> topics) throws PlanServiceException, IOException
  {
    final var req = """
        mutation($topics:[topic_insert_input!]!) {
          insert_topic(objects: $topics){
            affected_rows
          }
        }
        """;
    final var jsonTopics = Json.createArrayBuilder();
    for (final var topic : topics) {
      jsonTopics.add(
          Json.createObjectBuilder()
              .add("dataset_id", datasetId.id())
              .add("topic_index", topic.getLeft())
              .add("name", topic.getMiddle())
              .add("value_schema", valueSchemaP.unparse(topic.getRight()))
              .build()
      );
    }
    final var arguments = Json.createObjectBuilder()
                              .add("topics", jsonTopics.build())
                              .build();
    postRequest(req, arguments);
  }

  private void insertSimulationEvents(
      DatasetId datasetId,
      Map<Duration, List<EventGraph<Pair<Integer, SerializedValue>>>> eventPoints) throws PlanServiceException, IOException
  {
    final var req = """
            mutation($events:[event_insert_input!]!){
                  insert_event(objects: $events) {
                    affected_rows
                  }
            }
        """;
    final var events = Json.createArrayBuilder();
    for (final var eventPoint : eventPoints.entrySet()) {
      final var time = eventPoint.getKey();
      final var transactions = eventPoint.getValue();
      for (int transactionIndex = 0; transactionIndex < transactions.size(); transactionIndex++) {
        final var eventGraph = transactions.get(transactionIndex);
        final var flattenedEventGraph = EventGraphFlattener.flatten(eventGraph);
        events.addAll(batchInsertEventGraph(datasetId.id(), time, transactionIndex, flattenedEventGraph));
      }
    }
    final var arguments = Json.createObjectBuilder()
                              .add("events", events)
                              .build();
    postRequest(req, arguments);
  }

  private JsonArrayBuilder batchInsertEventGraph(
      final long datasetId,
      final Duration duration,
      final int transactionIndex,
      final List<Pair<String, Pair<Integer, SerializedValue>>> flattenedEventGraph
  ) {
    final var events = Json.createArrayBuilder();
    for (final Pair<String, Pair<Integer, SerializedValue>> entry : flattenedEventGraph) {
      final var causalTime = entry.getLeft();
      final Pair<Integer, SerializedValue> event = entry.getRight();
      events.add(
          Json.createObjectBuilder()
              .add("dataset_id",datasetId)
              .add("real_time", graphQLIntervalFromDuration(duration).toString())
              .add("transaction_index", transactionIndex)
              .add("causal_time", causalTime)
              .add("topic_index", event.getLeft())
              .add("value", serializedValueP.unparse(event.getRight()))
              .build()
      );
    }
    return events;
  }

  private void postActivities(
      final DatasetId datasetId,
      final Map<SimulatedActivityId, SimulatedActivity> simulatedActivities,
      final Map<SimulatedActivityId, UnfinishedActivity> unfinishedActivities,
      final Instant simulationStart,
      final Map<ActivityDirectiveId, ActivityDirectiveId> simulationActivityDirectiveIdToMerlinActivityDirectiveId
  ) throws PlanServiceException, IOException
  {
      final var simulatedActivityRecords = simulatedActivities.entrySet().stream()
                                                              .collect(Collectors.toMap(
                                                                  e -> e.getKey().id(),
                                                                  e -> simulatedActivityToRecord(e.getValue(), simulationActivityDirectiveIdToMerlinActivityDirectiveId)));
      final var allActivityRecords = unfinishedActivities.entrySet().stream()
                                                         .collect(Collectors.toMap(
                                                             e -> e.getKey().id(),
                                                             e -> unfinishedActivityToRecord(e.getValue(), simulationActivityDirectiveIdToMerlinActivityDirectiveId)));
      allActivityRecords.putAll(simulatedActivityRecords);
      final var simIdToPgId = postSpans(
          datasetId,
          allActivityRecords,
          simulationStart);
      updateSimulatedActivityParentsAction(
          datasetId,
          simulatedActivityRecords,
          simIdToPgId);
  }

  public void updateSimulatedActivityParentsAction(
    final DatasetId datasetId,
    final Map<Long, SpanRecord> simulatedActivities,
    final Map<Long, Long> simIdToPgId
) throws PlanServiceException, IOException
  {
  final var req = """
      mutation($updates:[span_updates!]!) {
        update_span_many(updates: $updates) {
          affected_rows
        }
      }
      """;
  final var updates = Json.createArrayBuilder();
  int updateCounter = 0;
  for (final var entry : simulatedActivities.entrySet()) {
    final var activity =  entry.getValue();
    final var id =  entry.getKey();
    if (activity.parentId().isEmpty()) continue;
    updates.add(Json.createObjectBuilder()
                   .add("where", Json.createObjectBuilder()
                                     .add("dataset_id",Json.createObjectBuilder().add("_eq", datasetId.id()).build())
                                     .add("id", Json.createObjectBuilder().add("_eq", simIdToPgId.get(id)).build()))
                   .add("_set", Json.createObjectBuilder().add("parent_id", simIdToPgId.get(activity.parentId().get())))
                   .build());
    updateCounter++;
  }
  final var arguments = Json.createObjectBuilder()
                            .add("updates", updates.build())
                            .build();

  final JsonObject response;
  response = postRequest(req, arguments).get();
    final var jsonValue = response.getJsonObject("data").get("update_span_many");
    var affected_rows = 0;
    if (jsonValue.getValueType() == JsonValue.ValueType.ARRAY) {
      for (final var jsonObject : response.getJsonObject("data").getJsonArray("update_span_many").getValuesAs(JsonObject.class)) {
        affected_rows += jsonObject.getInt("affected_rows");
      }
    } else {
      affected_rows = response.getJsonObject("data").getJsonObject("update_span_many").getInt("affected_rows");
    }
    if(affected_rows != updateCounter) {
      throw new PlanServiceException("not the same size");
    }
}

  private static SpanRecord simulatedActivityToRecord(final SimulatedActivity activity,
                                                      final Map<ActivityDirectiveId, ActivityDirectiveId> simulationActivityDirectiveIdToMerlinActivityDirectiveId) {
    return new SpanRecord(
        activity.type(),
        activity.start(),
        Optional.of(activity.duration()),
        Optional.ofNullable(activity.parentId()).map(SimulatedActivityId::id),
        activity.childIds().stream().map(SimulatedActivityId::id).collect(Collectors.toList()),
        new ActivityAttributesRecord(
            activity.directiveId().map(id -> simulationActivityDirectiveIdToMerlinActivityDirectiveId.get(id).id()),
            activity.arguments(),
            Optional.of(activity.computedAttributes())));
  }

  private static SpanRecord unfinishedActivityToRecord(final UnfinishedActivity activity,
                                                       final Map<ActivityDirectiveId, ActivityDirectiveId> simulationActivityDirectiveIdToMerlinActivityDirectiveId) {
    return new SpanRecord(
        activity.type(),
        activity.start(),
        Optional.empty(),
        Optional.ofNullable(activity.parentId()).map(SimulatedActivityId::id),
        activity.childIds().stream().map(SimulatedActivityId::id).collect(Collectors.toList()),
        new ActivityAttributesRecord(
            activity.directiveId().map(id -> simulationActivityDirectiveIdToMerlinActivityDirectiveId.get(id).id()),
            activity.arguments(),
            Optional.empty()));
  }

  public HashMap<Long, Long> postSpans(final DatasetId datasetId,
                                       final Map<Long, SpanRecord> spans,
                                       final Instant simulationStart
  ) throws PlanServiceException, IOException
  {
    final var req = """
                        mutation($spans:[span_insert_input!]!) {
                        insert_span(objects: $spans) {
                          returning {
                            id
                          }
                         }
                        }
                        """;
    final var spansJson = Json.createArrayBuilder();
    final var ids = spans.keySet().stream().toList();
    for (final var id : ids) {
      final var act = spans.get(id);
      final var startTime = graphQLIntervalFromDuration(simulationStart, act.start);
      spansJson.add(Json.createObjectBuilder()
                        .add("dataset_id", datasetId.id())
                        .add("start_offset", startTime.toString())
                        .add("duration", act.duration.isPresent() ? graphQLIntervalFromDuration(act.duration().get()).toString() : "null")
                        .add("type", act.type())
                        .add("attributes", buildAttributes(act.attributes().directiveId(), act.attributes().arguments(), act.attributes().computedAttributes()))
                        .build());
    }
    final var arguments = Json.createObjectBuilder()
                              .add("spans", spansJson)
                              .build();
    final JsonObject response;
    response = postRequest(req, arguments).get();
    final var returnedIds = response.getJsonObject("data").getJsonObject("insert_span").getJsonArray("returning");
    final var simIdToPostgresId = new HashMap<Long, Long>(ids.size());
    int i = 0;
    for (final var id : ids) {
      simIdToPostgresId.put(id, (long) returnedIds.get(i).asJsonObject().getInt("id"));
    }
    return simIdToPostgresId;
  }

  private JsonValue buildAttributes(final Optional<Long> directiveId, final Map<String, SerializedValue> arguments, final Optional<SerializedValue> returnValue) {
    return activityAttributesP.unparse(new ActivityAttributesRecord(directiveId, arguments, returnValue));
  }

  /**
   * serialize the given string in a manner that can be used as a graphql argument value
   * @param s the string to serialize
   * @return a serialization of the object suitable for use as a graphql value
   */
  public String serializeForGql(final String s) {
    //TODO: can probably leverage some serializers from aerie
    //TODO: (defensive) should escape contents of bare strings, eg internal quotes
    //NB: Time::toString will format correctly as HH:MM:SS.sss, just need to quote it here
    return "\"" + s + "\"";
  }
}
