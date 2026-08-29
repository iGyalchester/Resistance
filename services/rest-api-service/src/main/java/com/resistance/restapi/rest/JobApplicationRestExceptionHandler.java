package com.resistance.restapi.rest;

import com.resistance.shared.exceptions.ApiErrorResponse;
import com.resistance.shared.exceptions.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class JobApplicationRestExceptionHandler {

    // handle lookups for applications that do not exist
    @ExceptionHandler
    public ResponseEntity<ApiErrorResponse> handleException(ResourceNotFoundException exc) {

        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                exc.getMessage(),
                System.currentTimeMillis());

        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    // catch-all for bad requests
    @ExceptionHandler
    public ResponseEntity<ApiErrorResponse> handleException(Exception exc) {

        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                exc.getMessage(),
                System.currentTimeMillis());

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
}
