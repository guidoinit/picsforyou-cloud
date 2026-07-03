package com.picsforyou.cloud.client;

public class ApiException extends Exception {

    private final int statusCode;

    public ApiException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public ApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() { return statusCode; }
}
