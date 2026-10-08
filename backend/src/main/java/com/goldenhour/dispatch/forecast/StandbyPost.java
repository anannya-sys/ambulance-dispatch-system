package com.goldenhour.dispatch.forecast;

/**
 * A place an idle ambulance can wait: in reality a fire station, police chowki or petrol pump on a main
 * road. {@code index} is its row in the posts {@link com.goldenhour.dispatch.routing.TravelTimeTable}.
 */
public record StandbyPost(int index, String name, int node, double lat, double lon) {
}
