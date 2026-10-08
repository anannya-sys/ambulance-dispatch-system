package com.goldenhour.dispatch.trip;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TripStatusTest {

    @Test
    void followsTheHappyPath() {
        assertThat(TripStatus.ASSIGNED.canMoveTo(TripStatus.EN_ROUTE)).isTrue();
        assertThat(TripStatus.EN_ROUTE.canMoveTo(TripStatus.AT_SCENE)).isTrue();
        assertThat(TripStatus.AT_SCENE.canMoveTo(TripStatus.PICKED_UP)).isTrue();
        assertThat(TripStatus.PICKED_UP.canMoveTo(TripStatus.AT_HOSPITAL)).isTrue();
        assertThat(TripStatus.AT_HOSPITAL.canMoveTo(TripStatus.CLOSED)).isTrue();
    }

    @Test
    void cannotSkipStepsOrCancelWithPatientOnBoard() {
        assertThat(TripStatus.ASSIGNED.canMoveTo(TripStatus.AT_HOSPITAL)).isFalse();
        assertThat(TripStatus.PICKED_UP.canMoveTo(TripStatus.CANCELLED)).isFalse();
        assertThat(TripStatus.CLOSED.next()).isEmpty();
    }

    @Test
    void onlyClosedAndCancelledAreFinished() {
        assertThat(TripStatus.CLOSED.isOpen()).isFalse();
        assertThat(TripStatus.CANCELLED.isOpen()).isFalse();
        assertThat(TripStatus.EN_ROUTE.isOpen()).isTrue();
    }
}
