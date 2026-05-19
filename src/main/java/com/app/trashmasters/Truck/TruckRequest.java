package com.app.trashmasters.Truck;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class TruckRequest {
    @Schema(example = "TRK-001")
    private String truckId;
    @Schema(example = "DRV-101")
    private String assignedDriverId;
    @Schema(example = "30.0", description = "Maximum compacted cubic yards this truck can hold (defaults to 30.0 if omitted)")
    private Double maxCapacityYards;
    @Schema(example = "0.0", description = "Current compacted trash in cubic yards")
    private Double currentCompactedYards;
}


