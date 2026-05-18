package com.app.trashmasters.Route;

import com.app.trashmasters.bin.model.Location;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface RouteService {
    // Core route generation
    GenerateRoutesResponse generateRoutes(int numberOfTrucks, LocalDate routeDate,
                                          LocalTime startTime, String strategy, Location depot, int shiftDuration);

    // Route lifecycle
    void processEndOfDay(EndOfDayRequestDTO shiftReport);

    void processSingleRouteCompletion(SingleRouteCompletionDTO request);

    void processRealTimeBinSkip(String binId);

    // Search / CRUD
    List<Route> searchRoutes(RouteSearchRequest request);

    Route getRouteById(String id);

    Route completeRoute(String id);

    List<Route> getRoutesByDriver(String driverId);

    List<Route> getRoutesByStatus(String status);

    List<Route> getAllRoutes();

    // Add this method to the interface
    Route markBinAsCollected(String routeId, String binId);

    // Dashboard
    DashboardDTO getDashboardSummary();

    void deleteRoute(String routeId);

    void reportIssue(String routeId, String binId, String description);

    void skipBinOnRoute(String routeId, String binId, String reason);
}