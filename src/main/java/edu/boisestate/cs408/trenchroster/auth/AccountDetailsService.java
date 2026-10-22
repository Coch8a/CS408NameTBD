package edu.boisestate.cs408.trenchroster.auth;

import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads the Task 1 account hashes for Spring Security's authentication provider. */
@Service
public class AccountDetailsService implements UserDetailsService {
    private final JdbcTemplate jdbc;

    public AccountDetailsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        var accounts = jdbc.query("""
                SELECT username, password_hash FROM users WHERE username_normalized = ?
                """, (row, number) -> User.withUsername(row.getString("username"))
                .password(row.getString("password_hash")).roles("USER").build(), normalize(username));
        if (accounts.isEmpty()) throw new UsernameNotFoundException("Account not found.");
        return accounts.getFirst();
    }

    public RegistrationService.RegisteredUser currentAccount(String username) {
        var accounts = jdbc.query("""
                SELECT id, username, email FROM users WHERE username_normalized = ?
                """, (row, number) -> new RegistrationService.RegisteredUser(
                        row.getLong("id"), row.getString("username"), row.getString("email")), normalize(username));
        if (accounts.isEmpty()) throw new UsernameNotFoundException("Account not found.");
        return accounts.getFirst();
    }

    private String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
