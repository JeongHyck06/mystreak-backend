package mystreak.backend.common;

import mystreak.backend.auth.SupabaseAuthException;
import mystreak.backend.pod.PodNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SupabaseAuthException.class)
    ResponseEntity<ApiErrorResponse> handleSupabaseAuthException(SupabaseAuthException exception) {
        return ResponseEntity
                .status(exception.statusCode())
                .body(new ApiErrorResponse(exception.statusCode().value(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(this::validationMessage)
                .orElse("Invalid request");

        return ResponseEntity
                .badRequest()
                .body(new ApiErrorResponse(HttpStatus.BAD_REQUEST.value(), message));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException exception) {
        return ResponseEntity
                .status(exception.getStatusCode())
                .body(new ApiErrorResponse(exception.getStatusCode().value(), exception.getReason()));
    }

    @ExceptionHandler(PodNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handlePodNotFoundException(PodNotFoundException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(HttpStatus.NOT_FOUND.value(), exception.getMessage()));
    }

    private String validationMessage(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
