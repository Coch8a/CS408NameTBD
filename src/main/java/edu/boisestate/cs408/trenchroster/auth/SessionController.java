package edu.boisestate.cs408.trenchroster.auth;

import java.security.Principal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes a CSRF token and only the signed-in user's public account fields. */
@RestController
@RequestMapping("/api/auth")
public class SessionController {
    private final AccountDetailsService accounts;

    public SessionController(AccountDetailsService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfResponse> csrf(CsrfToken csrf) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(csrf.getHeaderName(), csrf.getParameterName(), csrf.getToken()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Principal principal, HttpServletRequest request) {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(accounts.currentAccount(principal.getName()));
        } catch (UsernameNotFoundException exception) {
            // A deleted account must not remain signed in through an older session.
            var session = request.getSession(false);
            if (session != null) session.invalidate();
            SecurityContextHolder.clearContext();
            return ResponseEntity.status(401).cacheControl(CacheControl.noStore())
                    .body(java.util.Map.of("message", "Sign in to continue."));
        }
    }

    public record CsrfResponse(String headerName, String parameterName, String token) {}
}
