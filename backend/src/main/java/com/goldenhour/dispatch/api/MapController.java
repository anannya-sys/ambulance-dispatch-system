package com.goldenhour.dispatch.api;

import com.goldenhour.dispatch.forecast.RebalanceService;
import com.goldenhour.dispatch.routing.CityModel;
import com.goldenhour.dispatch.routing.RoadClass;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/map")
@Tag(name = "Map", description = "Static map data for the dashboard")
public class MapController {

    private final CityModel city;
    private volatile List<double[][]> roads;

    public MapController(CityModel city) {
        this.city = city;
    }

    @GetMapping("/bootstrap")
    @Operation(summary = "Bounds and standby posts")
    public Map<String, Object> bootstrap() {
        return Map.of("bounds", city.graph().bounds(),
                "center", new double[]{19.06, 72.87},
                "posts", city.posts().stream().map(p -> new RebalanceService.PostView(p.index(), p.name(), p.lat(), p.lon())).toList());
    }

    /** Main roads as polylines, so the map still shows streets when map tiles can't be downloaded (offline). */
    @GetMapping("/roads")
    @Operation(summary = "Main-road polylines for the offline base map")
    public ResponseEntity<List<double[][]>> roads() {
        if (roads == null) {
            roads = city.graph().polylines(RoadClass.TERTIARY);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS)).body(roads);
    }
}
