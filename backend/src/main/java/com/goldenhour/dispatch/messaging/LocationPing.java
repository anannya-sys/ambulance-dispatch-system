package com.goldenhour.dispatch.messaging;

import java.time.LocalDateTime;

/** One GPS fix from an ambulance (simulated device or a real one posting to the API). */
public record LocationPing(long ambulanceId, double lat, double lon, double heading, double speedKmph,
                           LocalDateTime at) {
}
