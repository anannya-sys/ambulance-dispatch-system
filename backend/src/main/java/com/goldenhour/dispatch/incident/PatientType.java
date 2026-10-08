package com.goldenhour.dispatch.incident;

/** What kind of emergency it is; decides which hospital can treat the patient. */
public enum PatientType {
    CARDIAC,
    STROKE,
    TRAUMA,
    RESPIRATORY,
    MATERNITY,
    GENERAL
}
