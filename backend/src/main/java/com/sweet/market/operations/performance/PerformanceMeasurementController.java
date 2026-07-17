package com.sweet.market.operations.performance;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.common.error.ErrorResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/performance-measurements")
public class PerformanceMeasurementController {

    private final PerformanceMeasurementService service;
    private final ObjectMapper objectMapper;

    public PerformanceMeasurementController(
            PerformanceMeasurementService service,
            ObjectMapper objectMapper
    ) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PerformanceMeasurementResponse>> register(
            @RequestBody JsonNode body,
            @AuthenticationPrincipal AuthenticatedMember member
    ) {
        PerformanceMeasurementRegisterRequest request = readStrictly(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.register(request, member.id())));
    }

    @GetMapping
    public ApiResponse<Page<PerformanceMeasurementResponse>> list(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.ok(service.findAll(pageable));
    }

    @GetMapping("/{runId}")
    public ApiResponse<PerformanceMeasurementResponse> detail(@PathVariable long runId) {
        return ApiResponse.ok(service.findById(runId));
    }

    @ExceptionHandler(PerformanceMeasurementService.PerformanceMeasurementValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            PerformanceMeasurementService.PerformanceMeasurementValidationException exception
    ) {
        List<ErrorResponse.FieldErrorResponse> fieldErrors = exception.fieldViolations().stream()
                .map(violation -> new ErrorResponse.FieldErrorResponse(violation.field(), violation.message()))
                .toList();
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, fieldErrors));
    }

    private PerformanceMeasurementRegisterRequest readStrictly(JsonNode body) {
        try {
            return objectMapper.readerFor(PerformanceMeasurementRegisterRequest.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(body.traverse(objectMapper));
        } catch (IOException exception) {
            throw new PerformanceMeasurementService.PerformanceMeasurementValidationException(
                    List.of(new PerformanceMeasurementService.FieldViolation(
                            "request", "허용되지 않은 필드가 있거나 JSON 형식이 올바르지 않습니다."
                    ))
            );
        }
    }
}
