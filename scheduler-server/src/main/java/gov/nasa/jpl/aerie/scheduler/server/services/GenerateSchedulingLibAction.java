package gov.nasa.jpl.aerie.scheduler.server.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import gov.nasa.jpl.aerie.scheduler.server.exceptions.NoSuchMissionModelException;
import gov.nasa.jpl.aerie.scheduler.server.models.MissionModelId;

public record GenerateSchedulingLibAction(
    MissionModelService missionModelService
) {
  public GenerateSchedulingLibAction {
    Objects.requireNonNull(missionModelService);
  }

  /**
   * common interface for different possible results of the query
   */
  public sealed interface Response {
    record Failure(String reason) implements Response {}
    record Success(Map<String, String> files) implements Response {}
  }

  /**
   * execute the scheduling operation on the target plan (or retrieve existing scheduling results)
   *
   * @param missionModelId the id of the mission model for which to generate a scheduling library
   * @return a response object wrapping the results of generating the code (either successful or not)
   */
  public Response run(final MissionModelId missionModelId) {
    try {
      final var schedulingDsl         = getTypescriptResource("scheduler-edsl-fluent-api.ts");
      final var schedulerAst          = getTypescriptResource("scheduler-ast.ts");
      final var windowsDsl            = getTypescriptResource("constraints/constraints-edsl-fluent-api.ts");
      final var windowsAst            = getTypescriptResource("constraints/constraints-ast.ts");
      final var temporalPolyfillTypes = getTypescriptResource("constraints/TemporalPolyfillTypes.ts");

      final var missionModelTypes = missionModelService.getMissionModelTypes(missionModelId);

      final var generatedSchedulerCode = TypescriptCodeGenerationService.generateTypescriptTypesFromMissionModel(missionModelTypes);
      final var generatedConstraintsCode = gov.nasa.jpl.aerie.constraints.TypescriptCodeGenerationService
          .generateTypescriptTypes(
              ConstraintsTypescriptCodeGenerationHelper.activityTypes(missionModelTypes),
              ConstraintsTypescriptCodeGenerationHelper.resources(missionModelTypes));
      return new Response.Success(
          Map.of("file:///%s".formatted(schedulingDsl.basename), schedulingDsl.source,
                 "file:///scheduler-mission-model-generated-code.ts", generatedSchedulerCode,
                 "file:///%s".formatted(schedulerAst.basename), schedulerAst.source,
                 "file:///%s".formatted(windowsDsl.basename), windowsDsl.source,
                 "file:///%s".formatted(windowsAst.basename), windowsAst.source,
                 "file:///mission-model-generated-code.ts", generatedConstraintsCode,
                 "file:///%s".formatted(temporalPolyfillTypes.basename), temporalPolyfillTypes.source
                 ));
    } catch (final NoSuchMissionModelException | IOException | MissionModelService.MissionModelServiceException e) {
      return new Response.Failure(e.getMessage());
    }
  }

  /*package-private*/ record TypescriptResource(String basename, String source) { }

  /** Retrieve a static Typescript library as a resource by the file's basename. */
  /*package-private*/ static TypescriptResource getTypescriptResource(final String basename) {
    final var stream = GenerateSchedulingLibAction.class.getResourceAsStream("/"+basename);
    if (stream == null)
      throw new Error("Resource path does not exist: `/%s`".formatted(basename));
    final var source = new BufferedReader(new InputStreamReader(stream)).lines().collect(Collectors.joining("\n"));
    return new TypescriptResource(basename, source);
  }
}
