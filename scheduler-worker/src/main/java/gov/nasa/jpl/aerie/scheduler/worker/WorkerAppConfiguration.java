package gov.nasa.jpl.aerie.scheduler.worker;

import java.net.URI;
import java.nio.file.Path;
import gov.nasa.jpl.aerie.scheduler.server.config.PlanOutputMode;
import gov.nasa.jpl.aerie.scheduler.server.config.Store;

public record WorkerAppConfiguration(
    Store store,
    URI merlinGraphqlURI,
    Path merlinFileStore,
    Path missionRuleJarPath,
    PlanOutputMode outputMode,
    String hasuraGraphQlAdminSecret
) { }
