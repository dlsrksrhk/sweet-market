package com.sweet.market.operations.projection;

import com.sweet.market.operations.event.OperationalEvent;
import com.sweet.market.operations.event.OperationalEventType;

public interface OperationalEventHandler {

    String projectionName();

    boolean supports(OperationalEventType eventType, int schemaVersion);

    void handle(long generationId, OperationalEvent event);
}
