package mystreak.backend.auth;

import org.springframework.http.HttpStatusCode;

public class AuthException extends RuntimeException {

    private final HttpStatusCode statusCode;

    public AuthException(HttpStatusCode statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public HttpStatusCode statusCode() {
        return statusCode;
    }
}
