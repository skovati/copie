package gov.nasa.jpl.aerie.scheduler.constraints.timeexpressions;

import gov.nasa.jpl.aerie.constraints.model.SimulationResults;
import gov.nasa.jpl.aerie.constraints.time.Interval;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.scheduler.TimeUtility;
import gov.nasa.jpl.aerie.scheduler.constraints.TimeRangeExpression;
import gov.nasa.jpl.aerie.scheduler.model.Plan;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * class allowing to define dynamic expressions of timepoints, relative to time anchors
 */
public abstract class TimeExpression {

  /**
   * A TimeExpression must implement this method
   *
   * @param plan the current plan
   * @param interval the range on which the relative time expression must be computed
   * @return a range of valid times satisfying the expression
   */
  public abstract Interval computeTime(final SimulationResults simulationResults, final Plan plan, final Interval interval);


  protected final List<Pair<TimeUtility.Operator, Duration>> operations = new ArrayList<>();


  public static TimeExpression fromAnchor(TimeAnchor anchor) {
    return new TimeExpressionRelativeFixed(anchor, true);
  }

  public void addOperation(final TimeUtility.Operator operator, final Duration operand) {
    this.operations.add(Pair.of(operator, operand));
  }

  /**
   * Builder allowing to create Latching time expression
   */
  public static class LatchingBuilder {

    TimeExpression expr1;
    TimeExpression expr2;
    TimeRangeExpression expr;

    LatchingBuilder withinEach(TimeRangeExpression expr) {
      this.expr = expr;
      return this;
    }

    LatchingBuilder first(TimeExpression filter) {
      expr1 = filter;
      return this;
    }

    LatchingBuilder andThen(TimeExpression filter) {
      expr2 = filter;
      return this;
    }

    TimeExpression build() {
      return new TimeExpressionLatching(expr1, expr2, expr);
    }


  }

  public static TimeExpression atStart() {
    return new TimeExpressionRelativeFixed(TimeAnchor.START, true);
  }

  public static TimeExpression offsetByAfterStart(Duration dur) {
    TimeExpression te = new TimeExpressionRelativeFixed(TimeAnchor.START, true);
    te.operations.add(Pair.of(TimeUtility.Operator.PLUS, dur));
    return te;
  }


  public static TimeExpression offsetByBeforeStart(Duration dur) {
    TimeExpression te = new TimeExpressionRelativeFixed(TimeAnchor.START, true);
    te.operations.add(Pair.of(TimeUtility.Operator.MINUS, dur));
    return te;
  }


  public static TimeExpression offsetByAfterEnd(Duration dur) {
    TimeExpression te = new TimeExpressionRelativeFixed(TimeAnchor.END, true);
    te.operations.add(Pair.of(TimeUtility.Operator.PLUS, dur));
    return te;
  }


  public static TimeExpression offsetByBeforeEnd(Duration dur) {
    TimeExpression te = new TimeExpressionRelativeFixed(TimeAnchor.END, true);
    te.operations.add(Pair.of(TimeUtility.Operator.MINUS, dur));
    return te;
  }

  public static TimeExpression beforeEnd() {
    TimeExpression te = new TimeExpressionRelativeFixed(TimeAnchor.END, false);
    te.operations.add(Pair.of(TimeUtility.Operator.MINUS, Duration.MAX_VALUE));
    return te;
  }

  public static TimeExpression beforeStart() {
    TimeExpression te = new TimeExpressionRelativeFixed(TimeAnchor.START, false);
    te.operations.add(Pair.of(TimeUtility.Operator.MINUS,Duration.MAX_VALUE));
    return te;
  }

  public static TimeExpression afterEnd() {
    TimeExpression te = new TimeExpressionRelativeFixed(TimeAnchor.END, false);
    te.operations.add(Pair.of(TimeUtility.Operator.PLUS, Duration.MAX_VALUE));
    return te;
  }

  public static TimeExpression afterStart() {
    TimeExpression te = new TimeExpressionRelativeFixed(TimeAnchor.START, false);
    te.operations.add(Pair.of(TimeUtility.Operator.PLUS, Duration.MAX_VALUE));
    return te;
  }

  public static TimeExpression endsBefore(TimeExpression expr){
    //te.operations.add(Pair.of(Time.Operator.MINUS,Duration.MAX_VALUE));
    return new TimeExpressionBefore(expr, DEF_NAME);
  }


  public static final String DEF_NAME = "NO_NAME_TIME_EXPR";

  public static class Builder {
    private boolean interval = false;
    private String name = DEF_NAME;

    public Builder getThis() {
      return this;
    }


    public Builder name(String name) {
      this.name = name;
      return getThis();
    }

    public Builder from(TimeExpression otherExpr) {
      fromExpression = otherExpr;
      return getThis();
    }

    TimeExpression fromExpression;

    public Builder from(TimeAnchor anchor) {
      fromAnchor = anchor;
      return getThis();
    }

    TimeAnchor fromAnchor;

    public Builder minus(Duration dur) {
      operations.add(Pair.of(TimeUtility.Operator.MINUS, dur));
      return getThis();
    }

    public Builder interval() {
      this.interval = true;
      return getThis();
    }

    public Builder plus(Duration dur) {
      operations.add(Pair.of(TimeUtility.Operator.PLUS, dur));
      return getThis();
    }

    protected final List<Pair<TimeUtility.Operator, Duration>> operations = new ArrayList<>();

    public TimeExpression build() {
      if (fromExpression != null) {
        var expr = new TimeExpressionFromExpr(fromExpression, name);
        expr.operations.addAll(operations);
        return expr;
      } else if (fromAnchor != null) {
        var expr = new TimeExpressionRelativeFixed(fromAnchor, interval);
        expr.operations.addAll(operations);
        return expr;
      } else {
        throw new RuntimeException("Time expression must either be another time expression or a time anchor");
      }

    }

  }

}
