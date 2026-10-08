package com.goldenhour.dispatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code goldenhour.*} in application.yml.
 *
 * @param fleetSize           number of simulated ambulances
 * @param live                live demo settings (the dashboard can change most of them at runtime)
 * @param ackTimeoutSeconds   crew must acknowledge a dispatch within this many seconds or it is reassigned
 * @param candidateAmbulances how many nearest-by-air ambulances get a road ETA computed per call
 */
@ConfigurationProperties(prefix = "goldenhour")
public record GoldenHourProperties(int fleetSize, Live live, int ackTimeoutSeconds, int candidateAmbulances) {

    /**
     * @param enabled          run the simulated fleet, call generator and schedulers (off in some tests)
     * @param speed            simulated seconds per real second
     * @param autoCalls        generate calls from the city model
     * @param callsPerDay      call volume for the generator
     * @param prePositioning   USP 1 on/off
     * @param greenCorridor    USP 3 on/off
     * @param noAckProbability chance a simulated crew never acknowledges (tests the 30 s reassignment)
     * @param breakdownProbability chance per trip that the ambulance breaks down on the way to the scene
     */
    public record Live(boolean enabled, double speed, boolean autoCalls, double callsPerDay,
                       boolean prePositioning, boolean greenCorridor, double noAckProbability,
                       double breakdownProbability) {
    }
}
