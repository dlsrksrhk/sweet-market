package com.sweet.market.gateway.web;

import com.sweet.market.gateway.security.SignedRequestFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.UUID;

@RestControllerAdvice
public class IntegrationExceptionHandler {

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class,
            HttpMediaTypeNotSupportedException.class
    })
    ResponseEntity<IntegrationErrorResponse> handleInvalidRequest(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new IntegrationErrorResponse(
                "INTEGRATION_REQUEST_INVALID",
                "Integration request is invalid",
                authenticatedRequestId(request)
        ));
    }

    private UUID authenticatedRequestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(SignedRequestFilter.REQUEST_ID_ATTRIBUTE);
        return requestId instanceof UUID value ? value : null;
    }
}
