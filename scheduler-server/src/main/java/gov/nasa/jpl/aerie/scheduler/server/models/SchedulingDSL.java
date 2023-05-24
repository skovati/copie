package gov.nasa.jpl.aerie.scheduler.server.models;

import gov.nasa.jpl.aerie.constraints.time.Windows;
import gov.nasa.jpl.aerie.constraints.tree.Expression;
import gov.nasa.jpl.aerie.constraints.tree.StructExpressionAt;
import gov.nasa.jpl.aerie.json.JsonObjectParser;
import gov.nasa.jpl.aerie.json.JsonParser;
import gov.nasa.jpl.aerie.json.SumParsers;
import gov.nasa.jpl.aerie.json.Unit;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.scheduler.TimeUtility;
import gov.nasa.jpl.aerie.scheduler.constraints.timeexpressions.TimeAnchor;
import gov.nasa.jpl.aerie.scheduler.server.http.ActivityTemplateJsonParser;
import gov.nasa.jpl.aerie.scheduler.server.services.MissionModelService;

import java.util.List;
import java.util.Optional;

import static gov.nasa.jpl.aerie.constraints.json.ConstraintParsers.profileExpressionP;
import static gov.nasa.jpl.aerie.constraints.json.ConstraintParsers.structExpressionF;
import static gov.nasa.jpl.aerie.constraints.json.ConstraintParsers.windowsExpressionP;
import static gov.nasa.jpl.aerie.json.BasicParsers.boolP;
import static gov.nasa.jpl.aerie.json.BasicParsers.chooseP;
import static gov.nasa.jpl.aerie.json.BasicParsers.enumP;
import static gov.nasa.jpl.aerie.json.BasicParsers.intP;
import static gov.nasa.jpl.aerie.json.BasicParsers.listP;
import static gov.nasa.jpl.aerie.json.BasicParsers.literalP;
import static gov.nasa.jpl.aerie.json.BasicParsers.longP;
import static gov.nasa.jpl.aerie.json.BasicParsers.productP;
import static gov.nasa.jpl.aerie.json.BasicParsers.recursiveP;
import static gov.nasa.jpl.aerie.json.BasicParsers.stringP;
import static gov.nasa.jpl.aerie.json.Uncurry.tuple;
import static gov.nasa.jpl.aerie.json.Uncurry.untuple;
public class SchedulingDSL {

  public static final JsonParser<Duration> durationP =
      longP.map(
          microseconds -> Duration.of(microseconds, Duration.MICROSECONDS),
          duration -> duration.in(Duration.MICROSECONDS));

  private static final JsonParser<ClosedOpenInterval> intervalP =
      productP
          .field("start", durationP)
          .field("end", durationP)
          .map(
              untuple(ClosedOpenInterval::new),
              $ -> tuple($.start(), $.end()));

  private static final JsonParser<CardinalitySpecification> cardinalitySpecificationJsonParser =
      productP
          .optionalField("duration", durationP)
          .optionalField("occurrence", intP)
          .map(
              untuple(CardinalitySpecification::new),
              $ -> tuple($.duration(), $.occurrence()));

  private static JsonObjectParser<GoalSpecifier.RecurrenceGoalDefinition> recurrenceGoalDefinitionP(
      MissionModelService.MissionModelTypes activityTypes)
  {
    return productP
        .field("activityTemplate", new ActivityTemplateJsonParser(activityTypes))
        .optionalField("activityFinder", activityExpressionP)
        .field("interval", durationP)
        .field("shouldRollbackIfUnsatisfied", boolP)
        .map(
            untuple(GoalSpecifier.RecurrenceGoalDefinition::new),
            goalDefinition -> tuple(
                goalDefinition.activityTemplate(),
                goalDefinition.activityFinder(),
                goalDefinition.interval(),
                goalDefinition.shouldRollbackIfUnsatisfied()));
  }
  private static final JsonObjectParser<ConstraintExpression.ActivityExpression> activityExpressionP =
      productP
          .field("kind", literalP("ActivityExpression"))
          .field("type", stringP)
          .optionalField("matchingArguments", structExpressionF(profileExpressionP))
          .map(
              $ -> new ConstraintExpression.ActivityExpression($.getLeft().getRight(), $.getRight()),
              t -> tuple(Unit.UNIT, t.type(), t.arguments));

  private static final JsonParser<LinearResource> linearResourceP =
      stringP
          .map(LinearResource::new, LinearResource::name);

  private static final JsonParser<ConstraintExpression> constraintExpressionP =
      chooseP(
          activityExpressionP,
          windowsExpressionP.map(
              ConstraintExpression.WindowsExpression::new,
              ConstraintExpression.WindowsExpression::expression));

  public static final JsonParser<ActivityTimingConstraint> activityTimingConstraintP =
      productP
          .field("windowProperty", enumP(TimeAnchor.class, Enum::name))
          .field("operator", enumP(TimeUtility.Operator.class, Enum::name))
          .field("operand", durationP)
          .field("singleton", boolP)
          .map(
              untuple(ActivityTimingConstraint::new),
              $ -> tuple($.windowProperty(), $.operator(), $.operand(), $.singleton()));

  private static final JsonObjectParser<GoalSpecifier.CoexistenceGoalDefinition> coexistenceGoalDefinitionP(
  MissionModelService.MissionModelTypes activityTypes)
  {
    return
        productP
            .field("activityTemplate", new ActivityTemplateJsonParser(activityTypes))
            .optionalField("activityFinder", activityExpressionP)
            .field("alias", stringP)
            .field("forEach", constraintExpressionP)
            .optionalField("startConstraint", activityTimingConstraintP)
            .optionalField("endConstraint", activityTimingConstraintP)
            .field("shouldRollbackIfUnsatisfied", boolP)
            .map(
                untuple(GoalSpecifier.CoexistenceGoalDefinition::new),
                goalDefinition -> tuple(
                    goalDefinition.activityTemplate(),
                    goalDefinition.activityFinder(),
                    goalDefinition.alias(),
                    goalDefinition.forEach(),
                    goalDefinition.startConstraint(),
                    goalDefinition.endConstraint(),
                    goalDefinition.shouldRollbackIfUnsatisfied()));
  }
  private static final JsonObjectParser<GoalSpecifier.CardinalityGoalDefinition> cardinalityGoalDefinitionP(
      MissionModelService.MissionModelTypes activityTypes) {
    return
        productP
            .field("activityTemplate", new ActivityTemplateJsonParser(activityTypes))
            .optionalField("activityFinder", activityExpressionP)
            .field("specification", cardinalitySpecificationJsonParser)
            .field("shouldRollbackIfUnsatisfied", boolP)
            .map(
                untuple(GoalSpecifier.CardinalityGoalDefinition::new),
                goalDefinition -> tuple(
                    goalDefinition.activityTemplate(),
                    goalDefinition.activityFinder(),
                    goalDefinition.specification(),
                    goalDefinition.shouldRollbackIfUnsatisfied()));
  }

  private static JsonObjectParser<GoalSpecifier.GoalAnd> goalAndF(final JsonParser<GoalSpecifier> goalSpecifierP) {
    return productP
        .field("goals", listP(goalSpecifierP))
        .field("shouldRollbackIfUnsatisfied", boolP)
        .map(untuple(GoalSpecifier.GoalAnd::new),
             goalDefinition -> tuple(
                 goalDefinition.goals,
                 goalDefinition.shouldRollbackIfUnsatisfied
             ));
  }

  private static JsonObjectParser<GoalSpecifier.GoalOr> goalOrF(final JsonParser<GoalSpecifier> goalSpecifierP) {
    return productP
        .field("goals", listP(goalSpecifierP))
        .field("shouldRollbackIfUnsatisfied", boolP)
        .map(untuple(GoalSpecifier.GoalOr::new),
            goalDefinition -> tuple(
                goalDefinition.goals,
                goalDefinition.shouldRollbackIfUnsatisfied
            ));
  }

  private static JsonObjectParser<GoalSpecifier.GoalApplyWhen> goalApplyWhenF(final JsonParser<GoalSpecifier> goalSpecifierP) {
    return productP
        .field("goal", goalSpecifierP)
        .field("window", windowsExpressionP)
        .map(untuple(GoalSpecifier.GoalApplyWhen::new),
            goalDefinition -> tuple(
                goalDefinition.goal(),
                goalDefinition.windows()));
  }


  private static JsonParser<GoalSpecifier> goalSpecifierF(MissionModelService.MissionModelTypes missionModelTypes) {
    return recursiveP(self -> SumParsers.sumP("kind", GoalSpecifier.class, List.of(
        SumParsers.variant(
            "ActivityRecurrenceGoal",
            GoalSpecifier.RecurrenceGoalDefinition.class,
            recurrenceGoalDefinitionP(missionModelTypes)),
        SumParsers.variant(
            "ActivityCoexistenceGoal",
            GoalSpecifier.CoexistenceGoalDefinition.class,
            coexistenceGoalDefinitionP(missionModelTypes)),
        SumParsers.variant(
            "ActivityCardinalityGoal",
            GoalSpecifier.CardinalityGoalDefinition.class,
            cardinalityGoalDefinitionP(missionModelTypes)),
        SumParsers.variant("GoalAnd", GoalSpecifier.GoalAnd.class, goalAndF(self)),
        SumParsers.variant("GoalOr", GoalSpecifier.GoalOr.class, goalOrF(self)),
        SumParsers.variant("ApplyWhen", GoalSpecifier.GoalApplyWhen.class, goalApplyWhenF(self))
    )));
  }

  private static final JsonObjectParser<ConditionSpecifier.GlobalSchedulingCondition> globalSchedulingConditionP =
      productP
          .field("expression", windowsExpressionP)
          .field("activityTypes", listP(stringP))
          .map(
              $ -> new ConditionSpecifier.GlobalSchedulingCondition($.getKey(), $.getRight()),
              $ -> tuple($.expression, $.activityTypes)
          );

  private static JsonObjectParser<ConditionSpecifier.AndCondition> conditionAndF(final JsonParser<ConditionSpecifier> conditionSpecifierP) {
    return productP
        .field("conditions", listP(conditionSpecifierP))
        .map(untuple(ConditionSpecifier.AndCondition::new),
             ConditionSpecifier.AndCondition::conditionSpecifiers);
  }

  public static JsonParser<ConditionSpecifier> conditionSpecifierP =
      recursiveP(self -> SumParsers.sumP("kind", ConditionSpecifier.class, List.of(
        SumParsers.variant(
            "GlobalSchedulingConditionAnd",
            ConditionSpecifier.AndCondition.class,
            conditionAndF(self)),
        SumParsers.variant(
            "GlobalSchedulingCondition",
            ConditionSpecifier.GlobalSchedulingCondition.class,
            globalSchedulingConditionP)
  )));

  public static final JsonParser<GoalSpecifier> schedulingJsonP(MissionModelService.MissionModelTypes missionModelTypes){
    return goalSpecifierF(missionModelTypes);
  }

  public sealed interface ConditionSpecifier {
    record GlobalSchedulingCondition(
        Expression<Windows> expression,
        List<String> activityTypes
    )  implements ConditionSpecifier {}

    record AndCondition(
        List<ConditionSpecifier> conditionSpecifiers
    ) implements ConditionSpecifier{}
  }

  public sealed interface GoalSpecifier {
    record RecurrenceGoalDefinition(
        ActivityTemplate activityTemplate,
        Optional<ConstraintExpression.ActivityExpression> activityFinder,
        Duration interval,
        boolean shouldRollbackIfUnsatisfied
    ) implements GoalSpecifier {}
    record CoexistenceGoalDefinition(
        ActivityTemplate activityTemplate,
        Optional<ConstraintExpression.ActivityExpression> activityFinder,
        String alias,
        ConstraintExpression forEach,
        Optional<ActivityTimingConstraint> startConstraint,
        Optional<ActivityTimingConstraint> endConstraint,
        boolean shouldRollbackIfUnsatisfied
    ) implements GoalSpecifier {}
    record CardinalityGoalDefinition(
        ActivityTemplate activityTemplate,
        Optional<ConstraintExpression.ActivityExpression> activityFinder,
        CardinalitySpecification specification,
        boolean shouldRollbackIfUnsatisfied
    ) implements GoalSpecifier {}
    record GoalAnd(List<GoalSpecifier> goals,
                   boolean shouldRollbackIfUnsatisfied) implements GoalSpecifier {}
    record GoalOr(List<GoalSpecifier> goals,
                  boolean shouldRollbackIfUnsatisfied) implements GoalSpecifier {}
    record GoalApplyWhen(
        GoalSpecifier goal,
        Expression<Windows> windows
    ) implements GoalSpecifier {}
  }

  public record LinearResource(String name) {}
  public record CardinalitySpecification(Optional<Duration> duration, Optional<Integer> occurrence){}
  public record ClosedOpenInterval(Duration start, Duration end){}

  public record ActivityTemplate(String activityType, StructExpressionAt arguments) {}
  public sealed interface ConstraintExpression {
    record ActivityExpression(String type, Optional<StructExpressionAt> arguments) implements ConstraintExpression {}
    record WindowsExpression(Expression<Windows> expression) implements ConstraintExpression {}
  }
  public record ActivityTimingConstraint(TimeAnchor windowProperty, TimeUtility.Operator operator, Duration operand, boolean singleton) {}
}
