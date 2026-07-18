package com.sweet.market.provider.web;

import com.sweet.market.provider.security.SignedRequestFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
    ResponseEntity<IntegrationErrorResponse> invalidRequest(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new IntegrationErrorResponse(
                "INTEGRATION_REQUEST_INVALID",
                "Integration request is invalid",
                authenticatedRequestId(request)));
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    ResponseEntity<IntegrationErrorResponse> routeNotFound(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new IntegrationErrorResponse(
                "INTEGRATION_REQUEST_INVALID",
                "Requested route was not found",
                authenticatedRequestId(request)));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<IntegrationErrorResponse> methodNotAllowed(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(new IntegrationErrorResponse(
                "INTEGRATION_REQUEST_INVALID",
                "Request method is not allowed",
                authenticatedRequestId(request)));
    }

    private UUID authenticatedRequestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(SignedRequestFilter.REQUEST_ID_ATTRIBUTE);
        return requestId instanceof UUID uuid ? uuid : null;
    }
}
