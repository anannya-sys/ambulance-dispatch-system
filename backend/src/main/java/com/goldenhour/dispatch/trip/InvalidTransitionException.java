package com.goldenhour.dispatch.trip;

public class InvalidTransitionException extends RuntimeException {

    public InvalidTransitionException(TripStatus from, TripStatus to) {
        super("Trip cannot move from " + from + " to " + to);
    }
}
