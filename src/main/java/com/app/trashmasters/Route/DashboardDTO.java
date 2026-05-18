package com.app.trashmasters.Route;

import lombok.Data;

/**
 * Dashboard summary response — powers the main admin UI screen.
 * One API call gives the frontend everything it needs.
 */
@Data
public class DashboardDTO {

    // Fleet
    private long totalTrucks;
    private long trucksWithLoad;         // trucks that still have trash in them

    // Bins
    private long totalBins;
    private long binsNeedingPickup;      // fillLevel >= 50% or predicted full
    private long binsCritical;           // fillLevel >= 90%
    private long binsOverdue;            // skipped from previous days
    private long binsFlagged;            // maintenance/broken

    // Routes
    private long activeRoutes;           // status = CREATED or IN_PROGRESS
    private long completedRoutesToday;   // status = COMPLETED

    // Predictions
    private long binsWithStalePredictions; // lastPredicted > 2 hours ago or null
}

