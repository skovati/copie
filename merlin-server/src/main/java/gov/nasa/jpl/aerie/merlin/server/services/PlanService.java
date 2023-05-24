package gov.nasa.jpl.aerie.merlin.server.services;

import gov.nasa.jpl.aerie.merlin.protocol.types.Duration;
import gov.nasa.jpl.aerie.merlin.protocol.types.ValueSchema;
import gov.nasa.jpl.aerie.merlin.server.exceptions.NoSuchPlanDatasetException;
import gov.nasa.jpl.aerie.merlin.server.exceptions.NoSuchPlanException;
import gov.nasa.jpl.aerie.merlin.server.models.Constraint;
import gov.nasa.jpl.aerie.merlin.server.models.DatasetId;
import gov.nasa.jpl.aerie.merlin.server.models.Plan;
import gov.nasa.jpl.aerie.merlin.server.models.PlanId;
import gov.nasa.jpl.aerie.merlin.server.models.ProfileSet;
import gov.nasa.jpl.aerie.merlin.server.models.Timestamp;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Map;

public interface PlanService {
  Plan getPlanForSimulation(PlanId planId) throws NoSuchPlanException;
  Plan getPlanForValidation(PlanId planId) throws NoSuchPlanException;
  RevisionData getPlanRevisionData(PlanId planId) throws NoSuchPlanException;

  Map<Long, Constraint> getConstraintsForPlan(PlanId planId) throws NoSuchPlanException;

  long addExternalDataset(PlanId planId, Timestamp datasetStart, ProfileSet profileSet) throws NoSuchPlanException;
  void extendExternalDataset(DatasetId datasetId, ProfileSet profileSet) throws NoSuchPlanDatasetException;
  List<Pair<Duration, ProfileSet>> getExternalDatasets(final PlanId planId) throws NoSuchPlanException;
  Map<String, ValueSchema> getExternalResourceSchemas(final PlanId planId) throws NoSuchPlanException;
}
