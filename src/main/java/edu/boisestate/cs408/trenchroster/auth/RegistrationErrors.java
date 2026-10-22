package edu.boisestate.cs408.trenchroster.auth;

import java.util.Map;
import java.util.TreeMap;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RegistrationController.class)
public class RegistrationErrors {
    public record ErrorResponse(String message, Map<String, String> errors) {}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException exception) {
        var fields = new TreeMap<String, String>();
        // Only return validation messages, never rejected values or exception details.
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "Check the registration fields.", fields);
    }

    @ExceptionHandler(RegistrationService.InvalidRegistration.class)
    ResponseEntity<ErrorResponse> invalid(RegistrationService.InvalidRegistration exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), exception.fields());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> malformed() {
        return error(HttpStatus.BAD_REQUEST, "Send a valid JSON registration request.", Map.of());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ErrorResponse> duplicate() {
        return error(HttpStatus.CONFLICT, "Username or email is already in use.", Map.of());
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErrorResponse> unavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Registration is temporarily unavailable. Please try again.", Map.of());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(new ErrorResponse(message, fields));
    }
}
