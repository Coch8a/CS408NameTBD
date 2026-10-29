package edu.boisestate.cs408.trenchroster.roster;

import java.util.Map;
import java.util.TreeMap;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Safe API errors never disclose another account's roster or database details. */
@RestControllerAdvice(assignableTypes = RosterController.class)
public class RosterErrors {
    public record ErrorResponse(String message, Map<String, String> errors) {}
    @ExceptionHandler(RosterService.MissingRoster.class)
    ResponseEntity<ErrorResponse> missing() { return error(404, "Roster not found.", Map.of()); }
    @ExceptionHandler(UsernameNotFoundException.class)
    ResponseEntity<ErrorResponse> signedOut() { return error(401, "Sign in to continue.", Map.of()); }
    @ExceptionHandler(RosterService.InvalidRoster.class)
    ResponseEntity<ErrorResponse> invalid(RosterService.InvalidRoster exception) {
        return error(400, exception.getMessage(), exception.fields());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException exception) {
        var fields = new TreeMap<String, String>();
        exception.getBindingResult().getFieldErrors().forEach(field ->
                fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
        return error(400, "Check the roster settings.", fields);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> malformed() {
        return error(400, "Send valid roster settings with whole-number IDs and a whole-number ducat limit.", Map.of());
    }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErrorResponse> unavailable() {
        return error(503, "Rosters are temporarily unavailable. Please try again.", Map.of());
    }
    private ResponseEntity<ErrorResponse> error(int status, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new ErrorResponse(message, fields));
    }
}
