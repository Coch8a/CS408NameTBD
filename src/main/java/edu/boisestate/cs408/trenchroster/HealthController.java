package edu.boisestate.cs408.trenchroster;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reports application and database readiness without exposing credentials. */
@RestController
public class HealthController {
    private final JdbcTemplate jdbc;
    public HealthController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> health() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Map.of("status", "UP", "app", "trench-roster", "database", "UP"));
        } catch (DataAccessException exception) {
            return ResponseEntity.status(503).body(
                    Map.of("status", "DOWN", "app", "trench-roster", "database", "DOWN"));
        }
    }
}
