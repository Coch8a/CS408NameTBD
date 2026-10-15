package edu.boisestate.cs408.trenchroster;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** A database outage must not be reported as a successful deployment. */
class HealthControllerTest {
    @Test void reportsUnavailableWhenDatabaseCannotBeReached() {
        JdbcTemplate jdbc = new JdbcTemplate() {
            @Override public <T> T queryForObject(String sql, Class<T> requiredType) {
                throw new DataAccessResourceFailureException("private connection details");
            }
        };
        var response = new HealthController(jdbc).health();
        assertEquals(503, response.getStatusCode().value());
        assertEquals("DOWN", response.getBody().get("database"));
        assertEquals(3, response.getBody().size());
    }
}
