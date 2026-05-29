package mystreak.backend.auth;

import org.springframework.http.HttpStatusCode;

public class SupabaseAuthException extends RuntimeException {

    private final HttpStatusCode statusCode;

    public SupabaseAuthException(HttpStatusCode statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public HttpStatusCode statusCode() {
        return statusCode;
    }
}
