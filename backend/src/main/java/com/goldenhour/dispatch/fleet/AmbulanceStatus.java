package com.goldenhour.dispatch.fleet;

/** Operational state of one vehicle. Only AVAILABLE and REPOSITIONING units can be dispatched. */
public enum AmbulanceStatus {
    AVAILABLE,
    REPOSITIONING,
    DISPATCHED,
    EN_ROUTE,
    AT_SCENE,
    TRANSPORTING,
    AT_HOSPITAL,
    OUT_OF_SERVICE;

    public boolean isDispatchable() {
        return this == AVAILABLE || this == REPOSITIONING;
    }
}
