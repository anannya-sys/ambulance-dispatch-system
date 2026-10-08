package com.goldenhour.dispatch.hospital;

import com.goldenhour.dispatch.routing.RoadGraph;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads data/hospital-sites.csv (real locations, generic names) into {@link HospitalSite}s. */
public final class HospitalCatalog {

    private HospitalCatalog() {
    }

    public static List<HospitalSite> load(RoadGraph graph) {
        var in = HospitalCatalog.class.getClassLoader().getResourceAsStream("data/hospital-sites.csv");
        if (in == null) {
            throw new IllegalStateException("data/hospital-sites.csv missing");
        }
        List<HospitalSite> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            r.readLine(); // header
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split(",");
                double lat = Double.parseDouble(p[1]);
                double lon = Double.parseDouble(p[2]);
                out.add(HospitalSite.fromName(out.size(), p[0], lat, lon, graph.nearestNode(lat, lon)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }
}
