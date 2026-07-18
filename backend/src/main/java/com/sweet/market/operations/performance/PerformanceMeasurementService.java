package com.sweet.market.operations.performance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;

@Service
public class PerformanceMeasurementService {

    private static final int MAX_CANONICAL_PAYLOAD_BYTES = 1024 * 1024;
    private static final int MAX_PAGE_SIZE = 100;
    private static final long ACTUAL_DURATION_TOLERANCE_SECONDS = 5L;
    private static final Set<String> REQUIRED_ENDPOINTS = Set.of("catalog", "events", "popularity", "detail");
    private static final Set<String> REQUIRED_QUERY_SHAPES = Set.of(
            "GLOBAL_CATALOG", "FIXED_STORE_CATALOG", "ACTIVE_EVENTS", "POPULARITY"
    );
    private static final BigDecimal ONE = BigDecimal.ONE;

    private final PerformanceMeasurementRepository repository;
    private final ObjectMapper objectMapper;

    public PerformanceMeasurementService(
            PerformanceMeasurementRepository repository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PerformanceMeasurementResponse register(
            PerformanceMeasurementRegisterRequest request,
            long registeredBy
    ) {
        PerformanceMeasurementRegisterRequest canonical = validateAndCanonicalize(request);
        byte[] canonicalBytes = serialize(canonical);
        if (canonicalBytes.length > MAX_CANONICAL_PAYLOAD_BYTES) {
            throw invalid("request", "정규화한 요청은 1 MiB 이하여야 합니다.");
        }
        String payloadHash = sha256(canonicalBytes);

        OptionalLong insertedRunId = repository.insertRun(canonical, payloadHash, registeredBy);
        if (insertedRunId.isEmpty()) {
            PerformanceMeasurementRepository.RunRow existing = repository
                    .findRunByMeasurementId(canonical.measurementId())
                    .orElseThrow(() -> new IllegalStateException("충돌한 성능 측정 헤더를 찾을 수 없습니다."));
            if (!existing.payloadHash().equals(payloadHash)) {
                throw new BusinessException(ErrorCode.PERFORMANCE_MEASUREMENT_CONFLICT);
            }
            return detail(existing);
        }

        long runId = insertedRunId.getAsLong();
        repository.insertEndpointMetrics(runId, combineEndpointMetrics(canonical));
        repository.insertQueryEvidence(runId, combineQueryEvidence(canonical));
        return detail(repository.findRunById(runId).orElseThrow());
    }

    @Transactional(readOnly = true)
    public Page<PerformanceMeasurementResponse> findAll(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw invalid("size", "페이지 크기는 100 이하여야 합니다.");
        }
        List<PerformanceMeasurementResponse> content = repository.findRuns(pageable).stream()
                .map(run -> response(run, repository.findEndpointMetrics(run.runId()), List.of()))
                .toList();
        return new PageImpl<>(content, pageable, repository.countRuns());
    }

    @Transactional(readOnly = true)
    public PerformanceMeasurementResponse findById(long runId) {
        if (runId <= 0) {
            throw invalid("runId", "실행 ID는 양수여야 합니다.");
        }
        PerformanceMeasurementRepository.RunRow run = repository.findRunById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERFORMANCE_MEASUREMENT_NOT_FOUND));
        return detail(run);
    }

    private PerformanceMeasurementResponse detail(PerformanceMeasurementRepository.RunRow run) {
        return response(
                run,
                repository.findEndpointMetrics(run.runId()),
                repository.findQueryEvidence(run.runId())
        );
    }

    private PerformanceMeasurementResponse response(
            PerformanceMeasurementRepository.RunRow run,
            List<EndpointMetricInput> endpointMetrics,
            List<QueryEvidenceInput> queryEvidence
    ) {
        return new PerformanceMeasurementResponse(
                run.runId(), run.measurementId(), run.payloadHash(), run.gitCommit(), run.dirtyWorktree(),
                run.fixtureVersion(), run.scenarioVersion(), run.environmentName(), run.hardwareDescription(),
                run.artifactDirectory(), run.warmupSeconds(), run.measuredSeconds(),
                run.offStartedAt(), run.offCompletedAt(), run.onStartedAt(), run.onCompletedAt(),
                run.registeredBy(), run.registeredAt(), true, true,
                List.copyOf(endpointMetrics), List.copyOf(queryEvidence)
        );
    }

    private PerformanceMeasurementRegisterRequest validateAndCanonicalize(
            PerformanceMeasurementRegisterRequest request
    ) {
        if (request == null) {
            throw invalid("request", "요청 본문이 필요합니다.");
        }
        if (request.measurementId() == null) {
            throw invalid("measurementId", "측정 UUID가 필요합니다.");
        }
        validateArtifactDirectory(request.artifactDirectory());
        CacheModeMeasurementInput off = validateMode(request.off(), "OFF", "off");
        CacheModeMeasurementInput on = validateMode(request.on(), "ON", "on");
        validateComparable(off, on);
        return new PerformanceMeasurementRegisterRequest(
                request.measurementId(), request.artifactDirectory(), canonicalize(off), canonicalize(on)
        );
    }

    private CacheModeMeasurementInput validateMode(
            CacheModeMeasurementInput mode,
            String requiredMode,
            String field
    ) {
        if (mode == null) {
            throw invalid(field, requiredMode + " 측정값이 필요합니다.");
        }
        if (!requiredMode.equals(mode.cacheMode())) {
            throw invalid(field + ".cacheMode", "cacheMode는 " + requiredMode + "여야 합니다.");
        }
        if (mode.dirtyWorktree() == null) {
            throw invalid(field + ".dirtyWorktree", "dirtyWorktree 선언이 필요합니다.");
        }
        requireText(mode.gitCommit(), 64, field + ".gitCommit");
        if (!mode.gitCommit().matches("[0-9a-f]{7,64}")) {
            throw invalid(field + ".gitCommit", "Git commit은 소문자 16진수 해시여야 합니다.");
        }
        requireText(mode.fixtureVersion(), 80, field + ".fixtureVersion");
        requireText(mode.scenarioVersion(), 80, field + ".scenarioVersion");
        requireText(mode.environmentName(), 80, field + ".environmentName");
        requireText(mode.hardwareDescription(), 500, field + ".hardwareDescription");
        if (mode.warmupSeconds() <= 0) {
            throw invalid(field + ".warmupSeconds", "warmupSeconds는 양수여야 합니다.");
        }
        if (mode.measuredSeconds() <= 0) {
            throw invalid(field + ".measuredSeconds", "measuredSeconds는 양수여야 합니다.");
        }
        if (mode.startedAt() == null || mode.completedAt() == null) {
            throw invalid(field + ".startedAt", "측정 시작/완료 시각이 필요합니다.");
        }
        if (!mode.completedAt().isAfter(mode.startedAt())) {
            throw invalid(field + ".completedAt", "완료 시각은 시작 시각보다 뒤여야 합니다.");
        }
        Duration expectedDuration = Duration.ofSeconds(
                (long) mode.warmupSeconds() + mode.measuredSeconds());
        Duration actualDuration = Duration.between(mode.startedAt(), mode.completedAt());
        if (actualDuration.compareTo(expectedDuration.minusSeconds(
                ACTUAL_DURATION_TOLERANCE_SECONDS)) < 0
                || actualDuration.compareTo(expectedDuration.plusSeconds(
                ACTUAL_DURATION_TOLERANCE_SECONDS)) > 0) {
            throw invalid(field + ".completedAt",
                    "실제 측정 시간은 warmupSeconds + measuredSeconds의 ±5초 이내여야 합니다.");
        }
        validateEndpointMetrics(mode.endpointMetrics(), requiredMode, field + ".endpointMetrics");
        validateQueryEvidence(mode.queryEvidence(), requiredMode, field + ".queryEvidence");
        return mode;
    }

    private void validateEndpointMetrics(List<EndpointMetricInput> metrics, String mode, String field) {
        if (metrics == null || metrics.size() != REQUIRED_ENDPOINTS.size()) {
            throw invalid(field, "필수 endpoint 측정값 4개가 필요합니다.");
        }
        for (int index = 0; index < metrics.size(); index++) {
            if (metrics.get(index) == null) {
                throw invalid(field + "[" + index + "]", "endpoint 측정값이 필요합니다.");
            }
        }
        Set<String> endpoints = metrics.stream().map(EndpointMetricInput::endpoint)
                .collect(java.util.stream.Collectors.toSet());
        if (!endpoints.equals(REQUIRED_ENDPOINTS)) {
            throw invalid(field, "endpoint는 catalog, events, popularity, detail을 정확히 포함해야 합니다.");
        }
        for (int index = 0; index < metrics.size(); index++) {
            EndpointMetricInput metric = metrics.get(index);
            String item = field + "[" + index + "]";
            if (!mode.equals(metric.cacheMode())) {
                throw invalid(item + ".cacheMode", "상위 cacheMode와 일치해야 합니다.");
            }
            requireDecimal(metric.p50Millis(), item + ".p50Millis", 12, 3);
            requireDecimal(metric.p95Millis(), item + ".p95Millis", 12, 3);
            if (metric.p50Millis().signum() < 0 || metric.p95Millis().compareTo(metric.p50Millis()) < 0) {
                throw invalid(item + ".p50Millis", "p50은 0 이상이고 p95 이하여야 합니다.");
            }
            requireDecimal(metric.throughputPerSecond(), item + ".throughputPerSecond", 14, 3);
            if (metric.throughputPerSecond().signum() < 0) {
                throw invalid(item + ".throughputPerSecond", "처리량은 음수일 수 없습니다.");
            }
            requireDecimal(metric.errorRate(), item + ".errorRate", 8, 7);
            if (metric.errorRate().signum() < 0 || metric.errorRate().compareTo(ONE) > 0) {
                throw invalid(item + ".errorRate", "오류율은 0 이상 1 이하여야 합니다.");
            }
            if (metric.jdbcStatementCount() == null || metric.jdbcStatementCount() < 0) {
                throw invalid(item + ".jdbcStatementCount", "JDBC 문장 수는 음수일 수 없습니다.");
            }
            requireNonNegative(metric.cacheHitCount(), item + ".cacheHitCount");
            requireNonNegative(metric.cacheMissCount(), item + ".cacheMissCount");
            requireNonNegative(metric.cacheEvictionCount(), item + ".cacheEvictionCount");
        }
    }

    private void validateQueryEvidence(List<QueryEvidenceInput> evidence, String mode, String field) {
        if (evidence == null || evidence.size() != REQUIRED_QUERY_SHAPES.size()) {
            throw invalid(field, "필수 query evidence 4개가 필요합니다.");
        }
        for (int index = 0; index < evidence.size(); index++) {
            if (evidence.get(index) == null) {
                throw invalid(field + "[" + index + "]", "query evidence가 필요합니다.");
            }
        }
        Set<String> shapes = evidence.stream().map(QueryEvidenceInput::queryShape)
                .collect(java.util.stream.Collectors.toSet());
        if (!shapes.equals(REQUIRED_QUERY_SHAPES)) {
            throw invalid(field, "필수 query shape를 정확히 포함해야 합니다.");
        }
        for (int index = 0; index < evidence.size(); index++) {
            QueryEvidenceInput query = evidence.get(index);
            String item = field + "[" + index + "]";
            if (!mode.equals(query.cacheMode())) {
                throw invalid(item + ".cacheMode", "상위 cacheMode와 일치해야 합니다.");
            }
            requireText(query.bindSummary(), 1000, item + ".bindSummary");
            requireText(query.planSummary(), 4000, item + ".planSummary");
            requireDecimal(query.executionMillis(), item + ".executionMillis", 12, 3);
            if (query.executionMillis().signum() < 0) {
                throw invalid(item + ".executionMillis", "실행 시간은 음수일 수 없습니다.");
            }
            if (query.actualRows() == null || query.sharedHitBlocks() == null || query.sharedReadBlocks() == null
                    || query.actualRows() < 0 || query.sharedHitBlocks() < 0 || query.sharedReadBlocks() < 0) {
                throw invalid(item, "행 및 버퍼 수는 음수일 수 없습니다.");
            }
        }
    }

    private void validateComparable(CacheModeMeasurementInput off, CacheModeMeasurementInput on) {
        boolean comparable = Objects.equals(off.gitCommit(), on.gitCommit())
                && Objects.equals(off.dirtyWorktree(), on.dirtyWorktree())
                && Objects.equals(off.fixtureVersion(), on.fixtureVersion())
                && Objects.equals(off.scenarioVersion(), on.scenarioVersion())
                && Objects.equals(off.environmentName(), on.environmentName())
                && Objects.equals(off.hardwareDescription(), on.hardwareDescription())
                && off.warmupSeconds() == on.warmupSeconds()
                && off.measuredSeconds() == on.measuredSeconds();
        if (!comparable) {
            throw invalid("off.on.comparability", "OFF/ON 측정의 환경 및 시나리오 메타데이터가 일치해야 합니다.");
        }
        Duration offDuration = Duration.between(off.startedAt(), off.completedAt());
        Duration onDuration = Duration.between(on.startedAt(), on.completedAt());
        if (offDuration.minus(onDuration).abs()
                .compareTo(Duration.ofSeconds(ACTUAL_DURATION_TOLERANCE_SECONDS)) > 0) {
            throw invalid("off.on.duration", "OFF/ON 실제 측정 시간 차이는 5초 이내여야 합니다.");
        }
    }

    private CacheModeMeasurementInput canonicalize(CacheModeMeasurementInput mode) {
        List<EndpointMetricInput> metrics = mode.endpointMetrics().stream()
                .map(metric -> new EndpointMetricInput(
                        metric.cacheMode(), metric.endpoint(),
                        canonicalDecimal(metric.p50Millis()), canonicalDecimal(metric.p95Millis()),
                        canonicalDecimal(metric.throughputPerSecond()), canonicalDecimal(metric.errorRate()),
                        metric.jdbcStatementCount(), metric.cacheHitCount(), metric.cacheMissCount(),
                        metric.cacheEvictionCount()
                ))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        metrics.sort(Comparator.comparing(EndpointMetricInput::endpoint));
        List<QueryEvidenceInput> evidence = mode.queryEvidence().stream()
                .map(query -> new QueryEvidenceInput(
                        query.cacheMode(), query.queryShape(), query.bindSummary(), query.planSummary(),
                        canonicalDecimal(query.executionMillis()), query.actualRows(),
                        query.sharedHitBlocks(), query.sharedReadBlocks()
                ))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        evidence.sort(Comparator.comparing(QueryEvidenceInput::queryShape));
        return new CacheModeMeasurementInput(
                mode.cacheMode(), mode.gitCommit(), mode.dirtyWorktree(), mode.fixtureVersion(),
                mode.scenarioVersion(), mode.environmentName(), mode.hardwareDescription(),
                mode.warmupSeconds(), mode.measuredSeconds(), mode.startedAt(), mode.completedAt(),
                List.copyOf(metrics), List.copyOf(evidence)
        );
    }

    private BigDecimal canonicalDecimal(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
    }

    private void validateArtifactDirectory(String value) {
        requireText(value, 500, "artifactDirectory");
        if (!value.startsWith("performance/results/") || value.contains("\\") || value.contains(":")) {
            throw invalid("artifactDirectory", "artifactDirectory는 performance/results/ 아래의 상대 경로여야 합니다.");
        }
        try {
            Path path = Path.of(value);
            if (path.isAbsolute() || !path.normalize().toString().replace('\\', '/').equals(value)
                    || Arrays.stream(value.split("/"))
                    .anyMatch(segment -> segment.equals("..") || segment.equals("."))) {
                throw invalid("artifactDirectory", "artifactDirectory는 정규화된 상대 경로여야 합니다.");
            }
        } catch (InvalidPathException exception) {
            throw invalid("artifactDirectory", "artifactDirectory가 올바른 경로가 아닙니다.");
        }
    }

    private void requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || value.length() > maxLength) {
            throw invalid(field, "필수 문자열 형식 또는 길이가 올바르지 않습니다.");
        }
    }

    private void requireDecimal(BigDecimal value, String field, int precision, int scale) {
        int integerDigits = value == null ? 0 : Math.max(value.precision() - value.scale(), 0);
        if (value == null || Math.max(value.scale(), 0) > scale || integerDigits > precision - scale) {
            throw invalid(field, "숫자 정밀도 또는 단위가 올바르지 않습니다.");
        }
    }

    private void requireNonNegative(Long value, String field) {
        if (value != null && value < 0) {
            throw invalid(field, "카운트는 음수일 수 없습니다.");
        }
    }

    private List<EndpointMetricInput> combineEndpointMetrics(PerformanceMeasurementRegisterRequest request) {
        List<EndpointMetricInput> metrics = new ArrayList<>(request.off().endpointMetrics());
        metrics.addAll(request.on().endpointMetrics());
        return metrics;
    }

    private List<QueryEvidenceInput> combineQueryEvidence(PerformanceMeasurementRegisterRequest request) {
        List<QueryEvidenceInput> evidence = new ArrayList<>(request.off().queryEvidence());
        evidence.addAll(request.on().queryEvidence());
        return evidence;
    }

    private byte[] serialize(PerformanceMeasurementRegisterRequest request) {
        try {
            return objectMapper.writeValueAsBytes(request);
        } catch (JsonProcessingException exception) {
            throw invalid("request", "요청을 정규화할 수 없습니다.");
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private PerformanceMeasurementValidationException invalid(String field, String message) {
        return new PerformanceMeasurementValidationException(List.of(new FieldViolation(field, message)));
    }

    public static final class PerformanceMeasurementValidationException extends RuntimeException {
        private final List<FieldViolation> fieldViolations;

        public PerformanceMeasurementValidationException(List<FieldViolation> fieldViolations) {
            this.fieldViolations = List.copyOf(fieldViolations);
        }

        public List<FieldViolation> fieldViolations() {
            return fieldViolations;
        }
    }

    public record FieldViolation(String field, String message) {
    }
}
