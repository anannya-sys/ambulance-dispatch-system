package com.goldenhour.dispatch.geo;

/**
 * A ~1.1 km square cell (0.01 degrees) used to aggregate demand and response times.
 * Packing row/column into one int keeps maps and arrays cheap.
 */
public record GridCell(int row, int col) {

    public static final double SIZE_DEG = 0.01;

    public static GridCell of(double lat, double lon) {
        return new GridCell((int) Math.floor(lat / SIZE_DEG), (int) Math.floor(lon / SIZE_DEG));
    }

    public double centerLat() {
        return (row + 0.5) * SIZE_DEG;
    }

    public double centerLon() {
        return (col + 0.5) * SIZE_DEG;
    }

    public String id() {
        return row + ":" + col;
    }
}
