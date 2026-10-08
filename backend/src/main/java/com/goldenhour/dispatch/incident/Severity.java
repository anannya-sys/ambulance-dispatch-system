package com.goldenhour.dispatch.incident;

/**
 * Triage level given by the call taker.
 * {@code baseScore} feeds the priority queue; {@code sceneMinutes} is typical time spent stabilising on scene.
 */
public enum Severity {
    CRITICAL(100, 12),
    SERIOUS(50, 10),
    MINOR(10, 8);

    private final int baseScore;
    private final int sceneMinutes;

    Severity(int baseScore, int sceneMinutes) {
        this.baseScore = baseScore;
        this.sceneMinutes = sceneMinutes;
    }

    public int baseScore() {
        return baseScore;
    }

    public int sceneMinutes() {
        return sceneMinutes;
    }
}
