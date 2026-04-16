package com.example.ota.exception;

public class OtaException extends RuntimeException {

    private final String errorCode;

    public OtaException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
