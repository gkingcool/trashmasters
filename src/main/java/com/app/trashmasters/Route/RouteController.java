package com.app.trashmasters.route;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.app.trashmasters.route.RouteService;
import com.app.trashmasters.route.RouteRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/routes")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"}) // ✅ Allow both ports
@Tag(name = "Routes", description = "Route generation, lifecycle, and driver operations")
public class RouteController {

    private final RouteService routeService;
    private final RouteRepository routeRepository;

    public RouteController(RouteService routeService,  RouteRepository routeRepository) {
        this.routeService = routeService;
        this.routeRepository = routeRepository;
    }

    // ==========================================
    // GENERATE ROUTES
    // ==========================================
    // Example: POST /api/routes/generate?trucks=2&date=2026-04-19&time=07:00
    @Operation(summary = "Generate optimized routes", description = "Runs VRP solver on all eligible bins. Replaces any existing routes for the given date.")
    @PostMapping("/generate")
    public ResponseEntity<?> generateRoutes(
            @Parameter(description = "Number of trucks to dispatch") @RequestParam(defaultValue = "3") int trucks,
            @Parameter(description = "Route date (yyyy-MM-dd), e.g. 2026-04-19") @RequestParam String date,
            @Parameter(description = "Shift start time (HH:mm), e.g. 07:00") @RequestParam(defaultValue = "07:00") String time,
            @RequestParam(defaultValue = "predictive") String strategy) {
        try {
            LocalDate routeDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
            LocalTime startTime = LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"));

            GenerateRoutesResponse response = routeService.generateRoutes(trucks, routeDate, startTime, strategy);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            System.err.println("🔥 ROUTE GENERATION ERROR: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Route generation failed: " + e.getMessage());
        }
    }

    @Operation(summary = "Get routes by date", description = "Returns all saved routes for a specific date")
    @GetMapping("/by-date/{date}")
    public ResponseEntity<?> getRoutesByDate(@PathVariable String date) {
        try {
            LocalDate routeDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
            List<Route> routes = routeRepository.findByRouteDate(routeDate);
            return ResponseEntity.ok(routes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Failed to fetch routes: " + e.getMessage());
        }
    }

    // ==========================================
    // END OF DAY RECONCILIATION
    // ==========================================
    @Operation(summary = "End-of-day reconciliation", description = "Closes all routes for the shift, updates bin and truck state for tomorrow")
    @PostMapping("/end-of-day")
    public ResponseEntity<String> completeShift(@RequestBody EndOfDayRequestDTO shiftReport) {
        try {
            if (shiftReport == null || shiftReport.getCompletedRoutes() == null) {
                return ResponseEntity.badRequest().body("Invalid shift report payload.");
            }
            routeService.processEndOfDay(shiftReport);
            return ResponseEntity.ok("Shift closed successfully. Bins and Trucks updated for tomorrow.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Failed to process end of day: " + e.getMessage());
        }
    }

    // ==========================================
    // SINGLE ROUTE COMPLETION
    // ==========================================
    @Operation(summary = "Complete a single route", description = "Marks one driver's route as finished and updates bin/truck state")
    @PostMapping("/complete")
    public ResponseEntity<String> completeSingleRoute(@RequestBody SingleRouteCompletionDTO request) {
        try {
            if (request == null || request.getCompletedRoute() == null) {
                return ResponseEntity.badRequest().body("Invalid route completion payload.");
            }
            routeService.processSingleRouteCompletion(request);
            return ResponseEntity.ok("Route successfully closed for driver: "
                    + request.getCompletedRoute().getDriverId());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Failed to close route: " + e.getMessage());
        }
    }

    // ==========================================
    // DRIVER TABLET: SKIP A BIN
    // ==========================================
    @Operation(summary = "Skip a bin in real-time", description = "Driver marks a bin as skipped (e.g. blocked). Penalty applied for next day")
    @PostMapping("/skip/{binId}")
    public ResponseEntity<String> registerRealTimeSkip(@PathVariable String binId) {
        try {
            if (binId == null || binId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Invalid Bin ID provided.");
            }
            routeService.processRealTimeBinSkip(binId);
            return ResponseEntity.ok("Bin " + binId + " skipped. Penalty applied for tomorrow.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Failed to skip bin: " + e.getMessage());
        }
    }

    // ==========================================
    // ALL ROUTES (history)
    // ==========================================
    @Operation(summary = "Get all routes", description = "Returns full route history")
    @GetMapping("/all")
    public ResponseEntity<?> getAllRoutes() {
        try {
            List<Route> routes = routeService.getAllRoutes();
            return ResponseEntity.ok(routes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Failed to fetch routes: " + e.getMessage());
        }
    }

    // ==========================================
    // ROUTES BY STATUS (e.g., /api/routes/status/CREATED)
    // ==========================================
    @Operation(summary = "Get routes by status", description = "Filter routes by status: CREATED, IN_PROGRESS, COMPLETED")
    @GetMapping("/status/{status}")
    public ResponseEntity<?> getRoutesByStatus(@PathVariable String status) {
        try {
            List<Route> routes = routeService.getRoutesByStatus(status);
            return ResponseEntity.ok(routes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Failed to fetch routes: " + e.getMessage());
        }
    }

    // ==========================================
    // SEARCH ROUTES (flexible filter)
    // ==========================================
    @Operation(summary = "Search routes", description = "Search by any combination of routeDate, driverId, truckId, status. Empty body returns all routes.")
    @PostMapping("/search")
    public ResponseEntity<?> searchRoutes(@RequestBody RouteSearchRequest request) {
        try {
            List<Route> routes = routeService.searchRoutes(request);
            return ResponseEntity.ok(routes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Search failed: " + e.getMessage());
        }
    }

    // ==========================================
    // GET ROUTE BY ID
    // ==========================================
    @Operation(summary = "Get route by ID")
    @GetMapping("/{id}")
    public ResponseEntity<?> getRouteById(@PathVariable String id) {
        try {
            Route route = routeService.getRouteById(id);
            return ResponseEntity.ok(route);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ==========================================
    // MARK ROUTE AS COMPLETED
    // ==========================================
    @Operation(summary = "Mark route as completed")
    @PatchMapping("/{id}/complete")
    public ResponseEntity<?> completeRoute(@PathVariable String id) {
        try {
            Route route = routeService.completeRoute(id);
            return ResponseEntity.ok(route);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ==========================================
    // GET ALL ACTIVE ROUTES FOR A DRIVER
    // ==========================================
    @Operation(summary = "Get routes for a driver", description = "Returns all active routes assigned to a specific driver")
    @GetMapping("/driver/{driverId}")
    public ResponseEntity<?> getRoutesByDriver(@PathVariable String driverId) {
        try {
            List<Route> routes = routeService.getRoutesByDriver(driverId);
            return ResponseEntity.ok(routes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Failed to fetch routes: " + e.getMessage());
        }
    }
}