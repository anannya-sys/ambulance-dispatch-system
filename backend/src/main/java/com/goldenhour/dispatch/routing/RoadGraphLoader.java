package com.goldenhour.dispatch.routing;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.zip.GZIPInputStream;

/**
 * Reads the binary road graph produced by tools/build_road_graph.py
 * (OpenStreetMap roads of Mumbai, exported through Overture Maps).
 */
public final class RoadGraphLoader {

    private static final int MAGIC = 0x47484D52; // "GHMR"
    private static final double E6 = 1_000_000.0;

    private RoadGraphLoader() {
    }

    public static RoadGraph fromClasspath(String path) {
        InputStream raw = RoadGraphLoader.class.getClassLoader().getResourceAsStream(path);
        if (raw == null) {
            throw new IllegalStateException("Road graph not found on classpath: " + path);
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw), 1 << 16))) {
            return read(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read road graph " + path, e);
        }
    }

    private static RoadGraph read(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC || in.readInt() != 1) {
            throw new IOException("Not a GoldenHour road graph (bad magic/version)");
        }
        String[] names = new String[in.readInt()];
        for (int i = 0; i < names.length; i++) {
            byte[] b = new byte[in.readUnsignedShort()];
            in.readFully(b);
            names[i] = new String(b, java.nio.charset.StandardCharsets.UTF_8);
        }
        int n = in.readInt();
        double[] lat = new double[n];
        double[] lon = new double[n];
        for (int i = 0; i < n; i++) {
            lat[i] = in.readInt() / E6;
            lon[i] = in.readInt() / E6;
        }
        int m = in.readInt();
        int[] from = new int[m];
        int[] to = new int[m];
        byte[] cls = new byte[m];
        int[] name = new int[m];
        float[] len = new float[m];
        int[] shapeStart = new int[m + 1];
        // Shape arrays grow as we read; most edges have 2-6 points.
        double[] sLat = new double[m * 4];
        double[] sLon = new double[m * 4];
        int pts = 0;
        for (int e = 0; e < m; e++) {
            from[e] = in.readInt();
            to[e] = in.readInt();
            cls[e] = in.readByte();
            name[e] = in.readInt();
            len[e] = in.readFloat();
            int count = in.readShort();
            if (pts + count > sLat.length) {
                sLat = java.util.Arrays.copyOf(sLat, (pts + count) * 2);
                sLon = java.util.Arrays.copyOf(sLon, (pts + count) * 2);
            }
            shapeStart[e] = pts;
            for (int k = 0; k < count; k++) {
                sLat[pts] = in.readInt() / E6;
                sLon[pts] = in.readInt() / E6;
                pts++;
            }
        }
        shapeStart[m] = pts;
        boolean[] signal = new boolean[n];
        int signals = in.readInt();
        for (int i = 0; i < signals; i++) {
            signal[in.readInt()] = true;
        }
        return new RoadGraph(lat, lon, from, to, cls, name, len, shapeStart,
                java.util.Arrays.copyOf(sLat, pts), java.util.Arrays.copyOf(sLon, pts), signal, names);
    }
}
