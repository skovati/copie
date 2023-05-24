/// <reference path="./TemporalPolyfillTypes.ts"; />
import type * as API from "./constraints-edsl-fluent-api";

export enum NodeKind {
  StructProfileExpression = 'StructProfileExpression',
  ListProfileExpression = 'ListProfileExpression',
  ValueAtExpression = 'ValueAtExpression',
  AssignGapsExpression = 'AssignGapsExpression',
  DiscreteProfileResource = 'DiscreteProfileResource',
  DiscreteProfileValue = 'DiscreteProfileValue',
  DiscreteProfileParameter = 'DiscreteProfileParameter',
  RealProfileResource = 'RealProfileResource',
  RealProfileValue = 'RealProfileValue',
  RealProfileParameter = 'RealProfileParameter',
  RealProfilePlus = 'RealProfilePlus',
  RealProfileTimes = 'RealProfileTimes',
  RealProfileRate = 'RealProfileRate',
  RealProfileAccumulatedDuration = 'RealProfileAccumulatedDuration',
  DiscreteProfileTransition = 'DiscreteProfileTransition',
  WindowsExpressionActivityWindow = 'WindowsExpressionActivityWindow',
  SpansExpressionActivitySpan = 'SpansExpressionActivitySpan',
  WindowsExpressionValue = 'WindowsExpressionValue',
  WindowsExpressionStartOf = 'WindowsExpressionStartOf',
  WindowsExpressionEndOf = 'WindowsExpressionEndOf',
  WindowsExpressionLongerThan = 'WindowsExpressionLongerThan',
  WindowsExpressionShorterThan = 'WindowsExpressionShorterThan',
  WindowsExpressionShiftBy = 'WindowsExpressionShiftBy',
  WindowsExpressionFromSpans = 'WindowsExpressionFromSpans',
  SpansExpressionFromWindows = 'SpansExpressionFromWindows',
  SpansExpressionSplit = 'SpansExpressionSplit',
  SpansExpressionInterval = 'SpansExpressionInterval',
  ExpressionEqual = 'ExpressionEqual',
  ExpressionNotEqual = 'ExpressionNotEqual',
  RealProfileLessThan = 'RealProfileLessThan',
  RealProfileLessThanOrEqual = 'RealProfileLessThanOrEqual',
  RealProfileGreaterThan = 'RealProfileGreaterThan',
  RealProfileGreaterThanOrEqual = 'RealProfileGreaterThanOrEqual',
  WindowsExpressionAnd = 'WindowsExpressionAnd',
  WindowsExpressionOr = 'WindowsExpressionOr',
  WindowsExpressionNot = 'WindowsExpressionNot',
  IntervalsExpressionStarts = 'IntervalsExpressionStarts',
  IntervalsExpressionEnds = 'IntervalsExpressionEnds',
  ForEachActivitySpans = 'ForEachActivitySpans',
  ForEachActivityViolations = 'ForEachActivityViolations',
  ProfileChanges = 'ProfileChanges',
  ViolationsOf = 'ViolationsOf',
  AbsoluteInterval = 'AbsoluteInterval',
  IntervalAlias = 'IntervalAlias',
  IntervalDuration = 'IntervalDuration'
}

export type Constraint = ViolationsOf | WindowsExpression | SpansExpression | ForEachActivityConstraints;

export interface ViolationsOf {
  kind: NodeKind.ViolationsOf;
  expression: WindowsExpression;
}

export interface ForEachActivityConstraints {
  kind: NodeKind.ForEachActivityViolations;
  activityType: string;
  alias: string;
  expression: Constraint;
}

export interface ForEachActivitySpans {
  kind: NodeKind.ForEachActivitySpans;
  activityType: string;
  alias: string;
  expression: SpansExpression;
}

export interface AssignGapsExpression<P extends ProfileExpression> {
  kind: NodeKind.AssignGapsExpression,
  originalProfile: P,
  defaultProfile: P
}

export type WindowsExpression =
  | WindowsExpressionValue
  | WindowsExpressionActivityWindow
  | WindowsExpressionStartOf
  | WindowsExpressionEndOf
  | ProfileChanges
  | RealProfileLessThan
  | RealProfileLessThanOrEqual
  | RealProfileGreaterThan
  | RealProfileGreaterThanOrEqual
  | DiscreteProfileTransition
  | ExpressionEqual<RealProfileExpression>
  | ExpressionEqual<DiscreteProfileExpression>
  | ExpressionNotEqual<RealProfileExpression>
  | ExpressionNotEqual<DiscreteProfileExpression>
  | WindowsExpressionAnd
  | WindowsExpressionOr
  | WindowsExpressionLongerThan
  | WindowsExpressionShorterThan
  | WindowsExpressionNot
  | WindowsExpressionShiftBy
  | WindowsExpressionFromSpans
  | IntervalsExpressionStarts
  | IntervalsExpressionEnds
  | AssignGapsExpression<WindowsExpression>;

export type SpansExpression =
  | SpansExpressionActivitySpan
  | SpansExpressionSplit
  | IntervalsExpressionStarts
  | IntervalsExpressionEnds
  | SpansExpressionFromWindows
  | ForEachActivitySpans
  | SpansExpressionInterval;

export type IntervalsExpression =
  | WindowsExpression
  | SpansExpression;

export interface ProfileChanges {
  kind: NodeKind.ProfileChanges;
  expression: ProfileExpression;
}

export interface WindowsExpressionValue {
  kind: NodeKind.WindowsExpressionValue,
  value: boolean,
  interval?: IntervalExpression
}

export interface WindowsExpressionNot {
  kind: NodeKind.WindowsExpressionNot;
  expression: WindowsExpression;
}

export interface WindowsExpressionShiftBy {
  kind: NodeKind.WindowsExpressionShiftBy,
  windowExpression: WindowsExpression,
  fromStart: Duration,
  fromEnd: Duration,
}

export interface WindowsExpressionOr {
  kind: NodeKind.WindowsExpressionOr;
  expressions: WindowsExpression[];
}

export interface WindowsExpressionAnd {
  kind: NodeKind.WindowsExpressionAnd;
  expressions: WindowsExpression[];
}

export interface RealProfileGreaterThanOrEqual {
  kind: NodeKind.RealProfileGreaterThanOrEqual;
  left: RealProfileExpression;
  right: RealProfileExpression;
}

export interface RealProfileGreaterThan {
  kind: NodeKind.RealProfileGreaterThan;
  left: RealProfileExpression;
  right: RealProfileExpression;
}

export interface RealProfileLessThanOrEqual {
  kind: NodeKind.RealProfileLessThanOrEqual;
  left: RealProfileExpression;
  right: RealProfileExpression;
}

export interface RealProfileLessThan {
  kind: NodeKind.RealProfileLessThan;
  left: RealProfileExpression;
  right: RealProfileExpression;
}

export interface ExpressionNotEqual<T extends ProfileExpression> {
  kind: NodeKind.ExpressionNotEqual;
  left: T;
  right: T;
}

export interface ExpressionEqual<T extends ProfileExpression> {
  kind: NodeKind.ExpressionEqual;
  left: T;
  right: T;
}

export interface WindowsExpressionEndOf {
  kind: NodeKind.WindowsExpressionEndOf;
  alias: string;
}

export interface WindowsExpressionStartOf {
  kind: NodeKind.WindowsExpressionStartOf;
  alias: string;
}

export interface WindowsExpressionActivityWindow {
  kind: NodeKind.WindowsExpressionActivityWindow;
  alias: string;
}

export interface SpansExpressionActivitySpan {
  kind: NodeKind.SpansExpressionActivitySpan;
  alias: string;
}

export interface WindowsExpressionShorterThan {
  kind: NodeKind.WindowsExpressionShorterThan,
  windowExpression: WindowsExpression,
  duration: Duration
}

export interface WindowsExpressionLongerThan {
  kind: NodeKind.WindowsExpressionLongerThan,
  windowExpression: WindowsExpression,
  duration: Duration
}

export interface SpansExpressionSplit {
  kind: NodeKind.SpansExpressionSplit,
  intervals: IntervalsExpression,
  numberOfSubIntervals: number,
  internalStartInclusivity: API.Inclusivity,
  internalEndInclusivity: API.Inclusivity
}

export interface SpansExpressionInterval {
  kind: NodeKind.SpansExpressionInterval,
  interval: IntervalExpression
}

export interface WindowsExpressionFromSpans {
  kind: NodeKind.WindowsExpressionFromSpans,
  spansExpression: SpansExpression
}

export interface SpansExpressionFromWindows {
  kind: NodeKind.SpansExpressionFromWindows,
  windowsExpression: WindowsExpression
}

export interface IntervalsExpressionStarts {
  kind: NodeKind.IntervalsExpressionStarts,
  expression: IntervalsExpression
}

export interface IntervalsExpressionEnds {
  kind: NodeKind.IntervalsExpressionEnds,
  expression: IntervalsExpression
}

export interface DiscreteProfileTransition {
  kind: NodeKind.DiscreteProfileTransition;
  profile: DiscreteProfileExpression;
  from: any;
  to: any;
}

export type ProfileExpression = WindowsExpression | RealProfileExpression | DiscreteProfileExpression;

export type RealProfileExpression =
  | RealProfileRate
  | RealProfileTimes
  | RealProfilePlus
  | RealProfileResource
  | RealProfileValue
  | RealProfileParameter
  | AssignGapsExpression<RealProfileExpression>
  | RealProfileAccumulatedDuration;

export interface StructProfileExpression {
  kind: NodeKind.StructProfileExpression,
  expressions: {[key:string]: DiscreteProfileExpression}
}

export interface ListProfileExpression {
  kind: NodeKind.ListProfileExpression,
  expressions: DiscreteProfileExpression[]
}

export interface ValueAtExpression{
  kind: NodeKind.ValueAtExpression,
  profile: ProfileExpression,
  timepoint: SpansExpression
}

export interface RealProfileRate {
  kind: NodeKind.RealProfileRate;
  profile: RealProfileExpression;
}

export interface RealProfileTimes {
  kind: NodeKind.RealProfileTimes;
  profile: RealProfileExpression;
  multiplier: number;
}

export interface RealProfilePlus {
  kind: NodeKind.RealProfilePlus;
  left: RealProfileExpression;
  right: RealProfileExpression;
}

export interface RealProfileResource {
  kind: NodeKind.RealProfileResource;
  name: string;
}

export interface RealProfileValue {
  kind: NodeKind.RealProfileValue;
  value: number;
  rate: number;
  interval?: IntervalExpression;
}

export interface RealProfileParameter {
  kind: NodeKind.RealProfileParameter;
  alias: string;
  name: string;
}

export interface RealProfileAccumulatedDuration {
  kind: NodeKind.RealProfileAccumulatedDuration,
  intervalsExpression: IntervalsExpression,
  unit: Duration
}

export type DiscreteProfileExpression =
    | DiscreteProfileResource
    | DiscreteProfileValue
    | DiscreteProfileParameter
    | AssignGapsExpression<DiscreteProfileExpression>
    | StructProfileExpression
    | ListProfileExpression
    | ValueAtExpression
    | IntervalDuration;

export interface DiscreteProfileResource {
  kind: NodeKind.DiscreteProfileResource;
  name: string;
}

export interface DiscreteProfileValue {
  kind: NodeKind.DiscreteProfileValue;
  value: any;
  interval?: IntervalExpression;
}

export interface DiscreteProfileParameter {
  kind: NodeKind.DiscreteProfileParameter;
  alias: string;
  name: string;
}

export type IntervalExpression =
  | AbsoluteInterval
  | IntervalAlias;

export interface AbsoluteInterval {
  kind: NodeKind.AbsoluteInterval;
  start?: Temporal.Instant;
  end?: Temporal.Instant;
  startInclusivity?: API.Inclusivity;
  endInclusivity?: API.Inclusivity;
}

export interface IntervalAlias {
  kind: NodeKind.IntervalAlias;
  alias: string;
}

export type Duration =
  | Temporal.Duration
  | IntervalDuration;

export interface IntervalDuration {
  kind: NodeKind.IntervalDuration;
  interval: IntervalExpression
}
