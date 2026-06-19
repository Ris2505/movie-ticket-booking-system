package com.moviebooking.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final Object details;

    public AppException(String code, String message, HttpStatus status) {
        this(code, message, status, null);
    }

    public AppException(String code, String message, HttpStatus status, Object details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = details;
    }
}
