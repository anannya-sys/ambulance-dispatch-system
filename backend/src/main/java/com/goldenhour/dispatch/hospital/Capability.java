package com.goldenhour.dispatch.hospital;

/** Facilities a hospital may or may not have. A cardiac emergency without a cath lab means a transfer. */
public enum Capability {
    CATH_LAB,
    STROKE_UNIT,
    TRAUMA_CENTRE,
    MATERNITY
}
