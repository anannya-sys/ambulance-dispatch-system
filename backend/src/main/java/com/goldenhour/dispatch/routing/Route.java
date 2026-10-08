package com.goldenhour.dispatch.routing;

import java.util.List;

/**
 * A driveable path with timing, so we can answer "where is the ambulance after t seconds?" and
 * "how long is left?" without re-routing.
 *
 * @param points        polyline as [lat, lon]
 * @param secondsAt     cumulative seconds from the start at each polyline point
 * @param totalMeters   road distance
 * @param totalSeconds  travel time including expected signal waits
 * @param signals       signalised junctions on the way, in order (for the green corridor)
 * @param via           the main road names, e.g. "Western Express Highway, SV Road"
 */
public record Route(List<double[]> points, double[] secondsAt, double totalMeters, double totalSeconds,
                    List<SignalPass> signals, String via) {

    /** A signal the route passes and when the vehicle is expected to reach it. */
    public record SignalPass(int node, double lat, double lon, double secondsFromStart) {
    }

    /** Position after {@code elapsed} seconds, interpolated between polyline points. */
    public double[] positionAt(double elapsed) {
        if (elapsed <= 0 || points.size() == 1) {
            return points.get(0);
        }
        if (elapsed >= totalSeconds) {
            return points.get(points.size() - 1);
        }
        // binary search for the segment containing `elapsed`
        int lo = 0;
        int hi = secondsAt.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (secondsAt[mid] <= elapsed) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double span = secondsAt[hi] - secondsAt[lo];
        double f = span <= 0 ? 1 : (elapsed - secondsAt[lo]) / span;
        double[] a = points.get(lo);
        double[] b = points.get(hi);
        return new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f};
    }

    /** Compass heading in degrees at {@code elapsed}, for rotating the ambulance icon. */
    public double headingAt(double elapsed) {
        double[] a = positionAt(Math.max(0, elapsed - 2));
        double[] b = positionAt(Math.min(totalSeconds, elapsed + 2));
        double dy = b[0] - a[0];
        double dx = (b[1] - a[1]) * Math.cos(Math.toRadians(a[0]));
        if (dx == 0 && dy == 0) {
            return 0;
        }
        return (Math.toDegrees(Math.atan2(dx, dy)) + 360) % 360;
    }

    public double remainingSeconds(double elapsed) {
        return Math.max(0, totalSeconds - elapsed);
    }

    /** A route of zero length (vehicle already at the destination). */
    public static Route stationary(double lat, double lon) {
        return new Route(List.of(new double[]{lat, lon}), new double[]{0}, 0, 0, List.of(), "");
    }
}
