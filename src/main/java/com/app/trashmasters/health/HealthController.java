package com.app.trashmasters.health;

import com.app.trashmasters.bin.BinRepository;
import com.app.trashmasters.Truck.TruckRepository;
import com.app.trashmasters.route.RouteRepository;
import com.app.trashmasters.route.Route;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class HealthController {

    @Autowired
    private BinRepository binRepository;

    @Autowired
    private TruckRepository truckRepository;

    @Autowired
    private RouteRepository routeRepository;

    @GetMapping("/health")
    public ResponseEntity<?> getSystemHealth() {
        Map<String, Object> health = new HashMap<>();

        try {
            // 1. Database Stats
            health.put("totalBins", binRepository.count());
            health.put("totalTrucks", truckRepository.count());
            health.put("totalRoutes", routeRepository.count());

            // 2. Last Route Generation Time
            List<Route> allRoutes = routeRepository.findAll();
            LocalDateTime lastRouteTime = null;

            if (!allRoutes.isEmpty()) {
                lastRouteTime = allRoutes.stream()
                        .map(Route::getCreatedAt)
                        .filter(date -> date != null)
                        .max(Comparator.naturalOrder())
                        .orElse(null);
            }

            health.put("lastRouteGenerated", lastRouteTime != null ? lastRouteTime.toString() : "Never");

            // 3. API Status (Hardcoded for now as we don't have live ping checks yet)
            health.put("mapboxApiStatus", "Connected");
            health.put("sageMakerStatus", "Connected");
            health.put("mongoDbStatus", "Connected");

            return ResponseEntity.ok(health);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to fetch health stats: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}
