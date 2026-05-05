package com.app.trashmasters.route;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor // ✅ ADD THIS: Allows Spring to create an empty instance
@AllArgsConstructor // ✅ ADD THIS: Keeps your existing constructor usage
public class RouteStepDTO {
    @Schema(example = "47.6101")
    private double lat;
    @Schema(example = "-122.2015")
    private double lon;
    @Schema(example = "BIN", description = "STATION, DUMP, or BIN")
    private String type;
    @Schema(example = "BEL-BIN-023")
    private String binId;
    @Schema(example = "PICKUP", description = "START, PICKUP, EMPTY_TRUCK, or END")
    private String action;
    @Schema(example = "42")
    private long estimatedArrivalMinutes;
    @Schema(example = "8.5", description = "Compacted cubic yards in the truck after this step")
    private double currentTruckLoadYards;
    @Schema(example = "0", description = "Number of days this bin was skipped (0 = not overdue). Only applies to BIN steps.")
    private int daysOverdue;
    @Schema(example = "72.5", description = "Fill percent 0–100. Only applies to BIN steps.")
    private double binFillLevel;

    public RouteStepDTO(double lat, double lon, String type, String binId, String action, long eta, double truckLoad) {
        this.lat = lat;
        this.lon = lon;
        this.type = type;
        this.binId = binId;
        this.action = action;
        this.estimatedArrivalMinutes = eta;
        this.currentTruckLoadYards = truckLoad;
    }
}
