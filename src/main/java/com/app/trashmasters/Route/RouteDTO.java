package com.app.trashmasters.route;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Data
public class RouteDTO {
    @Schema(example = "TRK-002")
    private String truckId;
    @Schema(example = "DRV-003")
    private String driverId;
    @Schema(example = "185")
    private long totalTimeMinutes;
    @Schema(example = "0.0", description = "Compacted yards already in the truck at shift start")
    private double startingTruckLoadYards;
    @Schema(example = "12.5", description = "Compacted yards in the truck after the last stop")
    private double endingTruckVolumeYards;
    private List<RouteStepDTO> steps;
}