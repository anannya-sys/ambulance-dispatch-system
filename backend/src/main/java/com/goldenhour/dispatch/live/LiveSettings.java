package com.goldenhour.dispatch.live;

import com.goldenhour.dispatch.config.GoldenHourProperties;
import org.springframework.stereotype.Component;

/** Runtime-adjustable copy of the live settings (the dashboard's control panel writes here). */
@Component
public class LiveSettings {

    private volatile boolean autoCalls;
    private volatile double callsPerDay;
    private volatile boolean prePositioning;
    private volatile boolean greenCorridor;

    public LiveSettings(GoldenHourProperties props) {
        this.autoCalls = props.live().autoCalls();
        this.callsPerDay = props.live().callsPerDay();
        this.prePositioning = props.live().prePositioning();
        this.greenCorridor = props.live().greenCorridor();
    }

    public boolean autoCalls() {
        return autoCalls;
    }

    public void setAutoCalls(boolean autoCalls) {
        this.autoCalls = autoCalls;
    }

    public double callsPerDay() {
        return callsPerDay;
    }

    public void setCallsPerDay(double callsPerDay) {
        this.callsPerDay = Math.max(0, Math.min(5000, callsPerDay));
    }

    public boolean prePositioning() {
        return prePositioning;
    }

    public void setPrePositioning(boolean prePositioning) {
        this.prePositioning = prePositioning;
    }

    public boolean greenCorridor() {
        return greenCorridor;
    }

    public void setGreenCorridor(boolean greenCorridor) {
        this.greenCorridor = greenCorridor;
    }
}
