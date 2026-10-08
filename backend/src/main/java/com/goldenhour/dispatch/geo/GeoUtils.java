package com.goldenhour.dispatch.geo;

/** Small geometry helpers. All coordinates are WGS84 degrees. */
public final class GeoUtils {

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private GeoUtils() {
    }

    /** Great-circle distance in metres (haversine formula). */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }

    /** Linear interpolation between two points; fine for the short distances between shape points. */
    public static double[] interpolate(double lat1, double lon1, double lat2, double lon2, double fraction) {
        return new double[]{lat1 + (lat2 - lat1) * fraction, lon1 + (lon2 - lon1) * fraction};
    }
}
