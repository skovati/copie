package gov.nasa.jpl.aerie.scheduler.server.remotes.postgres;

import gov.nasa.jpl.aerie.merlin.protocol.types.SerializedValue;
import gov.nasa.jpl.aerie.scheduler.server.models.Timestamp;

import java.util.Map;

public final record SpecificationRecord(
    long id,
    long revision,
    long planId,
    long planRevision,
    Timestamp horizonStartTimestamp,
    Timestamp horizonEndTimestamp,
    Map<String, SerializedValue> simulationArguments,
    boolean analysisOnly
) {}
