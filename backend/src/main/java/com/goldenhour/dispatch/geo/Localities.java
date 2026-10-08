package com.goldenhour.dispatch.geo;

import java.util.Comparator;
import java.util.List;

/** Approximate centres of Mumbai neighbourhoods, used only to give posts and zones readable names. */
public final class Localities {

    public record Locality(String name, double lat, double lon) {
    }

    public static final List<Locality> ALL = List.of(
            new Locality("Colaba", 18.9067, 72.8147), new Locality("Fort", 18.9339, 72.8355),
            new Locality("Marine Lines", 18.9447, 72.8236), new Locality("Girgaon", 18.9550, 72.8150),
            new Locality("Grant Road", 18.9635, 72.8160), new Locality("Byculla", 18.9790, 72.8330),
            new Locality("Mazgaon", 18.9650, 72.8440), new Locality("Tardeo", 18.9700, 72.8130),
            new Locality("Mahalaxmi", 18.9820, 72.8240), new Locality("Worli", 19.0000, 72.8150),
            new Locality("Lower Parel", 18.9960, 72.8300), new Locality("Parel", 19.0030, 72.8410),
            new Locality("Sewri", 19.0000, 72.8560), new Locality("Dadar", 19.0190, 72.8430),
            new Locality("Prabhadevi", 19.0160, 72.8290), new Locality("Wadala", 19.0170, 72.8650),
            new Locality("Matunga", 19.0270, 72.8550), new Locality("Mahim", 19.0410, 72.8400),
            new Locality("Sion", 19.0430, 72.8620), new Locality("Dharavi", 19.0400, 72.8550),
            new Locality("Bandra West", 19.0600, 72.8360), new Locality("Bandra East", 19.0600, 72.8500),
            new Locality("BKC", 19.0660, 72.8680), new Locality("Kurla", 19.0720, 72.8830),
            new Locality("Chembur", 19.0620, 72.9000), new Locality("Khar", 19.0720, 72.8370),
            new Locality("Santacruz", 19.0820, 72.8400), new Locality("Kalina", 19.0750, 72.8600),
            new Locality("Vile Parle", 19.1000, 72.8450), new Locality("Ghatkopar", 19.0860, 72.9080),
            new Locality("Andheri West", 19.1360, 72.8300), new Locality("Andheri East", 19.1150, 72.8650),
            new Locality("Saki Naka", 19.1030, 72.8880), new Locality("Powai", 19.1180, 72.9050),
            new Locality("Vikhroli", 19.1110, 72.9280), new Locality("Jogeshwari", 19.1400, 72.8550),
            new Locality("Goregaon", 19.1630, 72.8500), new Locality("Malad", 19.1860, 72.8480),
            new Locality("Bhandup", 19.1450, 72.9380), new Locality("Mulund", 19.1720, 72.9560),
            new Locality("Juhu", 19.1000, 72.8270), new Locality("Versova", 19.1310, 72.8150),
            new Locality("Kanjurmarg", 19.1290, 72.9300), new Locality("Govandi", 19.0550, 72.9150),
            new Locality("Mankhurd", 19.0480, 72.9300), new Locality("Kandivali", 19.2050, 72.8510),
            new Locality("Trombay", 19.0100, 72.9250), new Locality("Vashi", 19.0770, 72.9980));

    private Localities() {
    }

    public static String nearest(double lat, double lon) {
        return ALL.stream()
                .min(Comparator.comparingDouble(l -> GeoUtils.distanceMeters(lat, lon, l.lat(), l.lon())))
                .map(Locality::name).orElse("Mumbai");
    }
}
