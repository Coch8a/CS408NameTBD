package edu.boisestate.cs408.trenchroster;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gives the deployment script an endpoint it can check without logging in. */
@RestController
public class HealthController {
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "app", "trench-roster");
    }
}
