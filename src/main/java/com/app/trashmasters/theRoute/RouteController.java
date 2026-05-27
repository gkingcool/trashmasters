package com.app.trashmasters.theRoute;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
     @Operation(
        summary = "Close out one or more routes",
        description = "Updates truck loads and applies overdue penalties to skipped bins. " +
                      "Works for a single driver finishing early (one entry in completedRoutes) " +
                      "or the full fleet at end of shift (multiple entries).")
    @PostMapping("/end-of-day")
    public ResponseEntity<?> completeShift(@RequestBody EndOfDayRequestDTO shiftReport) {
        try {
            if (shiftReport == null || shiftReport.getCompletedRoutes() == null) {
                return ResponseEntity.badRequest().body("Invalid shift report payload.");
            }
            EndOfDayResponseDTO response = routeService.processEndOfDay(shiftReport);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Failed to process end of day: " + e.getMessage());
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
    // DRIVER TABLET: CONFIRM BIN PICKUP
    // ==========================================
    @Operation(
        summary = "Confirm individual bin pickup",
        description = "Driver confirms a bin was collected. Immediately resets bin fillLevel to 0 and clears any overdue penalty. " +
                      "Call this in real-time as each bin is serviced — do NOT wait until end-of-day, " +
                      "as sensors will overwrite fill level with fresh readings during the shift.")
    @PostMapping("/{routeId}/pickup/{binId}")
    public ResponseEntity<String> confirmBinPickup(
            @PathVariable String routeId,
            @PathVariable String binId) {
        try {
            routeService.confirmBinPickup(routeId, binId);
            return ResponseEntity.ok("Bin " + binId + " pickup confirmed. Fill level cleared.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Failed to confirm pickup: " + e.getMessage());
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
    // DELETE ROUTE BY ID
    // ==========================================
    @Operation(summary = "Delete a route", description = "Permanently deletes a route by its MongoDB ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRoute(@PathVariable String id) {
        try {
            routeService.getRouteById(id); // throws if not found
            routeRepository.deleteById(id);
            return ResponseEntity.ok("Route " + id + " deleted successfully.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to delete route: " + e.getMessage());
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
    // ADMIN: OVERRIDE ROUTE STATUS
    // ==========================================
    @Operation(
        summary = "Override route status (admin)",
        description = "Manually sets the status of a route. Valid values: CREATED, IN_PROGRESS, COMPLETED. " +
                      "This is an admin flag only — it does NOT update truck loads or bin overdue counters. " +
                      "Use POST /complete or POST /end-of-day for real operational close-out.")
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateRouteStatus(
            @PathVariable String id,
            @Parameter(description = "New status: CREATED, IN_PROGRESS, or COMPLETED") @RequestParam String status) {
        try {
            Route route = routeService.updateRouteStatus(id, status);
            return ResponseEntity.ok(route);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ==========================================
    // ASSIGN / REASSIGN DRIVER
    // ==========================================
    @Operation(
        summary = "Assign a driver to a route",
        description = "Overrides the driver on an existing route. Use this when you want a different driver than the one assigned to the truck.")
    @PatchMapping("/{id}/assign-driver")
    public ResponseEntity<?> assignDriver(
            @PathVariable String id,
            @Parameter(description = "Driver ID to assign, e.g. DRV-007") @RequestParam String driverId) {
        try {
            Route route = routeService.assignDriver(id, driverId);
            return ResponseEntity.ok(route);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
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