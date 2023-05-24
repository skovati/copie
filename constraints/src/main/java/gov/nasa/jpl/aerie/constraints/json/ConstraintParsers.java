package gov.nasa.jpl.aerie.constraints.json;

import gov.nasa.jpl.aerie.constraints.model.DiscreteProfile;
import gov.nasa.jpl.aerie.constraints.model.LinearProfile;
import gov.nasa.jpl.aerie.constraints.model.Profile;
import gov.nasa.jpl.aerie.constraints.model.Violation;
import gov.nasa.jpl.aerie.constraints.tree.AbsoluteInterval;
import gov.nasa.jpl.aerie.constraints.time.Interval;
import gov.nasa.jpl.aerie.constraints.time.IntervalContainer;
import gov.nasa.jpl.aerie.constraints.time.Spans;
import gov.nasa.jpl.aerie.constraints.time.Windows;
import gov.nasa.jpl.aerie.constraints.tree.*;
import gov.nasa.jpl.aerie.json.JsonParser;
import gov.nasa.jpl.aerie.json.Unit;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;

import java.util.List;

import static gov.nasa.jpl.aerie.json.BasicParsers.boolP;
import static gov.nasa.jpl.aerie.json.BasicParsers.chooseP;
import static gov.nasa.jpl.aerie.json.BasicParsers.doubleP;
import static gov.nasa.jpl.aerie.json.BasicParsers.enumP;
import static gov.nasa.jpl.aerie.json.BasicParsers.instantP;
import static gov.nasa.jpl.aerie.json.BasicParsers.intP;
import static gov.nasa.jpl.aerie.json.BasicParsers.listP;
import static gov.nasa.jpl.aerie.json.BasicParsers.literalP;
import static gov.nasa.jpl.aerie.json.BasicParsers.longP;
import static gov.nasa.jpl.aerie.json.BasicParsers.mapP;
import static gov.nasa.jpl.aerie.json.BasicParsers.productP;
import static gov.nasa.jpl.aerie.json.BasicParsers.recursiveP;
import static gov.nasa.jpl.aerie.json.BasicParsers.stringP;
import static gov.nasa.jpl.aerie.json.Uncurry.tuple;
import static gov.nasa.jpl.aerie.json.Uncurry.untuple;
import static gov.nasa.jpl.aerie.merlin.driver.json.SerializedValueJsonParser.serializedValueP;

public final class ConstraintParsers {
  private ConstraintParsers() {}

  static final JsonParser<Interval.Inclusivity> inclusivityP =
      enumP(Interval.Inclusivity.class, Enum::name);

  static final JsonParser<IntervalAlias> intervalAliasP =
      productP
          .field("kind", literalP("IntervalAlias"))
          .field("alias", stringP)
          .map(
              untuple((kind, alias) -> new IntervalAlias(alias)),
              $ -> tuple(Unit.UNIT, $.alias())
          );

  static final JsonParser<AbsoluteInterval> absoluteIntervalP =
      productP
          .field("kind", literalP("AbsoluteInterval"))
          .optionalField("start", instantP)
          .optionalField("end", instantP)
          .optionalField("startInclusivity", inclusivityP)
          .optionalField("endInclusivity", inclusivityP)
          .map(
              untuple((kind, start, end, startInclusivity, endInclusivity) -> new AbsoluteInterval(start, end, startInclusivity, endInclusivity)),
              $ -> tuple(Unit.UNIT, $.start(), $.end(), $.startInclusivity(), $.endInclusivity())
          );

  static final JsonParser<Expression<Interval>> intervalExpressionP =
      chooseP(intervalAliasP, absoluteIntervalP);

  static <P extends Profile<P>> JsonParser<AssignGaps<P>> assignGapsF(final JsonParser<Expression<P>> profileParser) {
    return productP
        .field("kind", literalP("AssignGapsExpression"))
        .field("originalProfile", profileParser)
        .field("defaultProfile", profileParser)
        .map(
            untuple((kind, originalProfile, defaultProfile) -> new AssignGaps<P>(originalProfile, defaultProfile)),
            $ -> tuple(Unit.UNIT, $.originalProfile(), $.defaultProfile())
        );
  }

  static final JsonParser<DiscreteResource> discreteResourceP =
      productP
          .field("kind", literalP("DiscreteProfileResource"))
          .field("name", stringP)
          .map(
              untuple((kind, name) -> new DiscreteResource(name)),
              $ -> tuple(Unit.UNIT, $.name));

  static final JsonParser<DiscreteValue> discreteValueP =
      productP
          .field("kind", literalP("DiscreteProfileValue"))
          .field("value", serializedValueP)
          .optionalField("interval", intervalExpressionP)
          .map(
              untuple((kind, value, interval) -> new DiscreteValue(value, interval)),
              $ -> tuple(Unit.UNIT, $.value(), $.interval()));

  static final JsonParser<DiscreteParameter> discreteParameterP =
      productP
          .field("kind", literalP("DiscreteProfileParameter"))
          .field("alias", stringP)
          .field("name", stringP)
          .map(
              untuple((kind, alias, name) -> new DiscreteParameter(alias, name)),
              $ -> tuple(Unit.UNIT, $.activityAlias, $.parameterName));

  public static JsonParser<Expression<DiscreteProfile>> discreteProfileExprF(JsonParser<ProfileExpression<?>> profileExpressionP, JsonParser<Expression<Spans>> spansExpressionP) {
    return recursiveP(selfP -> chooseP(
        discreteResourceP,
        discreteValueP,
        discreteParameterP,
        assignGapsF(selfP),
        valueAtExpressionF(profileExpressionP, spansExpressionP),
        listExpressionF(profileExpressionP),
        structExpressionF(profileExpressionP)
    ));
  }

  public static JsonParser<StructExpressionAt> structExpressionF(JsonParser<ProfileExpression<?>> profileParser) {
      return productP
          .field("kind",literalP("StructProfileExpression"))
          .field("expressions", mapP(profileParser))
          .map(
              untuple((kind, expressions) -> new StructExpressionAt(expressions)),
              $ -> tuple(Unit.UNIT, $.fields()));
  }

  static JsonParser<ListExpressionAt> listExpressionF(JsonParser<ProfileExpression<?>> profileParser) {
    return productP
        .field("kind",literalP("ListProfileExpression"))
        .field("expressions", listP(profileParser))
        .map(
            untuple((kind, expressions) -> new ListExpressionAt(expressions)),
            $ -> tuple(Unit.UNIT, $.elements()));
  }

  static final JsonParser<RealResource> realResourceP =
      productP
          .field("kind", literalP("RealProfileResource"))
          .field("name", stringP)
          .map(
              untuple((kind, name) -> new RealResource(name)),
              $ -> tuple(Unit.UNIT, $.name));

  static final JsonParser<RealValue> realValueP =
      productP
          .field("kind", literalP("RealProfileValue"))
          .field("value", doubleP)
          .field("rate", doubleP)
          .optionalField("interval", intervalExpressionP)
          .map(
              untuple((kind, value, rate, interval) -> new RealValue(value, rate, interval)),
              $ -> tuple(Unit.UNIT, $.value(), $.rate(), $.interval()));

  static final JsonParser<RealParameter> realParameterP =
      productP
          .field("kind", literalP("RealProfileParameter"))
          .field("alias", stringP)
          .field("name", stringP)
          .map(
              untuple((kind, alias, name) -> new RealParameter(alias, name)),
              $ -> tuple(Unit.UNIT, $.activityAlias, $.parameterName));

  static JsonParser<Plus> plusF(final JsonParser<Expression<LinearProfile>> linearProfileExpressionP) {
    return productP
        .field("kind", literalP("RealProfilePlus"))
        .field("left", linearProfileExpressionP)
        .field("right", linearProfileExpressionP)
        .map(
            untuple((kind, left, right) -> new Plus(left, right)),
            $ -> tuple(Unit.UNIT, $.left, $.right));
  }

  static JsonParser<Times> timesF(final JsonParser<Expression<LinearProfile>> linearProfileExpressionP) {
    return productP
        .field("kind", literalP("RealProfileTimes"))
        .field("profile", linearProfileExpressionP)
        .field("multiplier", doubleP)
        .map(
            untuple((kind, profile, multiplier) -> new Times(profile, multiplier)),
            $ -> tuple(Unit.UNIT, $.profile, $.multiplier));
  }

  static JsonParser<Rate> rateF(final JsonParser<Expression<LinearProfile>> linearProfileExpressionP) {
    return productP
        .field("kind", literalP("RealProfileRate"))
        .field("profile", linearProfileExpressionP)
        .map(
            untuple((kind, profile) -> new Rate(profile)),
            $ -> tuple(Unit.UNIT, $.profile));
  }

  static <I extends IntervalContainer<I>> JsonParser<AccumulatedDuration<I>> accumulatedDurationF(final JsonParser<Expression<I>> intervalExpressionP) {
    return productP
        .field("kind", literalP("RealProfileAccumulatedDuration"))
        .field("intervalsExpression", intervalExpressionP)
        .field("unit", durationExprP)
        .map(
            untuple((kind, intervals, unit) -> new AccumulatedDuration<>(intervals, unit)),
            $ -> tuple(Unit.UNIT, $.intervals(), $.unit())
        );
  }

  private static JsonParser<Expression<LinearProfile>> linearProfileExprF(JsonParser<Expression<Windows>> windowsP, JsonParser<Expression<Spans>> spansP) {
    return recursiveP(selfP -> chooseP(
        realResourceP,
        realValueP,
        realParameterP,
        plusF(selfP),
        timesF(selfP),
        rateF(selfP),
        assignGapsF(selfP),
        accumulatedDurationF(windowsP),
        accumulatedDurationF(spansP)
    ));
  }

  public static JsonParser<ProfileExpression<?>> profileExpressionF(JsonParser<Expression<Spans>> spansExpressionP, JsonParser<Expression<LinearProfile>> linearProfileExprP) {
    return recursiveP(selfP -> chooseP(

        linearProfileExprP.map(ProfileExpression::new, $ -> $.expression),
        discreteProfileExprF(selfP, spansExpressionP).map(ProfileExpression::new, $ -> $.expression),
        durationExprP.map($ -> new ProfileExpression<>(new DiscreteProfileFromDuration($)), $ -> ((DiscreteProfileFromDuration) $.expression).duration())
    ));
  }

  static JsonParser<Transition> transitionP(JsonParser<ProfileExpression<?>> profileExpressionP, JsonParser<Expression<Spans>> spansExpressionP) {
    return productP
        .field("kind", literalP("DiscreteProfileTransition"))
        .field("profile", discreteProfileExprF(profileExpressionP, spansExpressionP))
        .field("from", serializedValueP)
        .field("to", serializedValueP)
        .map(
            untuple((kind, profile, from, to) -> new Transition(profile, from, to)),
            $ -> tuple(Unit.UNIT, $.profile, $.oldState, $.newState));
  }

  static final JsonParser<ActivityWindow> activityWindowP =
      productP
          .field("kind", literalP("WindowsExpressionActivityWindow"))
          .field("alias", stringP)
          .map(
              untuple((kind, alias) -> new ActivityWindow(alias)),
              $ -> tuple(Unit.UNIT, $.activityAlias));

  static final JsonParser<ActivitySpan> activitySpanP =
      productP
          .field("kind", literalP("SpansExpressionActivitySpan"))
          .field("alias", stringP)
          .map(
              untuple((kind, alias) -> new ActivitySpan(alias)),
              $ -> tuple(Unit.UNIT, $.activityAlias));
  static final JsonParser<StartOf> startOfP =
      productP
          .field("kind", literalP("WindowsExpressionStartOf"))
          .field("alias", stringP)
          .map(
              untuple((kind, alias) -> new StartOf(alias)),
              $ -> tuple(Unit.UNIT, $.activityAlias));

  static final JsonParser<Duration> durationP =
      longP
          .map(
              microseconds -> Duration.of(microseconds, Duration.MICROSECONDS),
              duration -> duration.in(Duration.MICROSECONDS));

  static final JsonParser<IntervalDuration> intervalDurationP =
      productP
          .field("kind", literalP("IntervalDuration"))
          .field("interval", intervalExpressionP)
          .map(
              untuple((kind, interval) -> new IntervalDuration(interval)),
              $ -> tuple(Unit.UNIT, $.interval())
          );

  static final JsonParser<DurationLiteral> durationLiteralP =
      durationP.map(
          DurationLiteral::new,
          DurationLiteral::duration
      );

  static final JsonParser<Expression<Duration>> durationExprP =
      chooseP(intervalDurationP, durationLiteralP);

  static final JsonParser<WindowsValue> windowsValueP =
      productP
          .field("kind", literalP("WindowsExpressionValue"))
          .field("value", boolP)
          .optionalField("interval", intervalExpressionP)
          .map(
              untuple((kind, value, interval) -> new WindowsValue(value, interval)),
              $ -> tuple(Unit.UNIT, $.value(), $.interval())
          );

  static JsonParser<ShiftBy> shiftByF(JsonParser<Expression<Windows>> windowsExpressionP) {
    return productP
        .field("kind", literalP("WindowsExpressionShiftBy"))
        .field("windowExpression", windowsExpressionP)
        .field("fromStart", durationExprP)
        .field("fromEnd", durationExprP)
        .map(
            untuple((kind, windowsExpression, fromStart, fromEnd) -> new ShiftBy(windowsExpression, fromStart, fromEnd)),
            $ -> tuple(Unit.UNIT, $.windows, $.fromStart, $.fromEnd));
  }
  static final JsonParser<EndOf> endOfP =
      productP
          .field("kind", literalP("WindowsExpressionEndOf"))
          .field("alias", stringP)
          .map(
              untuple((kind, alias) -> new EndOf(alias)),
              $ -> tuple(Unit.UNIT, $.activityAlias));

  static <P extends Profile<P>> JsonParser<Equal<P>> equalF(final JsonParser<Expression<P>> expressionParser) {
    return productP
        .field("kind", literalP("ExpressionEqual"))
        .field("left", expressionParser)
        .field("right", expressionParser)
        .map(
            untuple((kind, left, right) -> new Equal<>(left, right)),
            $ -> tuple(Unit.UNIT, $.left, $.right));
  }

  static <P extends Profile<P>> JsonParser<NotEqual<P>> notEqualF(final JsonParser<Expression<P>> expressionParser) {
    return productP
        .field("kind", literalP("ExpressionNotEqual"))
        .field("left", expressionParser)
        .field("right", expressionParser)
        .map(
            untuple((kind, left, right) -> new NotEqual<>(left, right)),
            $ -> tuple(Unit.UNIT, $.left, $.right));
  }

  static JsonParser<LessThan> lessThanF(final JsonParser<Expression<LinearProfile>> linearProfileExpressionP) {
    return productP
        .field("kind", literalP("RealProfileLessThan"))
        .field("left", linearProfileExpressionP)
        .field("right", linearProfileExpressionP)
        .map(
            untuple((kind, left, right) -> new LessThan(left, right)),
            $ -> tuple(Unit.UNIT, $.left, $.right));
  }

  static JsonParser<LongerThan> longerThanP(JsonParser<Expression<Windows>> windowsExpressionP) {
    return productP
            .field("kind", literalP("WindowsExpressionLongerThan"))
            .field("windowExpression", windowsExpressionP)
            .field("duration", durationExprP)
            .map(
                untuple((kind, windowsExpression, duration) -> new LongerThan(windowsExpression, duration)),
                $ -> tuple(Unit.UNIT, $.windows, $.duration));
  }

  static JsonParser<ShorterThan> shorterThanP(JsonParser<Expression<Windows>> windowsExpressionP) {
    return productP
        .field("kind", literalP("WindowsExpressionShorterThan"))
        .field("windowExpression", windowsExpressionP)
        .field("duration", durationExprP)
        .map(
            untuple((kind, windowsExpression, duration) -> new ShorterThan(windowsExpression, duration)),
            $ -> tuple(Unit.UNIT, $.windows, $.duration));
  }

  static JsonParser<LessThanOrEqual> lessThanOrEqualF(final JsonParser<Expression<LinearProfile>> linearProfileExpressionP) {
    return productP
        .field("kind", literalP("RealProfileLessThanOrEqual"))
        .field("left", linearProfileExpressionP)
        .field("right", linearProfileExpressionP)
        .map(
            untuple((kind, left, right) -> new LessThanOrEqual(left, right)),
            $ -> tuple(Unit.UNIT, $.left, $.right));
  }

  static JsonParser<GreaterThan> greaterThanF(final JsonParser<Expression<LinearProfile>> linearProfileExpressionP) {
    return productP
        .field("kind", literalP("RealProfileGreaterThan"))
        .field("left", linearProfileExpressionP)
        .field("right", linearProfileExpressionP)
        .map(
            untuple((kind, left, right) -> new GreaterThan(left, right)),
            $ -> tuple(Unit.UNIT, $.left, $.right));
  }

  static JsonParser<GreaterThanOrEqual> greaterThanOrEqualF(final JsonParser<Expression<LinearProfile>> linearProfileExpressionP) {
    return productP
        .field("kind", literalP("RealProfileGreaterThanOrEqual"))
        .field("left", linearProfileExpressionP)
        .field("right", linearProfileExpressionP)
        .map(
            untuple((kind, left, right) -> new GreaterThanOrEqual(left, right)),
            $ -> tuple(Unit.UNIT, $.left, $.right));
  }

  static JsonParser<And> andF(final JsonParser<Expression<Windows>> windowsExpressionP) {
    return productP
        .field("kind", literalP("WindowsExpressionAnd"))
        .field("expressions", listP(windowsExpressionP))
        .map(
            untuple((kind, expressions) -> new And(expressions)),
            $ -> tuple(Unit.UNIT, $.expressions));
  }

  static JsonParser<Or> orF(final JsonParser<Expression<Windows>> windowsExpressionP) {
    return productP
        .field("kind", literalP("WindowsExpressionOr"))
        .field("expressions", listP(windowsExpressionP))
        .map(
            untuple((kind, expressions) -> new Or(expressions)),
            $ -> tuple(Unit.UNIT, $.expressions));
  }

  static JsonParser<Not> notF(final JsonParser<Expression<Windows>> windowsExpressionP) {
    return productP
        .field("kind", literalP("WindowsExpressionNot"))
        .field("expression", windowsExpressionP)
        .map(
            untuple((kind, expr) -> new Not(expr)),
            $ -> tuple(Unit.UNIT, $.expression));
  }

  static <I extends IntervalContainer<I>> JsonParser<Starts<I>> startsF(final JsonParser<Expression<I>> intervalExpressionP) {
    return productP
        .field("kind", literalP("IntervalsExpressionStarts"))
        .field("expression", intervalExpressionP)
        .map(
            untuple((kind, expr) -> new Starts<I>(expr)),
            $ -> tuple(Unit.UNIT, $.expression));
  }

  static <I extends IntervalContainer<I>> JsonParser<Ends<I>> endsF(final JsonParser<Expression<I>> intervalsExpressionP) {
    return productP
        .field("kind", literalP("IntervalsExpressionEnds"))
        .field("expression", intervalsExpressionP)
        .map(
            untuple((kind, expr) -> new Ends<I>(expr)),
            $ -> tuple(Unit.UNIT, $.expression));
  }

  static <I extends IntervalContainer<I>> JsonParser<Split<I>> splitF(final JsonParser<Expression<I>> intervalExpressionP) {
    return productP
        .field("kind", literalP("SpansExpressionSplit"))
        .field("intervals", intervalExpressionP)
        .field("numberOfSubIntervals", intP)
        .field("internalStartInclusivity", inclusivityP)
        .field("internalEndInclusivity", inclusivityP)
        .map(
            untuple((kind, expr, numberOfSubWindows, internalStartInclusivity, internalEndInclusivity) -> new Split<I>(
                expr, numberOfSubWindows, internalStartInclusivity, internalEndInclusivity)
            ),
            $ -> tuple(Unit.UNIT, $.intervals, $.numberOfSubIntervals, $.internalStartInclusivity, $.internalEndInclusivity));
  }

  static JsonParser<WindowsFromSpans> windowsFromSpansF(final JsonParser<Expression<Spans>> spansExpressionP) {
    return productP
        .field("kind", literalP("WindowsExpressionFromSpans"))
        .field("spansExpression", spansExpressionP)
        .map(
            untuple((kind, expr) -> new WindowsFromSpans(expr)),
            $ -> tuple(Unit.UNIT, $.expression()));
  }

  static JsonParser<ForEachActivitySpans> forEachActivitySpansF(final JsonParser<Expression<Spans>> spansExpressionP) {
    return productP
        .field("kind", literalP("ForEachActivitySpans"))
        .field("activityType", stringP)
        .field("alias", stringP)
        .field("expression", spansExpressionP)
        .map(
            untuple((kind, actType, alias, expression) -> new ForEachActivitySpans(actType, alias, expression)),
            $ -> tuple(Unit.UNIT, $.activityType, $.alias, $.expression));
  }

  static JsonParser<ForEachActivityViolations> forEachActivityViolationsF(final JsonParser<Expression<List<Violation>>> violationListExpressionP) {
    return productP
        .field("kind", literalP("ForEachActivityViolations"))
        .field("activityType", stringP)
        .field("alias", stringP)
        .field("expression", violationListExpressionP)
        .map(
            untuple((kind, actType, alias, expression) -> new ForEachActivityViolations(actType, alias, expression)),
            $ -> tuple(Unit.UNIT, $.activityType, $.alias, $.expression));
  }

  public static JsonParser<ValueAt<?>> valueAtExpressionF(JsonParser<ProfileExpression<?>> profileExpressionP, JsonParser<Expression<Spans>> spansExpressionP) {
    return productP
        .field("kind", literalP("ValueAtExpression"))
        .field("profile", profileExpressionP)
        .field("timepoint", spansExpressionP)
        .map(
            untuple((kind, profile, timepoint) -> new ValueAt<>(profile, timepoint)),
            $ -> tuple(Unit.UNIT, $.profile(), $.timepoint()));
  }

  static JsonParser<Changes<?>> changesF(JsonParser<Expression<Spans>> spansExpressionP, JsonParser<Expression<LinearProfile>> linearProfileExprP) {
    return productP
        .field("kind", literalP("ProfileChanges"))
        .field("expression", profileExpressionF(spansExpressionP, linearProfileExprP))
        .map(
            untuple((kind, expression) -> new Changes<>(expression)),
            $ -> tuple(Unit.UNIT, $.expression));
  }

  private static JsonParser<Expression<Windows>> windowsExpressionF(JsonParser<Expression<Spans>> spansP, JsonParser<Expression<LinearProfile>> linearProfileExprP) {
    return recursiveP(selfP -> chooseP(
        windowsValueP,
        startOfP,
        endOfP,
        changesF(spansP, linearProfileExprP),
        lessThanF(linearProfileExprP),
        lessThanOrEqualF(linearProfileExprP),
        greaterThanF(linearProfileExprP),
        greaterThanOrEqualF(linearProfileExprP),
        longerThanP(selfP),
        shorterThanP(selfP),
        transitionP(profileExpressionF(spansP, linearProfileExprP), spansP),
        equalF(linearProfileExprP),
        equalF(discreteProfileExprF(profileExpressionF(spansP, linearProfileExprP), spansP)),
        notEqualF(linearProfileExprP),
        notEqualF(discreteProfileExprF(profileExpressionF(spansP, linearProfileExprP), spansP)),
        andF(selfP),
        orF(selfP),
        notF(selfP),
        shiftByF(selfP),
        startsF(selfP),
        endsF(selfP),
        windowsFromSpansF(spansP),
        activityWindowP,
        assignGapsF(selfP)
    ));
  }

  static JsonParser<SpansFromWindows> spansFromWindowsF(JsonParser<Expression<Windows>> windowsExpressionP) {
    return productP
        .field("kind", literalP("SpansExpressionFromWindows"))
        .field("windowsExpression", windowsExpressionP)
        .map(
            untuple((kind, expr) -> new SpansFromWindows(expr)),
            $ -> tuple(Unit.UNIT, $.expression()));
  }

  private static final JsonParser<SpansInterval> spansIntervalP =
      productP
          .field("kind", literalP("SpansExpressionInterval"))
          .field("interval", intervalExpressionP)
          .map(
              untuple((kind, interval) -> new SpansInterval(interval)),
              $ -> tuple(Unit.UNIT, $.interval())
          );

  private static JsonParser<Expression<Spans>> spansExpressionF(JsonParser<Expression<Windows>> windowsP) {
      return recursiveP(selfP -> chooseP(
          spansIntervalP,
          startsF(selfP),
          endsF(selfP),
          splitF(selfP),
          splitF(windowsP),
          spansFromWindowsF(windowsP),
          forEachActivitySpansF(selfP),
          activitySpanP
          ));
  }

  public static final JsonParser<Expression<Windows>> windowsExpressionP = recursiveP(selfP -> windowsExpressionF(spansExpressionF(selfP), linearProfileExprF(selfP, spansExpressionF(selfP))));
  public static final JsonParser<Expression<LinearProfile>> linearProfileExprP = recursiveP(selfP -> linearProfileExprF(windowsExpressionP, spansExpressionF(windowsExpressionP)));
  public static final JsonParser<Expression<Spans>> spansExpressionP = recursiveP(selfP -> spansExpressionF(windowsExpressionF(selfP, linearProfileExprP)));
  public static final JsonParser<ProfileExpression<?>> profileExpressionP = profileExpressionF(spansExpressionP, linearProfileExprP);
  public static final JsonParser<Expression<DiscreteProfile>> discreteProfileExprP = discreteProfileExprF(profileExpressionP, spansExpressionP);

  static final JsonParser<ViolationsOfWindows> violationsOfP =
      productP
          .field("kind", literalP("ViolationsOf"))
          .field("expression", windowsExpressionP)
          .map(
              untuple((kind, expression) -> new ViolationsOfWindows(expression)),
              $ -> tuple(Unit.UNIT, $.expression));

  public static final JsonParser<Expression<List<Violation>>> constraintP =
      recursiveP(selfP -> chooseP(
          forEachActivityViolationsF(selfP),
          windowsExpressionP.map(ViolationsOfWindows::new, $ -> $.expression),
          violationsOfP));
}
