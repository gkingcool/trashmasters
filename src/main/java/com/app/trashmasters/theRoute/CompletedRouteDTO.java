package com.app.trashmasters.theRoute;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Minimal route summary submitted at end-of-day.
 * Bin fill levels are NOT reset here — use POST /api/routes/{routeId}/pickup/{binId}
 * in real-time during the route to confirm each individual pickup.
 */
@Data
@Schema(description = "Summary of one completed route submitted at end of shift")
public class CompletedRouteDTO {

    @Schema(example = "TRK-001", description = "ID of the truck that ran this route", requiredMode = Schema.RequiredMode.REQUIRED)
    private String truckId;

    @Schema(example = "DRV-001", description = "ID of the driver who completed this route", requiredMode = Schema.RequiredMode.REQUIRED)
    private String driverId;

    @Schema(example = "5.2", description = "Compacted cubic yards remaining in the truck at end of route (0.0 if dumped at transfer station)")
    private double endingTruckVolumeYards;
}

