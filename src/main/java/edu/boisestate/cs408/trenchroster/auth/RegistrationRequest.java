package edu.boisestate.cs408.trenchroster.auth;

import java.util.Locale;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank(message = "Username is required.")
        @Pattern(regexp = "[A-Za-z0-9_]{3,50}", message = "Use 3–50 letters, numbers, or underscores.")
        String username,
        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 254, message = "Email must be at most 254 characters.")
        String email,
        @NotBlank(message = "Password is required.")
        @Size(min = 12, max = 72, message = "Password must be 12–72 characters (at most 72 UTF-8 bytes).")
        String password,
        @NotBlank(message = "Confirm your password.")
        @Size(max = 72, message = "Confirmation must be at most 72 characters.")
        String confirmPassword) {

    public RegistrationRequest {
        username = username == null ? null : username.trim();
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        // Passwords are deliberately not trimmed or case-normalized.
    }

    @Override public String toString() {
        return "RegistrationRequest[credentials redacted]";
    }
}
