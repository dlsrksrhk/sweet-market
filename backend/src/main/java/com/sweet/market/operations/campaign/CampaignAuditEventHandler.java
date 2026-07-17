package com.sweet.market.operations.campaign;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweet.market.operations.event.OperationalEvent;
import com.sweet.market.operations.event.OperationalEventType;
import com.sweet.market.operations.projection.OperationalEventHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

@Component
public class CampaignAuditEventHandler implements OperationalEventHandler {

    private static final String PROJECTION_NAME = "campaign-audit";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CampaignAuditEventHandler(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String projectionName() {
        return PROJECTION_NAME;
    }

    @Override
    public boolean supports(OperationalEventType eventType, int schemaVersion) {
        return eventType == OperationalEventType.CAMPAIGN_COMMAND_COMPLETED
                && schemaVersion == 1;
    }

    @Override
    public void handle(long generationId, OperationalEvent event) {
        CampaignCommandPayload payload = payload(event.payload());
        jdbcTemplate.update("""
                INSERT INTO campaign_audit_projection (
                    generation_id, event_id, campaign_kind, campaign_id,
                    owner_type, owner_store_id, actor_member_id, command,
                    occurred_at, aggregate_version, before_summary, after_summary
                ) VALUES (
                    :generationId, :eventId, :campaignKind, :campaignId,
                    :ownerType, :ownerStoreId, :actorMemberId, :command,
                    :occurredAt, :aggregateVersion,
                    CAST(:beforeSummary AS JSONB), CAST(:afterSummary AS JSONB)
                )
                """, new MapSqlParameterSource()
                .addValue("generationId", generationId)
                .addValue("eventId", event.eventId())
                .addValue("campaignKind", payload.campaignKind())
                .addValue("campaignId", payload.campaignId())
                .addValue("ownerType", payload.ownerType())
                .addValue("ownerStoreId", payload.ownerStoreId() == null ? 0L : payload.ownerStoreId())
                .addValue("actorMemberId", payload.actorMemberId())
                .addValue("command", payload.command())
                .addValue("occurredAt", Timestamp.from(event.occurredAt()))
                .addValue("aggregateVersion", event.aggregateVersion())
                .addValue("beforeSummary", serialized(payload.beforeSummary()))
                .addValue("afterSummary", serialized(payload.afterSummary())));
    }

    private CampaignCommandPayload payload(JsonNode payload) {
        try {
            return objectMapper.treeToValue(payload, CampaignCommandPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to deserialize campaign command payload", exception);
        }
    }

    private String serialized(JsonNode summary) {
        return summary == null || summary.isNull() ? null : summary.toString();
    }
}
