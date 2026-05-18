package com.app.trashmasters.Route;

import com.app.trashmasters.notification.NotificationService;
import com.app.trashmasters.routing.DistanceMatrixService;
import com.app.trashmasters.routing.SmartRoutingService;
import com.app.trashmasters.Truck.Truck;
import com.app.trashmasters.Truck.TruckRepository;
import com.app.trashmasters.bin.BinRepository;
import com.app.trashmasters.bin.model.Bin;
import com.app.trashmasters.bin.model.BinStatus;
import com.app.trashmasters.bin.model.Location;
import com.app.trashmasters.notification.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
    private final NotificationService notificationService;


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
                            MongoTemplate mongoTemplate,
                            NotificationService notificationService) {
        this.routeRepository = routeRepository;
        this.binRepository = binRepository;
        this.truckRepository = truckRepository;
        this.distanceMatrixService = distanceMatrixService;
        this.smartRoutingService = smartRoutingService;
        this.mongoTemplate = mongoTemplate;
        this.notificationService = notificationService;
    }

    private Location getStation() { return new Location(stationLat, stationLon); }
    private Location getDump()    { return new Location(dumpLat, dumpLon); }

    @Override
    public GenerateRoutesResponse generateRoutes(int numberOfTrucks, LocalDate routeDate, LocalTime startTime,
                                                 String strategy, Location depot, int shiftDuration) {
        if (routeDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Route date " + routeDate + " is in the past. Cannot generate routes for past dates.");
        }

        LocalDateTime startDateTime = LocalDateTime.of(routeDate, startTime);

        List<Route> existing = routeRepository.findByRouteDate(routeDate);
        if (!existing.isEmpty()) {
            routeRepository.deleteByRouteDate(routeDate);
            System.out.println("🗑️ Deleted " + existing.size() + " existing routes for " + routeDate + " — replacing with new generation.");
        }

        List<Truck> allTrucks = truckRepository.findAll();
        if (allTrucks.isEmpty()) {
            throw new RuntimeException("No trucks found in the database.");
        }
        List<Truck> activeTrucks = allTrucks.stream()
                .limit(numberOfTrucks)
                .collect(Collectors.toList());

        if (activeTrucks.size() < numberOfTrucks) {
            System.out.println("⚠️ Requested " + numberOfTrucks + " trucks but only " + activeTrucks.size() + " available.");
        }

        List<Bin> targetBins = getTargetBins(startDateTime, strategy);

        // FIX: Return a graceful empty response instead of throwing a 500.
        // The frontend can display "No bins need pickup" without crashing.
        if (targetBins.isEmpty()) {
            System.out.println("ℹ️ No bins qualify for pickup — returning empty route plan.");
            GenerateRoutesResponse empty = new GenerateRoutesResponse();
            empty.setRoutes(List.of());
            empty.setUrgentUnassignedBins(List.of());
            return empty;
        }

        targetBins.sort((a, b) -> {
            int overdueA = a.getDaysOverdue() != null ? a.getDaysOverdue() : 0;
            int overdueB = b.getDaysOverdue() != null ? b.getDaysOverdue() : 0;

            if (overdueA > 0 && overdueB == 0) return -1;
            if (overdueA == 0 && overdueB > 0) return 1;
            if (overdueA != overdueB) return Integer.compare(overdueB, overdueA);

            double fillA = Math.max(
                    a.getFillLevel() != null ? a.getFillLevel() : 0.0,
                    getMaxPrediction(a));
            double fillB = Math.max(
                    b.getFillLevel() != null ? b.getFillLevel() : 0.0,
                    getMaxPrediction(b));
            return Double.compare(fillB, fillA);
        });

        System.out.println("🗑️ Target bins for pickup (sorted by priority): " + targetBins.size());
        for (Bin b : targetBins) {
            double maxPred = getMaxPrediction(b);
            System.out.println("   ✅ " + b.getBinId()
                    + " | fill=" + b.getFillLevel() + "% "
                    + " | predicted=" + Math.round(maxPred) + "% "
                    + " | overdue=" + b.getDaysOverdue()
                    + " | capacity=" + b.getCapacityYards() + "yd "
                    + " | zone=" + b.getZone());
        }

        List<Location> binLocations = targetBins.stream()
                .map(Bin::getLocation)
                .collect(Collectors.toList());

        long[][] timeMatrix = distanceMatrixService.calculateTimeMatrix(depot, binLocations);

        List<RouteDTO> solverRoutes = smartRoutingService.generateRoutes(
                timeMatrix, targetBins, activeTrucks, depot, shiftDuration);

        for (RouteDTO route : solverRoutes) {
            insertDumpTripsIfNeeded(route, targetBins, activeTrucks);
        }

        for (RouteDTO routeDTO : solverRoutes) {
            Route routeEntity = new Route();
            routeEntity.setDriverId(routeDTO.getDriverId());
            routeEntity.setTruckId(routeDTO.getTruckId());
            routeEntity.setRouteDate(routeDate);

            List<String> binIds = routeDTO.getSteps().stream()
                    .filter(s -> "BIN".equals(s.getType()))
                    .map(RouteStepDTO::getBinId)
                    .collect(Collectors.toList());
            routeEntity.setBinIds(binIds);

            routeEntity.setStatus("CREATED");
            routeEntity.setCreatedAt(startDateTime);

            long binStepsCount = routeDTO.getSteps().stream()
                    .filter(s -> "BIN".equals(s.getType()))
                    .count();
            routeEntity.setTotalStops((int) binStepsCount);

            routeEntity.setSteps(routeDTO.getSteps());
            routeEntity.setTotalTimeMinutes(routeDTO.getTotalTimeMinutes());
            routeEntity.setStartingTruckLoadYards(routeDTO.getStartingTruckLoadYards());
            routeEntity.setEndingTruckVolumeYards(routeDTO.getEndingTruckVolumeYards());

            routeRepository.save(routeEntity);
        }

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
                    if (overdue > 0) reason = "OVERDUE: " + overdue + " day(s) skipped ";
                    else if (fill >= 80.0 || maxPred >= 80.0) reason = "URGENT: ≥80% full ";
                    else reason = "WARNING: ≥75% full ";

                    return new UnassignedBinDTO(
                            bin.getBinId(), fill, maxPred, overdue,
                            bin.getCapacityYards() != null ? bin.getCapacityYards() : 0,
                            bin.getZone() != null ? bin.getZone().name() : "UNKNOWN ",
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

    private double getMaxPrediction(Bin bin) {
        if (bin.getFuturePredictions() == null) return 0.0;
        return bin.getFuturePredictions().values().stream()
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .max().orElse(0.0);
    }

    /**
     * Returns all bins that qualify for pickup today.
     *
     * A bin qualifies if it passes ALL of the pre-flight checks below AND meets
     * at least one of the pickup conditions (full / predicted full / overdue).
     *
     * PRE-FLIGHT CHECKS (any failure → bin is skipped with a log line):
     *   1. Not flagged for maintenance issues
     *   2. Status is not MAINTENANCE
     *   3. Has a GPS location
     *   4. Has a valid capacity (≥ 1 yard — no upper bound; large dumpsters are fine)
     *
     * PICKUP CONDITIONS (at least one must be true):
     *   - fillLevel ≥ physicalThreshold (default 50 %)
     *   - predicted fill ≥ predictedThreshold (default 80 %) within the horizon
     *   - daysOverdue > 0
     *
     * FIX applied here:
     *   The original code rejected any bin with capacityYards > 8, which silently
     *   excluded large commercial dumpsters (10–40 yd³) and any bin whose capacity
     *   was accidentally stored as 0 or 1 in the database.  The upper-bound check
     *   has been removed; only obviously bad values (null / < 1) are rejected.
     */
    private List<Bin> getTargetBins(LocalDateTime startDateTime, String strategy) {
        List<Bin> allBins = binRepository.findAll();

        int primaryHorizon = 8;
        int secondaryHorizon = 12;
        if (startDateTime.getHour() >= 12) {
            primaryHorizon = 4;
            secondaryHorizon = 8;
        }

        final int pH = primaryHorizon;
        final int sH = secondaryHorizon;
        final boolean usePredictions = "predictive".equalsIgnoreCase(strategy);

        System.out.println("📋 getTargetBins — total bins in DB: " + allBins.size()
                + " | strategy: " + strategy
                + " | AI predictions: " + usePredictions
                + " | horizons: " + pH + "h / " + sH + "h"
                + " | physicalThreshold: " + physicalThreshold + "%"
                + " | predictedThreshold: " + predictedThreshold + "%");

        List<Bin> result = new ArrayList<>();

        for (Bin bin : allBins) {
            String id = bin.getBinId();

            // --- Pre-flight checks ---
            if (bin.isFlagged()) {
                System.out.println("   ⏭️  SKIP " + id + ": flagged for maintenance");
                continue;
            }
            if (bin.getStatus() == BinStatus.MAINTENANCE) {
                System.out.println("   ⏭️  SKIP " + id + ": status = MAINTENANCE");
                continue;
            }
            if (bin.getLocation() == null) {
                System.out.println("   ⏭️  SKIP " + id + ": no GPS location");
                continue;
            }

            // FIX: removed the `> 8` upper-bound that was silently discarding
            //      large dumpsters and bins stored with capacity 0 in the DB.
            //      Only reject clearly invalid values (null or < 1 yard).
            if (bin.getCapacityYards() == null || bin.getCapacityYards() < 1) {
                System.out.println("   ⏭️  SKIP " + id + ": invalid capacity = " + bin.getCapacityYards());
                continue;
            }

            // --- Pickup conditions ---
            boolean isFull = bin.getFillLevel() != null
                    && bin.getFillLevel() >= physicalThreshold;

            boolean isPredictedFull = false;
            if (usePredictions && bin.getFuturePredictions() != null) {
                Double primary   = bin.getFuturePredictions().get(pH);
                Double secondary = bin.getFuturePredictions().get(sH);
                isPredictedFull = (primary   != null && primary   >= predictedThreshold)
                        || (secondary != null && secondary >= predictedThreshold);
            }

            boolean isOverdue = bin.getDaysOverdue() != null && bin.getDaysOverdue() > 0;

            if (isFull || isPredictedFull || isOverdue) {
                System.out.println("   ✅  INCLUDE " + id
                        + " | fill=" + bin.getFillLevel() + "%"
                        + " | predicted=" + Math.round(getMaxPrediction(bin)) + "%"
                        + " | overdue=" + bin.getDaysOverdue()
                        + " | capacity=" + bin.getCapacityYards() + "yd");
                result.add(bin);
            } else {
                System.out.println("   ⏭️  SKIP " + id
                        + ": fill=" + bin.getFillLevel() + "% (threshold " + physicalThreshold + "%)"
                        + ", predicted=" + Math.round(getMaxPrediction(bin)) + "% (threshold " + predictedThreshold + "%)"
                        + ", overdue=" + bin.getDaysOverdue());
            }
        }

        System.out.println("📦 Bins selected for routing: " + result.size() + " / " + allBins.size());
        return result;
    }

    private void insertDumpTripsIfNeeded(RouteDTO route, List<Bin> targetBins, List<Truck> trucks) {
        Truck truck = trucks.stream()
                .filter(t -> t.getTruckId().equals(route.getTruckId()))
                .findFirst().orElse(null);
        if (truck == null) return;

        double maxCapacity  = truck.getMaxCapacityYards();
        double dumpThreshold = maxCapacity * truckDumpPercent;
        double startingLoad  = truck.getCurrentCompactedYards() != null
                ? truck.getCurrentCompactedYards() : 0.0;
        double currentLoad   = startingLoad;

        route.setStartingTruckLoadYards(Math.round(startingLoad * 100.0) / 100.0);

        Location dump = getDump();
        List<RouteStepDTO> originalSteps = route.getSteps();
        List<RouteStepDTO> newSteps = new ArrayList<>();
        long etaOffset = 0;

        for (RouteStepDTO step : originalSteps) {
            step.setEstimatedArrivalMinutes(step.getEstimatedArrivalMinutes() + etaOffset);

            if ("BIN".equals(step.getType()) && step.getBinId() != null) {
                double binLoad = calculateBinCompactedLoad(step.getBinId(), targetBins);

                if (currentLoad + binLoad > dumpThreshold) {
                    long dumpEta = step.getEstimatedArrivalMinutes();
                    newSteps.add(new RouteStepDTO(
                            dump.getLat(), dump.getLon(),
                            "DUMP", null, "EMPTY_TRUCK",
                            dumpEta, 0.0));
                    currentLoad = 0;
                    etaOffset  += dumpTripMinutes;
                    step.setEstimatedArrivalMinutes(step.getEstimatedArrivalMinutes() + dumpTripMinutes);

                    System.out.println("🚛 Truck " + route.getTruckId()
                            + " → dump trip inserted before bin " + step.getBinId()
                            + " (+" + dumpTripMinutes + " min)");
                }

                currentLoad += binLoad;
                step.setCurrentTruckLoadYards(Math.round(currentLoad * 100.0) / 100.0);
            } else {
                step.setCurrentTruckLoadYards(Math.round(currentLoad * 100.0) / 100.0);
            }
            newSteps.add(step);
        }

        route.setSteps(newSteps);
        route.setEndingTruckVolumeYards(Math.round(currentLoad * 100.0) / 100.0);
        route.setTotalTimeMinutes(route.getTotalTimeMinutes() + etaOffset);
    }

    private double calculateBinCompactedLoad(String binId, List<Bin> targetBins) {
        Bin bin = targetBins.stream()
                .filter(b -> binId.equals(b.getBinId()))
                .findFirst().orElse(null);
        if (bin == null || bin.getCapacityYards() == null) return 0.0;

        double currentFill = bin.getFillLevel() != null ? bin.getFillLevel() : 0.0;
        double futureFill  = 0.0;
        if (bin.getFuturePredictions() != null) {
            Double pred = bin.getFuturePredictions().get(8);
            if (pred != null) futureFill = pred;
        }

        double fillPercent = Math.max(currentFill, futureFill) / 100.0;
        double looseYards  = bin.getCapacityYards() * fillPercent;
        return looseYards / 4.0;
    }

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

    private void incrementBinOverdue(String binId) {
        binRepository.findByBinId(binId).ifPresent(bin -> {
            bin.setDaysOverdue((bin.getDaysOverdue() != null ? bin.getDaysOverdue() : 0) + 1);
            binRepository.save(bin);
        });
    }

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
        Query query = new Query();
        query.addCriteria(Criteria.where("driverId").is(driverId)
                .andOperator(Criteria.where("status").in("CREATED", "IN_PROGRESS")));
        return mongoTemplate.find(query, Route.class);
    }

    @Override
    public List<Route> getRoutesByStatus(String status) {
        return routeRepository.findByStatus(status);
    }

    @Override
    public List<Route> getAllRoutes() {
        return routeRepository.findAll();
    }

    @Override
    public void reportIssue(String routeId, String binId, String description) {
        Bin bin = binRepository.findByBinId(binId)
                .orElseThrow(() -> new RuntimeException("Bin not found: " + binId));

        bin.setFlagged(true);
        bin.setIssue(description);
        binRepository.save(bin);
    }

    @Override
    public DashboardDTO getDashboardSummary() {
        DashboardDTO dto = new DashboardDTO();

        var trucks = truckRepository.findAll();
        dto.setTotalTrucks(trucks.size());
        dto.setTrucksWithLoad(trucks.stream()
                .filter(t -> t.getCurrentCompactedYards() != null && t.getCurrentCompactedYards() > 0)
                .count());

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

        java.time.Instant twoHoursAgo = java.time.Instant.now().minus(java.time.Duration.ofHours(2));
        dto.setBinsWithStalePredictions(bins.stream()
                .filter(b -> b.getLastPredicted() == null || b.getLastPredicted().isBefore(twoHoursAgo))
                .count());

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

    @Override
    public Route markBinAsCollected(String routeId, String binId) {
        Route route = getRouteById(routeId);

        Bin bin = binRepository.findById(binId).orElse(null);
        if (bin == null) {
            bin = binRepository.findByBinId(binId)
                    .orElseThrow(() -> new RuntimeException("Bin not found with ID: " + binId));
        }

        String businessId = bin.getBinId();

        if (!route.getBinIds().contains(businessId)) {
            throw new RuntimeException("Bin " + businessId + " is not in this route");
        }

        if (route.getCompletedBinIds() == null) {
            route.setCompletedBinIds(new ArrayList<>());
        }
        if (!route.getCompletedBinIds().contains(businessId)) {
            route.getCompletedBinIds().add(businessId);
        }

        route.setCurrentStopIndex(route.getCompletedBinIds().size());

        int totalStops     = route.getBinIds().size();
        int completedStops = route.getCompletedBinIds().size();

        if (completedStops >= totalStops) {
            route.setStatus("COMPLETED");
            route.setCompletedAt(LocalDateTime.now());
        } else {
            route.setStatus("IN_PROGRESS");
        }

        bin.setFillLevel(0.0);
        bin.setLastUpdated(Instant.now());
        binRepository.save(bin);

        return routeRepository.save(route);
    }

    @Override
    public void deleteRoute(String routeId) {
        if (!routeRepository.existsById(routeId)) {
            throw new RuntimeException("Route not found with ID: " + routeId);
        }
        routeRepository.deleteById(routeId);
    }

    @Override
    public void skipBinOnRoute(String routeId, String binId, String reason) {
        // 1. Find route
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new RuntimeException("Route not found: " + routeId));

        // 2. Find the bin step
        RouteStepDTO targetStep = route.getSteps().stream()
                .filter(step -> "BIN".equals(step.getType()) && binId.equals(step.getBinId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Bin " + binId + " not found in route " + routeId));

        // 3. Mark step as skipped
        targetStep.setAction("SKIPPED");
        routeRepository.save(route);

        // 4. Apply overdue penalty to bin
        incrementBinOverdue(binId);

        // 5. ✅ CREATE ADMIN NOTIFICATION
        String driverId = route.getDriverId();
        String driverName = route.getDriverName() != null ? route.getDriverName() : driverId;
        String routeLabel = route.getRouteNumber() != null ? route.getRouteNumber() : routeId;

        com.app.trashmasters.notification.model.Notification notif = new com.app.trashmasters.notification.model.Notification();
        notif.setTitle("⚠️ Bin Skipped by Driver");
        notif.setMessage(String.format("Driver %s skipped bin %s on Route %s. Reason: %s",
                driverName, binId, routeLabel, reason));
        notif.setDriverId("ADMIN"); // ✅ Routes exclusively to admin dashboard & ticket log
        notif.setType(com.app.trashmasters.notification.model.NotificationType.WARNING);
        notif.setRead(false);
        notif.setTimestamp(java.time.Instant.now());
        notif.setStatus("Under Review");
        notificationService.saveNotification(notif);

        System.out.println("⚠️ Route " + routeId + ": Bin " + binId + " skipped. Reason: " + reason);
    }
}