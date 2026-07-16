package com.sweet.market.common.error;

import com.sweet.market.purchase.api.CartCheckoutFailureException;
import com.sweet.market.purchase.api.CartCheckoutFailureResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.errorCode();
        return ResponseEntity
                .status(errorCode.status())
                .body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(CartCheckoutFailureException.class)
    public ResponseEntity<CartCheckoutFailureResponse> handleCartCheckoutFailure(
            CartCheckoutFailureException exception
    ) {
        return ResponseEntity.status(ErrorCode.CART_CHECKOUT_NOT_ALLOWED.status())
                .body(new CartCheckoutFailureResponse(
                        ErrorCode.CART_CHECKOUT_NOT_ALLOWED.name(),
                        ErrorCode.CART_CHECKOUT_NOT_ALLOWED.message(),
                        exception.failure()
                ));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailureException(
            ObjectOptimisticLockingFailureException exception
    ) {
        return ResponseEntity
                .status(ErrorCode.ORDER_CONFLICT.status())
                .body(ErrorResponse.of(ErrorCode.ORDER_CONFLICT));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        List<ErrorResponse.FieldErrorResponse> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldErrorResponse)
                .toList();

        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.status())
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadableException(
            HttpMessageNotReadableException exception
    ) {
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.status())
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR));
    }

    private ErrorResponse.FieldErrorResponse toFieldErrorResponse(FieldError fieldError) {
        return new ErrorResponse.FieldErrorResponse(fieldError.getField(), fieldError.getDefaultMessage());
    }
}
