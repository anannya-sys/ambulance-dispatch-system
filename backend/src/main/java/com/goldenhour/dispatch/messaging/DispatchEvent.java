package com.goldenhour.dispatch.messaging;

import java.time.LocalDateTime;

/**
 * Something happened on a trip. {@code type} examples: ASSIGNED, ACKNOWLEDGED, AT_SCENE, PICKED_UP,
 * PRE_ALERT, AT_HOSPITAL, CLOSED, REASSIGNED, BREAKDOWN, CORRIDOR.
 */
public record DispatchEvent(String type, Long tripId, Long incidentId, Long ambulanceId, String callSign,
                            Long hospitalId, String severity, String message, LocalDateTime at) {
}
