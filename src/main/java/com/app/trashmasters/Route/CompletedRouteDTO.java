package com.app.trashmasters.route;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * Minimal route summary submitted at end-of-day.
 * Only the fields the server needs to update truck and bin state.
 */
@Data
@Schema(description = "Summary of one completed route submitted at end of shift")
public class CompletedRouteDTO {

    @Schema(example = "TRK-001", description = "ID of the truck that ran this route", requiredMode = Schema.RequiredMode.REQUIRED)
    private String truckId;

    @Schema(example = "DRV-001", description = "ID of the driver who completed this route", requiredMode = Schema.RequiredMode.REQUIRED)
    private String driverId;

    @Schema(example = "5.2", description = "Compacted cubic yards remaining in the truck at end of route (0.0 if dumped)")
    private double endingTruckVolumeYards;

    @ArraySchema(schema = @Schema(example = "BEL-BIN-023"))
    @Schema(description = "Bin IDs that were successfully collected on this route — their fill level will be reset to 0")
    private List<String> collectedBinIds;
}

