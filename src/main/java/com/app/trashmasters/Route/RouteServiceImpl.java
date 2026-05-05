package com.app.trashmasters.route;

import com.app.trashmasters.routing.DistanceMatrixService;
import com.app.trashmasters.routing.SmartRoutingService;
import com.app.trashmasters.Truck.Truck;
import com.app.trashmasters.Truck.TruckRepository;
import com.app.trashmasters.bin.BinRepository;
import com.app.trashmasters.bin.model.Bin;
import com.app.trashmasters.bin.model.BinStatus;
import com.app.trashmasters.bin.model.Location;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RouteServiceImpl implements RouteService {

    private final RouteRepository routeRepository;
    private final BinRepository binRepository;
    private final TruckRepository truckRepository;
    private final DistanceMatrixService distanceMatrixService;
    private final SmartRoutingService smartRoutingService;
    private final MongoTemplate mongoTemplate;

    // Injected from application.properties
    @Value("${route.station.lat}")
    private double stationLat;
    @Value("${route.station.lon}")
    private double stationLon;
    @Value("${route.dump.lat}")
    private double dumpLat;
    @Value("${route.dump.lon}")
    private double dumpLon;

    @Value("${route.threshold.physical:50.0}")
    private double physicalThreshold;
    @Value("${route.threshold.predicted:80.0}")
    private double predictedThreshold;
    @Value("${route.threshold.truck-dump-percent:0.85}")
    private double truckDumpPercent;
    @Value("${route.threshold.dump-trip-minutes:30}")
    private long dumpTripMinutes;

    public RouteServiceImpl(RouteRepository routeRepository,
                            BinRepository binRepository,
                            TruckRepository truckRepository,
                            DistanceMatrixService distanceMatrixService,
                            SmartRoutingService smartRoutingService,
                            MongoTemplate mongoTemplate) {
        this.routeRepository = routeRepository;
        this.binRepository = binRepository;
        this.truckRepository = truckRepository;
        this.distanceMatrixService = distanceMatrixService;
        this.smartRoutingService = smartRoutingService;
        this.mongoTemplate = mongoTemplate;
    }

    private Location getStation() { return new Location(stationLat, stationLon); }
    private Location getDump()    { return new Location(dumpLat, dumpLon); }

    // ==========================================
    // CORE: ROUTE GENERATION
    // ==========================================

    @Override
    public GenerateRoutesResponse generateRoutes(int numberOfTrucks, LocalDate routeDate, LocalTime startTime, String strategy) {

        // 0. Validate: route date cannot be in the past
        if (routeDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Route date " + routeDate + " is in the past. Cannot generate routes for past dates.");
        }

        LocalDateTime startDateTime = LocalDateTime.of(routeDate, startTime);

        // 0b. Delete existing routes for this date (replace with fresh generation)
        List<Route> existing = routeRepository.findByRouteDate(routeDate);
        if (!existing.isEmpty()) {
            routeRepository.deleteByRouteDate(routeDate);
            System.out.println("🗑️ Deleted " + existing.size() + " existing routes for " + routeDate + " — replacing with new generation.");
        }

        // 1. Get available trucks (limit to requested number)
        List<Truck> allTrucks = truckRepository.findAll();
        if (allTrucks.isEmpty()) {
            throw new RuntimeException("No trucks found in the database.");
        }
        List<Truck> activeTrucks = allTrucks.stream()
                .limit(numberOfTrucks)
                .collect(Collectors.toList());

        if (activeTrucks.size() < numberOfTrucks) {
            System.out.println("⚠️ Requested " + numberOfTrucks + " trucks but only "
                    + activeTrucks.size() + " available.");
        }

        // 2. Find bins that need pickup (full, predicted full, or overdue)
        List<Bin> targetBins = getTargetBins(startDateTime, strategy);
        if (targetBins.isEmpty()) {
            throw new RuntimeException("No bins require pickup at this time.");
        }

        // 2b. Sort: overdue first, then by effective fill level descending
        //     This ensures the solver processes highest-priority bins first
        targetBins.sort((a, b) -> {
            int overdueA = a.getDaysOverdue() != null ? a.getDaysOverdue() : 0;
            int overdueB = b.getDaysOverdue() != null ? b.getDaysOverdue() : 0;

            // Overdue bins always come first
            if (overdueA > 0 && overdueB == 0) return -1;
            if (overdueA == 0 && overdueB > 0) return 1;
            if (overdueA != overdueB) return Integer.compare(overdueB, overdueA); // more overdue first

            // Then by effective fill (max of current fill and max prediction), descending
            double fillA = Math.max(
                    a.getFillLevel() != null ? a.getFillLevel() : 0.0,
                    getMaxPrediction(a));
            double fillB = Math.max(
                    b.getFillLevel() != null ? b.getFillLevel() : 0.0,
                    getMaxPrediction(b));
            return Double.compare(fillB, fillA); // highest fill first
        });

        System.out.println("🗑️ Target bins for pickup (sorted by priority): " + targetBins.size());
        for (Bin b : targetBins) {
            double maxPred = getMaxPrediction(b);
            System.out.println("   ✅ " + b.getBinId()
                    + " | fill=" + b.getFillLevel() + "%"
                    + " | predicted=" + Math.round(maxPred) + "%"
                    + " | overdue=" + b.getDaysOverdue()
                    + " | capacity=" + b.getCapacityYards() + "yd"
                    + " | zone=" + b.getZone());
        }

        // 3. Build the distance/time matrix via Mapbox
        List<Location> binLocations = targetBins.stream()
                .map(Bin::getLocation)
                .collect(Collectors.toList());

        Location station = getStation();
        long[][] timeMatrix = distanceMatrixService.calculateTimeMatrix(station, binLocations);

        // 4. Run OR-Tools VRP solver
        List<RouteDTO> solverRoutes = smartRoutingService.generateRoutes(
                timeMatrix, targetBins, activeTrucks, station);

        // 5. Post-process: insert dump trips when truck capacity is exceeded
        for (RouteDTO route : solverRoutes) {
            insertDumpTripsIfNeeded(route, targetBins, activeTrucks);
        }

        // 6. Persist routes to MongoDB
        for (RouteDTO routeDTO : solverRoutes) {
            Route routeEntity = new Route();
            routeEntity.setDriverId(routeDTO.getDriverId());
            routeEntity.setTruckId(routeDTO.getTruckId());
            routeEntity.setRouteDate(routeDate);
            routeEntity.setBinIds(
                    routeDTO.getSteps().stream()
                            .filter(s -> "BIN".equals(s.getType()))
                            .map(RouteStepDTO::getBinId)
                            .collect(Collectors.toList())
            );
            routeEntity.setStatus("CREATED");
            routeEntity.setCreatedAt(startDateTime);
            routeEntity.setTotalStops(routeDTO.getSteps().size());

            // Save full route plan & metrics
            routeEntity.setSteps(routeDTO.getSteps());
            routeEntity.setTotalTimeMinutes(routeDTO.getTotalTimeMinutes());
            routeEntity.setStartingTruckLoadYards(routeDTO.getStartingTruckLoadYards());
            routeEntity.setEndingTruckVolumeYards(routeDTO.getEndingTruckVolumeYards());

            routeRepository.save(routeEntity);
        }

        // 7. Identify urgent unassigned bins (≥75% full or overdue, but solver couldn't fit them)
        java.util.Set<String> assignedBinIds = solverRoutes.stream()
                .flatMap(r -> r.getSteps().stream())
                .filter(s -> "BIN".equals(s.getType()) && s.getBinId() != null)
                .map(RouteStepDTO::getBinId)
                .collect(Collectors.toSet());

        List<UnassignedBinDTO> urgentUnassigned = targetBins.stream()
                .filter(bin -> !assignedBinIds.contains(bin.getBinId()))
                .filter(bin -> {
                    double fill = bin.getFillLevel() != null ? bin.getFillLevel() : 0.0;
                    double maxPred = getMaxPrediction(bin);
                    int overdue = bin.getDaysOverdue() != null ? bin.getDaysOverdue() : 0;
                    return fill >= 75.0 || maxPred >= 75.0 || overdue > 0;
                })
                .map(bin -> {
                    double fill = bin.getFillLevel() != null ? bin.getFillLevel() : 0.0;
                    double maxPred = getMaxPrediction(bin);
                    int overdue = bin.getDaysOverdue() != null ? bin.getDaysOverdue() : 0;

                    String reason;
                    if (overdue > 0) reason = "OVERDUE: " + overdue + " day(s) skipped";
                    else if (fill >= 80.0 || maxPred >= 80.0) reason = "URGENT: ≥80% full";
                    else reason = "WARNING: ≥75% full";

                    return new UnassignedBinDTO(
                            bin.getBinId(), fill, maxPred, overdue,
                            bin.getCapacityYards() != null ? bin.getCapacityYards() : 0,
                            bin.getZone() != null ? bin.getZone().name() : "UNKNOWN",
                            reason);
                })
                .collect(Collectors.toList());

        if (!urgentUnassigned.isEmpty()) {
            System.out.println("🚨 " + urgentUnassigned.size() + " urgent bins could NOT fit into routes!");
            urgentUnassigned.forEach(u -> System.out.println("   ⚠️ " + u.getBinId() + " — " + u.getReason()));
        }

        GenerateRoutesResponse response = new GenerateRoutesResponse();
        response.setRoutes(solverRoutes);
        response.setUrgentUnassignedBins(urgentUnassigned);
        return response;
    }

    // ==========================================
    // BIN FILTERING: The intelligence layer
    // ==========================================

    /** Returns the highest predicted fill % across all horizons (4h, 8h, 12h) */
    private double getMaxPrediction(Bin bin) {
        if (bin.getFuturePredictions() == null) return 0.0;
        return bin.getFuturePredictions().values().stream()
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .max().orElse(0.0);
    }

    /**
     * Finds all bins that should be picked up. Excludes maintenance/flagged bins.
     * Picks bins based on:
     *  - Current fill level >= physical threshold
     *  - ML prediction >= predicted threshold (time-horizon-aware)
     *  - Overdue from previous days (skipped bins get priority)
     */
    private List<Bin> getTargetBins(LocalDateTime startDateTime, String strategy) {
        List<Bin> allBins = binRepository.findAll();

        // Choose prediction horizons based on shift start time
        // Morning (before noon) → look at 8h and 12h predictions
        // Afternoon (noon+)    → look at 4h and 8h predictions
        int primaryHorizon = 8;
        int secondaryHorizon = 12;
        if (startDateTime.getHour() >= 12) {
            primaryHorizon = 4;
            secondaryHorizon = 8;
        }

        final int pH = primaryHorizon;
        final int sH = secondaryHorizon;
        final boolean usePredictions = "predictive".equalsIgnoreCase(strategy); // ✅ STRATEGY FLAG

        System.out.println(" Using strategy: " + strategy + " | AI Predictions: " + usePredictions);


        return allBins.stream()
                .filter(bin -> {
                    // EXCLUDE: flagged for maintenance, broken sensor, or missing location
                    if (bin.isFlagged()) return false;
                    if (bin.getStatus() == BinStatus.MAINTENANCE) return false;
                    if (bin.getLocation() == null) return false;

                    // EXCLUDE: bins outside our service range (2-8 yard commercial/public bins)
                    if (bin.getCapacityYards() == null
                            || bin.getCapacityYards() < 2
                            || bin.getCapacityYards() > 8) return false;

                    // A) Currently full enough to pick up
                    boolean isFull = bin.getFillLevel() != null
                            && bin.getFillLevel() >= physicalThreshold;

                    // B) ML predicts it will be full within the shift window
                    boolean isPredictedFull = false;
                    if (usePredictions && bin.getFuturePredictions() != null) {
                        Double primary = bin.getFuturePredictions().get(pH);
                        Double secondary = bin.getFuturePredictions().get(sH);
                        isPredictedFull = (primary != null && primary >= predictedThreshold)
                                || (secondary != null && secondary >= predictedThreshold);
                    }

                    // C) Overdue — was skipped on a previous day
                    boolean isOverdue = bin.getDaysOverdue() != null && bin.getDaysOverdue() > 0;

                    return isFull || isPredictedFull || isOverdue;
                })
                .collect(Collectors.toList());
    }

    // ==========================================
    // PHYSICS: Dump trip insertion
    // ==========================================

    /**
     * Walks through a route step-by-step, tracking cumulative truck load.
     * When the load would exceed the dump threshold, inserts a DUMP step
     * so the truck empties before continuing.
     * Also updates currentTruckLoadYards on every step.
     */
    private void insertDumpTripsIfNeeded(RouteDTO route, List<Bin> targetBins,
                                         List<Truck> trucks) {
        Truck truck = trucks.stream()
                .filter(t -> t.getTruckId().equals(route.getTruckId()))
                .findFirst().orElse(null);
        if (truck == null) return;

        double maxCapacity = truck.getMaxCapacityYards();
        double dumpThreshold = maxCapacity * truckDumpPercent;
        double startingLoad = truck.getCurrentCompactedYards() != null
                ? truck.getCurrentCompactedYards() : 0.0;
        double currentLoad = startingLoad;

        // Set the starting load on the RouteDTO
        route.setStartingTruckLoadYards(Math.round(startingLoad * 100.0) / 100.0);

        Location dump = getDump();
        List<RouteStepDTO> originalSteps = route.getSteps();
        List<RouteStepDTO> newSteps = new ArrayList<>();
        long etaOffset = 0; // cumulative time added by dump detours

        for (RouteStepDTO step : originalSteps) {
            // Apply accumulated dump-trip time offset to this step's ETA
            step.setEstimatedArrivalMinutes(step.getEstimatedArrivalMinutes() + etaOffset);

            if ("BIN".equals(step.getType()) && step.getBinId() != null) {
                double binLoad = calculateBinCompactedLoad(step.getBinId(), targetBins);

                // Insert dump trip BEFORE this bin if picking it up would exceed threshold
                if (currentLoad + binLoad > dumpThreshold) {
                    // Dump step happens at the current ETA (before the bin pickup)
                    long dumpEta = step.getEstimatedArrivalMinutes();
                    newSteps.add(new RouteStepDTO(
                            dump.getLat(), dump.getLon(),
                            "DUMP", null, "EMPTY_TRUCK",
                            dumpEta, 0.0));
                    currentLoad = 0;

                    // Offset this bin and all future steps by the dump round-trip time
                    etaOffset += dumpTripMinutes;
                    step.setEstimatedArrivalMinutes(step.getEstimatedArrivalMinutes() + dumpTripMinutes);

                    System.out.println("🚛 Truck " + route.getTruckId()
                            + " → dump trip inserted before bin " + step.getBinId()
                            + " (+" + dumpTripMinutes + " min)");
                }

                currentLoad += binLoad;
                step.setCurrentTruckLoadYards(Math.round(currentLoad * 100.0) / 100.0);
            } else {
                // STATION (START or END) — show current load at that point
                step.setCurrentTruckLoadYards(Math.round(currentLoad * 100.0) / 100.0);
            }
            newSteps.add(step);
        }

        route.setSteps(newSteps);
        route.setEndingTruckVolumeYards(Math.round(currentLoad * 100.0) / 100.0);

        // Update total time to include dump detours
        route.setTotalTimeMinutes(route.getTotalTimeMinutes() + etaOffset);
    }

    /**
     * Calculates compacted cubic yards for a single bin pickup.
     * Uses max(current fill, 8h prediction), applies 4:1 compaction ratio.
     */
    private double calculateBinCompactedLoad(String binId, List<Bin> targetBins) {
        Bin bin = targetBins.stream()
                .filter(b -> binId.equals(b.getBinId()))
                .findFirst().orElse(null);
        if (bin == null || bin.getCapacityYards() == null) return 0.0;

        double currentFill = bin.getFillLevel() != null ? bin.getFillLevel() : 0.0;
        double futureFill = 0.0;
        if (bin.getFuturePredictions() != null) {
            Double pred = bin.getFuturePredictions().get(8);
            if (pred != null) futureFill = pred;
        }

        double fillPercent = Math.max(currentFill, futureFill) / 100.0;
        double looseYards = bin.getCapacityYards() * fillPercent;
        return looseYards / 4.0; // 4:1 compaction ratio
    }

    // ==========================================
    // ROUTE LIFECYCLE: End-of-day, completion, skips
    // (Moved from SmartRoutingService — single owner)
    // ==========================================

    @Override
    public void processEndOfDay(EndOfDayRequestDTO shiftReport) {
        for (RouteDTO route : shiftReport.getCompletedRoutes()) {
            Truck truck = truckRepository.findByTruckId(route.getTruckId())
                    .orElseThrow(() -> new RuntimeException("Truck not found: " + route.getTruckId()));
            truck.setCurrentCompactedYards(route.getEndingTruckVolumeYards());
            truckRepository.save(truck);
        }

        if (shiftReport.getSkippedBinIds() != null) {
            for (String skippedId : shiftReport.getSkippedBinIds()) {
                incrementBinOverdue(skippedId);
            }
        }
        System.out.println("✅ End of Day processing complete. Database ready for tomorrow.");
    }

    @Override
    public void processSingleRouteCompletion(SingleRouteCompletionDTO request) {
        RouteDTO route = request.getCompletedRoute();

        Truck truck = truckRepository.findByTruckId(route.getTruckId())
                .orElseThrow(() -> new RuntimeException("Truck not found: " + route.getTruckId()));
        truck.setCurrentCompactedYards(route.getEndingTruckVolumeYards());
        truckRepository.save(truck);

        if (request.getSkippedBinIds() != null) {
            for (String skippedId : request.getSkippedBinIds()) {
                incrementBinOverdue(skippedId);
            }
        }
        System.out.println("✅ Route closed for Driver: " + route.getDriverId());
    }

    @Override
    public void processRealTimeBinSkip(String binId) {
        Bin bin = binRepository.findByBinId(binId)
                .orElseThrow(() -> new IllegalArgumentException("Bin ID " + binId + " not found."));

        Integer currentOverdue = bin.getDaysOverdue();
        bin.setDaysOverdue((currentOverdue != null ? currentOverdue : 0) + 1);
        binRepository.save(bin);
        System.out.println("⚠️ Real-Time Skip: Bin " + binId + " penalty increased.");
    }

    /** Shared helper — increments daysOverdue for a bin */
    private void incrementBinOverdue(String binId) {
        binRepository.findByBinId(binId).ifPresent(bin -> {
            bin.setDaysOverdue((bin.getDaysOverdue() != null ? bin.getDaysOverdue() : 0) + 1);
            binRepository.save(bin);
        });
    }

    // ==========================================
    // SEARCH: Dynamic query from request body
    // ==========================================

    @Override
    public List<Route> searchRoutes(RouteSearchRequest request) {
        Query query = new Query();

        if (request.getRouteDate() != null && !request.getRouteDate().isBlank()) {
            query.addCriteria(Criteria.where("routeDate").is(LocalDate.parse(request.getRouteDate())));
        }
        if (request.getDriverId() != null && !request.getDriverId().isBlank()) {
            query.addCriteria(Criteria.where("driverId").is(request.getDriverId()));
        }
        if (request.getTruckId() != null && !request.getTruckId().isBlank()) {
            query.addCriteria(Criteria.where("truckId").is(request.getTruckId()));
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            query.addCriteria(Criteria.where("status").is(request.getStatus()));
        }

        return mongoTemplate.find(query, Route.class);
    }

    // ==========================================
    // CRUD
    // ==========================================

    @Override
    public Route getRouteById(String id) {
        return routeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Route not found"));
    }

    @Override
    public Route completeRoute(String id) {
        Route route = getRouteById(id);
        route.setStatus("COMPLETED");
        return routeRepository.save(route);
    }

    @Override
    public List<Route> getRoutesByDriver(String driverId) {
        return routeRepository.findByDriverIdAndStatus(driverId, "CREATED");
    }

    @Override
    public List<Route> getRoutesByStatus(String status) {
        return routeRepository.findByStatus(status);
    }

    @Override
    public List<Route> getAllRoutes() {
        return routeRepository.findAll();
    }

    // ==========================================
    // DASHBOARD
    // ==========================================

    @Override
    public DashboardDTO getDashboardSummary() {
        DashboardDTO dto = new DashboardDTO();

        // Fleet
        var trucks = truckRepository.findAll();
        dto.setTotalTrucks(trucks.size());
        dto.setTrucksWithLoad(trucks.stream()
                .filter(t -> t.getCurrentCompactedYards() != null && t.getCurrentCompactedYards() > 0)
                .count());

        // Bins
        var bins = binRepository.findAll();
        dto.setTotalBins(bins.size());
        dto.setBinsNeedingPickup(bins.stream()
                .filter(b -> b.getFillLevel() != null && b.getFillLevel() >= physicalThreshold)
                .count());
        dto.setBinsCritical(bins.stream()
                .filter(b -> b.getFillLevel() != null && b.getFillLevel() >= 90.0)
                .count());
        dto.setBinsOverdue(bins.stream()
                .filter(b -> b.getDaysOverdue() != null && b.getDaysOverdue() > 0)
                .count());
        dto.setBinsFlagged(bins.stream()
                .filter(b -> b.isFlagged())
                .count());

        // Predictions — stale if lastPredicted is null or older than 2 hours
        java.time.Instant twoHoursAgo = java.time.Instant.now().minus(java.time.Duration.ofHours(2));
        dto.setBinsWithStalePredictions(bins.stream()
                .filter(b -> b.getLastPredicted() == null || b.getLastPredicted().isBefore(twoHoursAgo))
                .count());

        // Routes
        var routes = routeRepository.findAll();
        dto.setActiveRoutes(routes.stream()
                .filter(r -> "CREATED".equals(r.getStatus()) || "IN_PROGRESS".equals(r.getStatus()))
                .count());
        dto.setCompletedRoutesToday(routes.stream()
                .filter(r -> "COMPLETED".equals(r.getStatus())
                        && r.getCreatedAt() != null
                        && r.getCreatedAt().toLocalDate().equals(java.time.LocalDate.now()))
                .count());

        return dto;
    }
}
