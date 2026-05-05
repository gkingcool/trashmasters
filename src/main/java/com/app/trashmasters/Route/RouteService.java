package com.app.trashmasters.route;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface RouteService {

    // Core route generation
    GenerateRoutesResponse generateRoutes(int numberOfTrucks, LocalDate routeDate, LocalTime startTime, String strategy);

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

    // Dashboard
    DashboardDTO getDashboardSummary();
}