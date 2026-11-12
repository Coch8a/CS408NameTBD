package edu.boisestate.cs408.trenchroster.catalog;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProfileController {
    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) { this.profiles = profiles; }

    @GetMapping("/api/catalog/units/{id}/profile")
    public ResponseEntity<?> unit(@PathVariable long id) {
        return ResponseEntity.of(profiles.unit(id));
    }

    @GetMapping("/api/catalog/equipment/{id}/profile")
    public ResponseEntity<?> equipment(@PathVariable long id) {
        return ResponseEntity.of(profiles.equipment(id));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<?> unavailable() {
        return ResponseEntity.status(503).body(Map.of("message", "The profile is temporarily unavailable."));
    }
}
