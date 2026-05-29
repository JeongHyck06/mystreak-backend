package mystreak.backend.common;

public record ApiErrorResponse(
        int status,
        String message
) {
}
