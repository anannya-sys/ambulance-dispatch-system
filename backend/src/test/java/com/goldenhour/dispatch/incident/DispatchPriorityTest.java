package com.goldenhour.dispatch.incident;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DispatchPriorityTest {

    @Test
    void criticalBeatsSeriousAtTheSameWait() {
        assertThat(DispatchPriority.score(Severity.CRITICAL, 60))
                .isGreaterThan(DispatchPriority.score(Severity.SERIOUS, 60));
    }

    @Test
    void longWaitingMinorCallIsNotStarvedForever() {
        // a minor call waiting 50 minutes outranks a critical call that just came in
        assertThat(DispatchPriority.score(Severity.MINOR, 50 * 60))
                .isGreaterThan(DispatchPriority.score(Severity.CRITICAL, 0));
    }
}
