package com.goldenhour.dispatch.corridor;

import java.time.LocalDateTime;

/** One junction that should show green for the ambulance between {@code greenFrom} and {@code greenUntil}. */
public record SignalWindow(int signalNode, double lat, double lon, LocalDateTime greenFrom, LocalDateTime greenUntil) {
}
