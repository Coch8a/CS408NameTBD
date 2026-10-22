package edu.boisestate.cs408.trenchroster.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;

    public RegistrationService(JdbcTemplate jdbc, PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.passwords = passwords;
    }

    public RegisteredUser register(RegistrationRequest request) {
        var errors = new TreeMap<String, String>();
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            errors.put("password", "Password must be at most 72 UTF-8 bytes.");
        }
        if (!request.password().equals(request.confirmPassword())) {
            errors.put("confirmPassword", "Passwords must match.");
        }
        if (!errors.isEmpty()) throw new InvalidRegistration(errors);

        String hash = passwords.encode(request.password());
        var key = new GeneratedKeyHolder();
        // Database unique indexes also protect against simultaneous registrations.
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO users(username, email, password_hash, username_normalized, email_normalized)
                    VALUES (?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, request.username());
            statement.setString(2, request.email());
            statement.setString(3, hash);
            statement.setString(4, request.username().toLowerCase(Locale.ROOT));
            statement.setString(5, request.email());
            return statement;
        }, key);
        return new RegisteredUser(java.util.Objects.requireNonNull(key.getKey()).longValue(),
                request.username(), request.email());
    }

    public record RegisteredUser(long id, String username, String email) {}

    static class InvalidRegistration extends RuntimeException {
        private final Map<String, String> fields;
        InvalidRegistration(Map<String, String> fields) {
            super("Check the registration fields.");
            this.fields = Map.copyOf(fields);
        }
        Map<String, String> fields() { return fields; }
    }
}
