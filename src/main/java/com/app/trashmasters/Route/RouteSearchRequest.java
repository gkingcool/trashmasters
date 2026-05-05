package com.app.trashmasters.route;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Search filters for routes — all fields are optional, only non-empty fields are used")
public class RouteSearchRequest {
    @Schema(example = "2026-04-19", description = "Route date (yyyy-MM-dd)")
    private String routeDate;

    @Schema(example = "DRV-003")
    private String driverId;

    @Schema(example = "TRK-002")
    private String truckId;

    @Schema(example = "CREATED", description = "CREATED, IN_PROGRESS, or COMPLETED")
    private String status;
}

