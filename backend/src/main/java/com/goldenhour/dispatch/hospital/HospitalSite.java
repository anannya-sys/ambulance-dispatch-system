package com.goldenhour.dispatch.hospital;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Static facts about a hospital: a real hospital <i>location</i> in Mumbai with a generic name, and a
 * simulated capability/bed profile derived from its type.
 */
public record HospitalSite(int index, String name, double lat, double lon, int node,
                           Set<Capability> capabilities, Map<BedType, Integer> beds,
                           int erBays, boolean specialists24x7) {

    /** Profiles by hospital type (the last words of the generated name). */
    static HospitalSite fromName(int index, String name, double lat, double lon, int node) {
        EnumMap<BedType, Integer> beds = new EnumMap<>(BedType.class);
        Set<Capability> caps;
        int bays;
        boolean full;
        if (name.contains("Medical College")) {
            caps = EnumSet.allOf(Capability.class);
            put(beds, 10, 6, 12, 40);
            bays = 6;
            full = true;
        } else if (name.contains("Municipal")) {
            caps = EnumSet.of(Capability.TRAUMA_CENTRE, Capability.MATERNITY);
            put(beds, 5, 0, 8, 30);
            bays = 4;
            full = false;
        } else if (name.contains("Multispeciality")) {
            caps = EnumSet.of(Capability.CATH_LAB, Capability.STROKE_UNIT);
            put(beds, 8, 6, 1, 16);
            bays = 3;
            full = false;
        } else if (name.contains("Heart & Trauma")) {
            caps = EnumSet.of(Capability.CATH_LAB, Capability.TRAUMA_CENTRE);
            put(beds, 5, 6, 8, 8);
            bays = 3;
            full = false;
        } else {
            caps = EnumSet.of(Capability.MATERNITY);
            put(beds, 3, 0, 0, 20);
            bays = 3;
            full = false;
        }
        return new HospitalSite(index, name, lat, lon, node, caps, beds, bays, full);
    }

    private static void put(Map<BedType, Integer> beds, int icu, int cardiac, int trauma, int general) {
        beds.put(BedType.ICU, icu);
        beds.put(BedType.CARDIAC, cardiac);
        beds.put(BedType.TRAUMA, trauma);
        beds.put(BedType.GENERAL, general);
    }
}
