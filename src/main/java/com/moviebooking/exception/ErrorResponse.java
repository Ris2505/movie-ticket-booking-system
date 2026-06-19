package com.moviebooking.exception;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ErrorResponse {
    String code;
    String message;
    Object details;
}
