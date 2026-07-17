package com.sweet.market.operations.admin;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.operations.projection.ProjectionGenerationService;
import com.sweet.market.operations.projection.ProjectionRebuildResult;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
public class AdminOperationalEventController {

    private final AdminOperationalEventService eventService;
    private final ProjectionGenerationService generationService;

    public AdminOperationalEventController(
            AdminOperationalEventService eventService,
            ProjectionGenerationService generationService
    ) {
        this.eventService = eventService;
        this.generationService = generationService;
    }

    @GetMapping("/api/admin/operational-events/dead")
    public ApiResponse<Page<DeadOperationalEventResponse>> dead(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(eventService.findDead(page, size));
    }

    @PostMapping("/api/admin/operational-events/{eventId}/retry")
    public ApiResponse<Void> retry(Authentication authentication, @PathVariable UUID eventId) {
        eventService.retry(eventId, memberId(authentication), Instant.now());
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/operational-projections/rebuild")
    public ApiResponse<ProjectionRebuildResult> rebuild(Authentication authentication) {
        return ApiResponse.ok(generationService.rebuild(memberId(authentication), Instant.now()));
    }

    private Long memberId(Authentication authentication) {
        return ((AuthenticatedMember) authentication.getPrincipal()).id();
    }
}
