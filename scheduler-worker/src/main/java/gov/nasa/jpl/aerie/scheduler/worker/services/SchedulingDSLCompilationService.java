package gov.nasa.jpl.aerie.scheduler.worker.services;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.stream.JsonParsingException;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Objects;
import static gov.nasa.jpl.aerie.constraints.json.ConstraintParsers.windowsExpressionP;
import gov.nasa.jpl.aerie.constraints.time.Windows;
import gov.nasa.jpl.aerie.constraints.tree.Expression;
import gov.nasa.jpl.aerie.json.JsonParser;
import gov.nasa.jpl.aerie.scheduler.server.http.InvalidEntityException;
import gov.nasa.jpl.aerie.scheduler.server.http.InvalidJsonException;
import gov.nasa.jpl.aerie.scheduler.server.models.PlanId;
import gov.nasa.jpl.aerie.scheduler.server.models.SchedulingCompilationError;
import gov.nasa.jpl.aerie.scheduler.server.models.SchedulingDSL;
import gov.nasa.jpl.aerie.scheduler.server.services.ConstraintsTypescriptCodeGenerationHelper;
import gov.nasa.jpl.aerie.scheduler.server.services.MissionModelService;
import gov.nasa.jpl.aerie.scheduler.server.services.TypescriptCodeGenerationService;

public class SchedulingDSLCompilationService {

  private final Process nodeProcess;

  public SchedulingDSLCompilationService()
  throws IOException
  {
    final var schedulingDslCompilerRoot = System.getenv("SCHEDULING_DSL_COMPILER_ROOT");
    final var schedulingDslCompilerCommand = System.getenv("SCHEDULING_DSL_COMPILER_COMMAND");
    final var nodePath = System.getenv("NODE_PATH");
    this.nodeProcess = new ProcessBuilder(nodePath, "--experimental-vm-modules", schedulingDslCompilerCommand)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .directory(new File(schedulingDslCompilerRoot))
        .start();

    final var inputStream = this.nodeProcess.outputWriter();
    inputStream.write("ping\n");
    inputStream.flush();
    if (!Objects.equals(this.nodeProcess.inputReader().readLine(), "pong")) {
      throw new Error("Could not create node subprocess");
    }
  }

  public void close() {
    this.nodeProcess.destroy();
  }

  public SchedulingDSLCompilationResult<SchedulingDSL.ConditionSpecifier> compileGlobalSchedulingCondition(final MissionModelService missionModelService, final PlanId planId, final String conditionTypescript) {
    try{
      final var missionModelTypes = missionModelService.getMissionModelTypes(planId);
      return compile(missionModelTypes,  conditionTypescript, SchedulingDSL.conditionSpecifierP, "GlobalSchedulingCondition");
    } catch (IOException | MissionModelService.MissionModelServiceException e) {
        throw new Error(e);
    }
  }

  /**
   * NOTE: This method is not re-entrant (assumes only one call to this method is running at any given time)
   */
  public SchedulingDSLCompilationResult<SchedulingDSL.GoalSpecifier> compileSchedulingGoalDSL(final MissionModelService missionModelService, final PlanId planId, final String goalTypescript)
  {
    try {
      final var missionModelTypes = missionModelService.getMissionModelTypes(planId);
      return compile(missionModelTypes, goalTypescript, SchedulingDSL.schedulingJsonP(missionModelTypes), "Goal");
    } catch (IOException | MissionModelService.MissionModelServiceException e) {
      throw new Error(e);
    }
  }

  private <T> SchedulingDSLCompilationResult<T> compile(
      final MissionModelService.MissionModelTypes missionModelTypes,
      final String goalTypescript,
      final JsonParser<T> parser,
      final String expectedReturnType)
  {
    final var schedulerGeneratedCode = TypescriptCodeGenerationService.generateTypescriptTypesFromMissionModel(missionModelTypes);
    final var constraintsGeneratedCode = gov.nasa.jpl.aerie.constraints.TypescriptCodeGenerationService.generateTypescriptTypes(
        ConstraintsTypescriptCodeGenerationHelper.activityTypes(missionModelTypes),
        ConstraintsTypescriptCodeGenerationHelper.resources(missionModelTypes));
    final JsonObject messageJson = Json.createObjectBuilder()
        .add("goalCode", goalTypescript)
        .add("schedulerGeneratedCode", schedulerGeneratedCode)
        .add("constraintsGeneratedCode", constraintsGeneratedCode)
        .add("expectedReturnType", expectedReturnType)
        .build();

    /*
    * PROTOCOL:
    *   denote this java program as JAVA, and the node subprocess as NODE
    *
    *   JAVA -- stdin --> NODE: { "goalCode": "sourcecode", "missionModelGeneratedCode": "generatedcode" } \n
    *   NODE -- stdout --> JAVA: one of "success\n", "error\n", or "panic\n"
    *   NODE -- stdout --> JAVA: payload associated with success, error, or panic, must be exactly one line terminated with \n
    * */
    final var inputWriter = this.nodeProcess.outputWriter();
    final var outputReader = this.nodeProcess.inputReader();
    try {
      inputWriter.write(messageJson+"\n");
      inputWriter.flush();
      final var status = outputReader.readLine();
      return switch (status) {
        case "panic" -> throw new Error(outputReader.readLine());
        case "error" -> {
          final var output = outputReader.readLine();
          try {
            yield new SchedulingDSLCompilationResult.Error<>(parseJson(
                output,
                SchedulingCompilationError.schedulingErrorJsonP));
          } catch (InvalidJsonException e) {
            throw new Error("Could not parse JSON returned from typescript: ", e);
          } catch (InvalidEntityException e) {
            throw new Error("Could not parse JSON returned from typescript: " + e.failures + "\n" + output);
          }
        }
        case "success" -> {
          final var output = outputReader.readLine();
          try {
            yield new SchedulingDSLCompilationResult.Success<>(parseJson(output, parser));
          } catch (InvalidJsonException e) {
            throw new Error("Could not parse JSON returned from typescript: " + output, e);
          } catch (InvalidEntityException e) {
            throw new Error("Could not parse JSON returned from typescript: " + e.failures + "\n" + output, e);
          }
        }
        default -> throw new Error("scheduling dsl compiler returned unexpected status: " + status);
      };
    } catch (IOException e) {
      throw new Error(e);
    }
  }

  private static <T> T parseJson(final String jsonStr, final JsonParser<T> parser)
  throws InvalidJsonException, InvalidEntityException
  {
    try (final var reader = Json.createReader(new StringReader(jsonStr))) {
      final var requestJson = reader.readValue();
      final var result = parser.parse(requestJson);
      return result.getSuccessOrThrow(reason -> new InvalidEntityException(List.of(reason)));
    } catch (JsonParsingException e) {
      throw new InvalidJsonException(e);
    }
  }

  public sealed interface SchedulingDSLCompilationResult<T> {
    record Success<T>(T value) implements SchedulingDSLCompilationResult<T> {}
    record Error<T>(List<SchedulingCompilationError.UserCodeError> errors) implements SchedulingDSLCompilationResult<T> {}
  }
}
