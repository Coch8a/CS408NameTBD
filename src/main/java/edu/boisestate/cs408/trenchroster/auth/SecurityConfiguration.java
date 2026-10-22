package edu.boisestate.cs408.trenchroster.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;

/** Session authentication with JSON responses and CSRF protection for every write. */
@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(access -> access
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/favicon.ico",
                        "/api/health", "/api/catalog", "/api/checkpoint", "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .anyRequest().authenticated())
            .csrf(Customizer.withDefaults())
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(fixation -> fixation.changeSessionId()))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(login -> login
                // A custom URL suppresses Spring's generated login page. Task 3 supplies the UI.
                .loginPage("/api/auth/login")
                .loginProcessingUrl("/api/auth/login")
                .successHandler((request, response, authentication) ->
                    json(response, 200, "{\"message\":\"Signed in.\"}"))
                .failureHandler((request, response, exception) ->
                    json(response, 401, "{\"message\":\"Invalid username or password.\"}")))
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> {
                    response.setHeader("Cache-Control", "no-store");
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                }))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                    json(response, 401, "{\"message\":\"Sign in to continue.\"}"))
                .accessDeniedHandler((request, response, exception) ->
                    json(response, 403, "{\"message\":\"Request rejected. Refresh your CSRF token and try again.\"}")));
        return http.build();
    }

    private static void json(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(body);
    }
}
