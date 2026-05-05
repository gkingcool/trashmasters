package com.app.trashmasters.routing;


import com.app.trashmasters.route.RouteDTO;
import com.app.trashmasters.route.RouteStepDTO;
import com.app.trashmasters.Truck.Truck;
import com.app.trashmasters.bin.model.Bin;
import com.app.trashmasters.bin.model.Location;
import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure VRP solver — takes a time matrix, bins, and trucks and returns optimized routes.
 * Has NO business logic, NO database access. That belongs in RouteServiceImpl.
 */
@Service
public class SmartRoutingService {

    static {
        Loader.loadNativeLibraries();
    }

    /**
     * Solves the Vehicle Routing Problem using Google OR-Tools.
     *
     * Node model (SIMPLIFIED — dump is handled by post-processing):
     *   Node 0 = Station A (start/end)
     *   Node 1+ = target bins (index = i+1 for targetBins[i])
     */
    public List<RouteDTO> generateRoutes(
            long[][] timeMatrix,
            List<Bin> targetBins,
            List<Truck> trucks,
            Location stationA) {

        int vehicleNumber = trucks.size();

        System.out.println("⚙️ Initializing OR-Tools VRP Solver...");
        try {
            // All vehicles start and end at Station (node 0)
            int[] starts = new int[vehicleNumber];
            int[] ends = new int[vehicleNumber];
            for (int i = 0; i < vehicleNumber; i++) {
                starts[i] = 0;
                ends[i] = 0;
            }

            RoutingIndexManager manager = new RoutingIndexManager(
                    timeMatrix.length, vehicleNumber, starts, ends);
            RoutingModel routing = new RoutingModel(manager);

            // Time/Distance Callback (+5 min service time per bin)
            final int transitCallbackIndex = routing.registerTransitCallback(
                    (long fromIndex, long toIndex) -> {
                        int fromNode = manager.indexToNode(fromIndex);
                        int toNode = manager.indexToNode(toIndex);
                        long serviceTime = (fromNode >= 1) ? 5 : 0; // bins are node 1+
                        return timeMatrix[fromNode][toNode] + serviceTime;
                    });
            routing.setArcCostEvaluatorOfAllVehicles(transitCallbackIndex);

            // 8-Hour Shift Constraint
            routing.addDimension(transitCallbackIndex,
                    0, 480, true, "Time");

            // Capacity Callback (compacted yards per bin)
            final int demandCallbackIndex = routing.registerUnaryTransitCallback(
                    (long fromIndex) -> {
                        int fromNode = manager.indexToNode(fromIndex);
                        if (fromNode == 0) return 0; // station

                        Bin bin = targetBins.get(fromNode - 1);
                        double currentFill = (bin.getFillLevel() != null) ? bin.getFillLevel() : 0.0;
                        double futureFill = (bin.getFuturePredictions() != null)
                                ? bin.getFuturePredictions().getOrDefault(8, 0.0) : 0.0;
                        double fillPercent = Math.max(currentFill, futureFill) / 100.0;

                        Integer capacity = bin.getCapacityYards();
                        if (capacity == null) {
                            throw new IllegalArgumentException(
                                    "Bin " + bin.getBinId() + " is missing capacityYards.");
                        }
                        return Math.round((capacity * fillPercent) / 4.0);
                    });

// Vehicle capacities and starting loads
// NOTE: Dump trips are handled by post-processing in RouteServiceImpl.insertDumpTripsIfNeeded.
// The solver itself must NOT be limited to a single truck load — give it enough headroom
// to represent many dump cycles within the 8-hour shift (e.g. ~10 × 30 yd = 300 yd).
// Without this, the solver hits capacity after one truckload and stops assigning bins,
// leaving the rest of the shift empty and bins as urgentUnassigned.
long[] vehicleCapacities = new long[vehicleNumber];
long[] vehicleStartingLoads = new long[vehicleNumber];
for (int i = 0; i < vehicleNumber; i++) {
    vehicleCapacities[i] = 300; // 10 dump-trip cycles worth; real capacity enforced in post-processing
    Double truckYards = trucks.get(i).getCurrentCompactedYards();
                vehicleStartingLoads[i] = truckYards != null ? Math.round(truckYards) : 0L;
            }

            routing.addDimensionWithVehicleCapacity(
                    demandCallbackIndex, 0, vehicleCapacities, false, "Capacity");

            RoutingDimension capacityDimension = routing.getMutableDimension("Capacity");
            for (int i = 0; i < vehicleNumber; ++i) {
                long index = routing.start(i);
                capacityDimension.cumulVar(index).setValue(vehicleStartingLoads[i]);
            }

            // Disjunctions: mandatory bins have NO disjunction (must be visited).
            // Only optional bins get a disjunction with fill-level-based penalty.
            for (int i = 1; i < timeMatrix.length; ++i) {
                Bin bin = targetBins.get(i - 1);
                if (isMandatory(bin)) {
                    System.out.println("   🔒 MANDATORY: " + bin.getBinId()
                            + " | fill=" + bin.getFillLevel() + "% | overdue=" + bin.getDaysOverdue());
                } else {
                    long penalty = calculateDropPenalty(bin);
                    routing.addDisjunction(new long[]{manager.nodeToIndex(i)}, penalty);
                }
            }

            // Solve
            RoutingSearchParameters searchParameters = main.defaultRoutingSearchParameters()
                    .toBuilder()
                    .setFirstSolutionStrategy(FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC)
                    .setLocalSearchMetaheuristic(LocalSearchMetaheuristic.Value.GUIDED_LOCAL_SEARCH)
                    .setTimeLimit(com.google.protobuf.Duration.newBuilder().setSeconds(5).build())
                    .build();

            Assignment solution = routing.solveWithParameters(searchParameters);

            // If no feasible solution with mandatory constraints, fall back to
            // all-droppable mode with very high penalties for priority bins
            if (solution == null) {
                System.out.println("⚠️ Solver could not fit all mandatory bins. Falling back to priority-penalty mode...");

                // Rebuild the model with all bins droppable
                RoutingModel fallbackRouting = new RoutingModel(manager);
                fallbackRouting.setArcCostEvaluatorOfAllVehicles(
                        fallbackRouting.registerTransitCallback(
                                (long fromIndex, long toIndex) -> {
                                    int fromNode = manager.indexToNode(fromIndex);
                                    int toNode = manager.indexToNode(toIndex);
                                    long serviceTime = (fromNode >= 1) ? 5 : 0;
                                    return timeMatrix[fromNode][toNode] + serviceTime;
                                }));

                int fallbackDemand = fallbackRouting.registerUnaryTransitCallback(
                        (long fromIndex) -> {
                            int fromNode = manager.indexToNode(fromIndex);
                            if (fromNode == 0) return 0;
                            Bin bin = targetBins.get(fromNode - 1);
                            double currentFill = (bin.getFillLevel() != null) ? bin.getFillLevel() : 0.0;
                            double futureFill = (bin.getFuturePredictions() != null)
                                    ? bin.getFuturePredictions().getOrDefault(8, 0.0) : 0.0;
                            double fillPercent = Math.max(currentFill, futureFill) / 100.0;
                            Integer cap = bin.getCapacityYards();
                            return (cap != null) ? Math.round((cap * fillPercent) / 4.0) : 0;
                        });

                fallbackRouting.addDimension(
                        fallbackRouting.registerTransitCallback(
                                (long fromIndex, long toIndex) -> {
                                    int fromNode = manager.indexToNode(fromIndex);
                                    int toNode = manager.indexToNode(toIndex);
                                    long serviceTime = (fromNode >= 1) ? 5 : 0;
                                    return timeMatrix[fromNode][toNode] + serviceTime;
                                }),
                        0, 480, true, "Time");

                // Use the same expanded capacities (dump trips handled in post-processing)
                fallbackRouting.addDimensionWithVehicleCapacity(
                        fallbackDemand, 0, vehicleCapacities, false, "Capacity");

                RoutingDimension fbCapDim = fallbackRouting.getMutableDimension("Capacity");
                for (int i = 0; i < vehicleNumber; ++i) {
                    long idx = fallbackRouting.start(i);
                    fbCapDim.cumulVar(idx).setValue(vehicleStartingLoads[i]);
                }

                // All bins droppable, but mandatory bins get massive penalties
                for (int i = 1; i < timeMatrix.length; ++i) {
                    Bin bin = targetBins.get(i - 1);
                    long penalty;
                    if (isMandatory(bin)) {
                        // Overdue gets highest, then ≥80% fill
                        int overdue = bin.getDaysOverdue() != null ? bin.getDaysOverdue() : 0;
                        penalty = 100_000_000L + (overdue * 50_000_000L);
                        double fill = bin.getFillLevel() != null ? bin.getFillLevel() : 0.0;
                        penalty += (long) (fill * 500_000L);
                    } else {
                        penalty = calculateDropPenalty(bin);
                    }
                    fallbackRouting.addDisjunction(new long[]{manager.nodeToIndex(i)}, penalty);
                }

                solution = fallbackRouting.solveWithParameters(searchParameters);

                if (solution == null) {
                    throw new RuntimeException("Solver could not find any valid route. Try adding more trucks.");
                }

                return extractRoutes(manager, fallbackRouting, solution, vehicleNumber,
                        targetBins, trucks, stationA);
            }

            return extractRoutes(manager, routing, solution, vehicleNumber,
                    targetBins, trucks, stationA);

        } catch (IllegalArgumentException | NullPointerException e) {
            System.err.println("🚨 DATA ERROR: " + e.getMessage());
            throw new RuntimeException("Data integrity failure in routing engine: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            System.err.println("🚨 SOLVER ERROR: " + e.getMessage());
            throw new RuntimeException("OR-Tools Solver Crash: " + e.getMessage(), e);
        } catch (Exception e) {
            System.err.println("🚨 UNKNOWN ERROR: " + e.getMessage());
            throw new RuntimeException("Unexpected error in SmartRoutingService: " + e.getMessage(), e);
        }
    }

    /**
     * Determines if a bin is mandatory (must be on a route, no exceptions).
     *  - Overdue bins (skipped from previous day)
     *  - Bins ≥ 80% full (current fill or any prediction)
     */
    private boolean isMandatory(Bin bin) {
        int overdue = (bin.getDaysOverdue() != null) ? bin.getDaysOverdue() : 0;
        if (overdue > 0) return true;

        double fill = (bin.getFillLevel() != null) ? bin.getFillLevel() : 0.0;
        if (fill >= 80.0) return true;

        double predictedFill = getMaxPrediction(bin);
        if (predictedFill >= 80.0) return true;

        return false;
    }

    /**
     * Calculates the OR-Tools drop penalty for OPTIONAL bins only.
     * Higher fill = higher penalty = dropped last.
     * Least-full bins get the lowest penalty and are dropped first.
     */
    private long calculateDropPenalty(Bin bin) {
        double fill = (bin.getFillLevel() != null) ? bin.getFillLevel() : 0.0;
        double predictedFill = getMaxPrediction(bin);
        double effectiveFill = Math.max(fill, predictedFill);

        // Base penalty + fill-level scaling (0-79% range)
        // e.g., 50% fill → 1000 + 250,000 = 251,000
        // e.g., 70% fill → 1000 + 350,000 = 351,000
        long penalty = 1000L + (long) (effectiveFill * 5_000L);

        // Zone bonus
        if (bin.getZone() != null) {
            switch (bin.getZone()) {
                case COMMERCIAL: penalty += 100_000L; break;
                case PUBLIC:     penalty += 50_000L; break;
            }
        }

        return penalty;
    }

    /** Returns the highest predicted fill % across all horizons (4h, 8h, 12h) */
    private double getMaxPrediction(Bin bin) {
        if (bin.getFuturePredictions() == null) return 0.0;
        return bin.getFuturePredictions().values().stream()
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .max().orElse(0.0);
    }

    /**
     * Extracts the optimized routes from the OR-Tools solution into RouteDTOs.
     * Skips vehicles with no bin assignments (empty routes).
     */
    private List<RouteDTO> extractRoutes(
            RoutingIndexManager manager,
            RoutingModel routing,
            Assignment solution,
            int vehicleNumber,
            List<Bin> targetBins,
            List<Truck> trucks,
            Location stationA) {

        List<RouteDTO> generatedRoutes = new ArrayList<>();
        RoutingDimension timeDimension = routing.getMutableDimension("Time");
        Set<String> assignedBinIds = new HashSet<>();

        for (int vehicleId = 0; vehicleId < vehicleNumber; ++vehicleId) {

            long index = routing.start(vehicleId);

            // First pass: check if this vehicle has any bin stops
            List<Integer> binNodes = new ArrayList<>();
            long tempIndex = index;
            while (!routing.isEnd(tempIndex)) {
                tempIndex = solution.value(routing.nextVar(tempIndex));
                int node = manager.indexToNode(tempIndex);
                if (node >= 1) binNodes.add(node); // node 1+ are bins
            }
            if (binNodes.isEmpty()) continue; // Skip empty routes

            RouteDTO routeDTO = new RouteDTO();
            routeDTO.setTruckId(trucks.get(vehicleId).getTruckId());
            routeDTO.setDriverId(trucks.get(vehicleId).getAssignedDriverId());

            List<RouteStepDTO> steps = new ArrayList<>();

            // START at station (load values will be set by post-processing)
            steps.add(new RouteStepDTO(
                    stationA.getLat(), stationA.getLon(), "STATION", null, "START", 0, 0));

            // Walk the route — all nodes are bins (node 1+ = targetBins[node-1])
            while (!routing.isEnd(index)) {
                index = solution.value(routing.nextVar(index));
                int nodeIndex = manager.indexToNode(index);
                long etaMinutes = solution.value(timeDimension.cumulVar(index));

                if (nodeIndex == 0) {
                    // Back at station (end)
                    steps.add(new RouteStepDTO(
                            stationA.getLat(), stationA.getLon(), "STATION", null, "END",
                            etaMinutes, 0));
                } else {
                    // Bin pickup
                    Bin bin = targetBins.get(nodeIndex - 1);
                    RouteStepDTO binStep = new RouteStepDTO(
                            bin.getLocation().getLat(), bin.getLocation().getLon(),
                            "BIN", bin.getBinId(), "PICKUP", etaMinutes, 0);
                    binStep.setDaysOverdue(bin.getDaysOverdue() != null ? bin.getDaysOverdue() : 0);
                    binStep.setBinFillLevel(bin.getFillLevel() != null ? bin.getFillLevel() : 0.0);
                    steps.add(binStep);
                    assignedBinIds.add(bin.getBinId());
                }
            }

            long totalShiftTime = solution.value(timeDimension.cumulVar(routing.end(vehicleId)));
            routeDTO.setTotalTimeMinutes(totalShiftTime);
            routeDTO.setSteps(steps);
            generatedRoutes.add(routeDTO);
        }

        List<Bin> skippedBins = targetBins.stream()
                .filter(b -> !assignedBinIds.contains(b.getBinId()))
                .collect(java.util.stream.Collectors.toList());

        System.out.println("🚛 Routes Generated: " + generatedRoutes.size());
        System.out.println("⚠️ Bins Skipped by solver: " + skippedBins.size());
        for (Bin b : skippedBins) {
            System.out.println("   ❌ DROPPED: " + b.getBinId()
                    + " | fill=" + b.getFillLevel() + "%"
                    + " | overdue=" + b.getDaysOverdue()
                    + " | capacity=" + b.getCapacityYards() + "yd");
        }

        return generatedRoutes;
    }
}



















