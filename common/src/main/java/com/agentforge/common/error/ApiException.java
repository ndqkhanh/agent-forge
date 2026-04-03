package com.agentforge.common.error;

/**
 * Exception thrown when an API call fails.
 * Immutable. Thread-safe.
 */
public non-sealed class ApiException extends AgentForgeException {

    private final int statusCode;
    private final String errorType;

    public ApiException(String message, int statusCode, String errorType) {
        super(message);
        this.statusCode = statusCode;
        this.errorType = errorType;
    }

    public ApiException(String message, int statusCode, String errorType, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorType = errorType;
    }

    public ApiException(String message) {
        this(message, 0, "unknown");
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.errorType = "unknown";
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorType() {
        return errorType;
    }

    @Override
    public String toString() {
        return "ApiException{statusCode=" + statusCode + ", errorType='" + errorType + "', message='" + getMessage() + "'}";
    }
}
