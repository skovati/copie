package gov.nasa.jpl.aerie.constraints.time;

import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Objects;

import static gov.nasa.jpl.aerie.constraints.time.Interval.Inclusivity.Exclusive;
import static gov.nasa.jpl.aerie.constraints.time.Interval.Inclusivity.Inclusive;

/**
 * An Interval on the timeline, represented by start and end points
 * and start and end inclusivity.
 */
public final class Interval implements Comparable<Interval>{
  // If end.shorterThan(start), this is the empty interval.
  // If end.equals(start), this is a single point.
  // If end.longerThan(start), this is a closed interval.
  // We don't seem to be alone in this representation -- the interval arithmetic library Gaol
  //   represents empty intervals in the same way.
  public final Duration start;
  public final Duration end;
  public final Inclusivity startInclusivity;
  public final Inclusivity endInclusivity;

  public enum Inclusivity {
    Inclusive,
    Exclusive;

    public Inclusivity opposite() {
      return (this == Inclusive) ? Exclusive : Inclusive;
    }

    public boolean moreRestrictiveThan(final Inclusivity other) {
      return this == Exclusive && other == Inclusive;
    }
  }

  public boolean includesStart() {
    return (this.startInclusivity == Inclusive);
  }

  public boolean includesEnd() {
    return (this.endInclusivity == Inclusive);
  }

  private Interval(
      final Duration start,
      final Inclusivity startInclusivity,
      final Duration end,
      final Inclusivity endInclusivity) {
    this.start = Objects.requireNonNull(start);
    this.end = Objects.requireNonNull(end);
    this.startInclusivity = startInclusivity;
    this.endInclusivity = endInclusivity;
  }

  private Interval(final Duration start, final Duration end) {
    this(start, Inclusive, end, Inclusive);
  }

  /**
   * Constructs an interval between two durations based on a common instant.
   *
   * @param start The starting time of the interval.
   * @param end The ending time of the interval.
   * @return A non-empty interval if start &le; end, or an empty interval otherwise.
   */
  public static Interval between(
      final Duration start,
      final Inclusivity startInclusivity,
      final Duration end,
      final Inclusivity endInclusivity
  ) {
    return (end.shorterThan(start))
        ? Interval.EMPTY
        : new Interval(start, startInclusivity, end, endInclusivity);
  }

  public static Interval between(final Duration start, final Duration end) {
    return between(start, Inclusive, end, Inclusive);
  }

  public static Interval between(
      final long start,
      final Inclusivity startInclusivity,
      final long end,
      final Inclusivity endInclusivity,
      final Duration unit
  ) {
    return between(Duration.of(start, unit), startInclusivity, Duration.of(end, unit), endInclusivity);
  }

  public static Interval between(final long start, final long end, final Duration unit) {
    return between(start, Inclusive, end, Inclusive, unit);
  }

  public static Interval interval(
      final Duration start,
      final Inclusivity startInclusivity,
      final Duration end,
      final Inclusivity endInclusivity
  ) {
    return between(start, startInclusivity, end, endInclusivity);
  }

  public static Interval interval(final Duration start, final Duration end) {
    return interval(start, Inclusive, end, Inclusive);
  }

  public static Interval interval(
      final long start,
      final Inclusivity startInclusivity,
      final long end,
      final Inclusivity endInclusivity,
      final Duration unit
  ) {
    return between(start, startInclusivity, end, endInclusivity, unit);
  }

  public static Interval interval(final long start, final long end, final Duration unit) {
    return interval(start, Inclusive, end, Inclusive, unit);
  }

  public static Interval at(final Duration point) {
    return new Interval(point, Inclusive, point, Inclusive);
  }

  public static Interval at(final long quantity, final Duration unit) {
    return at(Duration.of(quantity, unit));
  }

  public static final Interval EMPTY = new Interval(Duration.ZERO, Duration.ZERO.minus(Duration.EPSILON));
  public static final Interval FOREVER = new Interval(Duration.MIN_VALUE, Duration.MAX_VALUE);

  public boolean isEmpty() {
    if (this.end.shorterThan(this.start)) return true;
    if (this.end.longerThan(this.start)) return false;

    return !(this.includesStart() && this.includesEnd());
  }

  // Use this instead of `.duration().isZero()` to avoid overflow on long intervals.
  public boolean isPoint() {
    return
        this.includesStart() &&
        this.includesEnd() &&
        this.start == this.end;
  }

  public Duration duration() {
    if (this.isEmpty()) return Duration.ZERO;
    return this.end.minus(this.start);
  }

  public static Interval intersect(final Interval x, final Interval y) {
    final Duration start;
    final Inclusivity startInclusivity;

    if (x.start.longerThan(y.start)) {
      start = x.start;
      startInclusivity = x.startInclusivity;
    } else if (y.start.longerThan(x.start)) {
      start = y.start;
      startInclusivity = y.startInclusivity;
    } else {
      start = x.start;
      startInclusivity = (x.includesStart() && y.includesStart()) ? Inclusive : Exclusive;
    }

    final Duration end;
    final Inclusivity endInclusivity;
    if (x.end.shorterThan(y.end)) {
      end = x.end;
      endInclusivity = x.endInclusivity;
    } else if (y.end.shorterThan(x.end)) {
      end = y.end;
      endInclusivity = y.endInclusivity;
    } else {
      end = x.end;
      endInclusivity = (x.includesEnd() && y.includesEnd()) ? Inclusive : Exclusive;
    }

    return Interval.between(start, startInclusivity, end, endInclusivity);
  }

  public static Interval unify(final Interval x, final Interval y) {
    if (x.isEmpty()) return y;
    if (y.isEmpty()) return x;

    final Duration start;
    final Inclusivity startInclusivity;

    if (x.start.shorterThan(y.start)) {
      start = x.start;
      startInclusivity = x.startInclusivity;
    } else if (y.start.shorterThan(x.start)) {
      start = y.start;
      startInclusivity = y.startInclusivity;
    } else {
      start = x.start;
      startInclusivity = (x.includesStart() || y.includesStart()) ? Inclusive : Exclusive;
    }

    final Duration end;
    final Inclusivity endInclusivity;
    if (x.end.longerThan(y.end)) {
      end = x.end;
      endInclusivity = x.endInclusivity;
    } else if (y.end.longerThan(x.end)) {
      end = y.end;
      endInclusivity = y.endInclusivity;
    } else {
      end = x.end;
      endInclusivity = (x.includesEnd() || y.includesEnd()) ? Inclusive : Exclusive;
    }

    return Interval.between(start, startInclusivity, end, endInclusivity);
  }

  public boolean isStrictlyAfter(Interval x){
    return compareStartToEnd(this, x) > 0;
  }

  public boolean isStrictlyBefore(Interval x){
    return compareEndToStart(this,x) < 0;
  }

  public boolean contains(Duration d){
    return !intersect(this, at(d)).isEmpty();
  }

  public boolean contains(Interval x){
    return intersect(this,x).equals(x);
  }

  public boolean isSingleton(){
    return this.start.isEqualTo(this.end);
  }

  public static Interval betweenClosedOpen(final Duration start, final Duration end) {
    return between(start, Inclusive, end, Exclusive);
  }

  public boolean adjacent(Interval x){
    return meets(x,this) || meets(this,x);
  }

  @Override
  public int compareTo(final Interval o) {
    return start.compareTo(o.start);
  }

  public static int compareStartToStart(final Interval x, final Interval y) {
    // First, order by absolute time.
    if (!x.start.isEqualTo(y.start)) {
      return x.start.compareTo(y.start);
    }

    // Second, order by whichever one includes the point.
    if (x.includesStart() != y.includesStart()) {
      return (x.includesStart()) ? -1 : 1;
    }

    return 0;
  }

  public static int compareEndToEnd(final Interval x, final Interval y) {
    // First, order by absolute time.
    if (!x.end.isEqualTo(y.end)) {
      return x.end.compareTo(y.end);
    }

    // Second, order by whichever one includes the point
    if (x.includesEnd() != y.includesEnd()) {
      return (x.includesEnd()) ? 1 : -1;
    }

    return 0;
  }

  public static int compareStartToEnd(final Interval x, final Interval y) {
    // First, order by absolute time.
    if (!x.start.isEqualTo(y.end)) {
      return x.start.compareTo(y.end);
    }

    // Second, order by inclusivity
    if (!y.includesEnd()) return 1;
    if (!x.includesStart()) return 1;

    return 0;
  }

  public static int compareEndToStart(final Interval x, final Interval y) {
    return -compareStartToEnd(y, x);
  }

  public static boolean meets(final Interval x, final Interval y) {
    return (x.end.isEqualTo(y.start)) && (x.endInclusivity != y.startInclusivity);
  }

  public static boolean metBy(final Interval x, final Interval y) {
    return meets(y, x);
  }

  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof final Interval other)) return false;

    return ( (this.isEmpty() && other.isEmpty())
             || ( Objects.equals(this.start, other.start)
                  && Objects.equals(this.startInclusivity, other.startInclusivity)
                  && Objects.equals(this.end, other.end)
                  && Objects.equals(this.endInclusivity, other.endInclusivity) ) );
  }

  @Override
  public int hashCode() {
    return (this.isEmpty()) ? Objects.hash(0L, -1L) : Objects.hash(this.start, this.startInclusivity, this.end, this.endInclusivity);
  }

  @Override
  public String toString() {
    if (this.isEmpty()) {
      return "Interval(empty)";
    } else {
      return String.format(
          "Interval%s%s, %s%s",
          this.includesStart() ? "[" : "(",
          this.start,
          this.end,
          this.includesEnd() ? "]" : ")"
      );
    }
  }
}
