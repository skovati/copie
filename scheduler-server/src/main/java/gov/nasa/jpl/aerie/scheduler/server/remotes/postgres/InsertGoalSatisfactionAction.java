package gov.nasa.jpl.aerie.scheduler.server.remotes.postgres;

import gov.nasa.jpl.aerie.scheduler.server.models.GoalId;
import org.intellij.lang.annotations.Language;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/*package-local*/ final class InsertGoalSatisfactionAction implements AutoCloseable {
  private static final @Language("SQL") String sql = """
    insert into scheduling_goal_analysis (analysis_id, goal_id, satisfied)
    values (?, ?, ?)
    """;

  private final PreparedStatement statement;

  public InsertGoalSatisfactionAction(final Connection connection) throws SQLException {
    this.statement = connection.prepareStatement(sql);
  }

  public void apply(final long analysisId, final Map<GoalId, Boolean> goalSatisfactions) throws SQLException {
    for (final var entry : goalSatisfactions.entrySet()) {
      this.statement.setLong(1, analysisId);
      this.statement.setLong(2, entry.getKey().id());
      this.statement.setBoolean(3, entry.getValue());
      this.statement.addBatch();
    }

    final var resultSet = this.statement.executeBatch();
    for (final var result : resultSet) {
      if (result == Statement.EXECUTE_FAILED) throw new FailedInsertException("scheduling_goal_analysis");
    }
  }

  @Override
  public void close() throws SQLException {
    this.statement.close();
  }
}
