package gov.nasa.jpl.aerie.scheduler.constraints.timeexpressions;

import gov.nasa.jpl.aerie.constraints.model.SimulationResults;
import gov.nasa.jpl.aerie.constraints.time.Interval;
import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.scheduler.TimeUtility;
import gov.nasa.jpl.aerie.scheduler.model.Plan;

public class TimeExpressionRelativeFixed extends TimeExpression {

  protected final TimeAnchor anchor;
  protected boolean fixed = true;

  public TimeExpressionRelativeFixed(final TimeAnchor anchor, final boolean fixed) {
    this.fixed = fixed;
    this.anchor = anchor;
  }

  @Override
  public Interval computeTime(final SimulationResults simulationResults, final Plan plan, final Interval interval) {
    Duration from = null;
    if (anchor == TimeAnchor.START) {
      from = interval.start;
    } else if (anchor == TimeAnchor.END) {
      from = interval.end;
    }

    Duration res = from;
    for (final var entry : this.operations) {
      res = TimeUtility.performOperation(entry.getKey(), res, entry.getValue());
    }

    Interval retRange;

    //if we want an range of possibles
    if (!fixed) {
      if (res.compareTo(from) > 0) {
        retRange = Interval.between(from, res);

      } else {
        retRange = Interval.between(res, from);
      }
      // we just want to compute the absolute timepoint
    } else {
      retRange = Interval.between(res, res);
    }

    return retRange;
  }
}
