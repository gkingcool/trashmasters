package com.app.trashmasters.route;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Route generation result — includes optimized routes and any urgent bins that couldn't fit")
public class GenerateRoutesResponse {
    private List<RouteDTO> routes;

    @Schema(description = "Bins ≥75% full (or overdue) that the solver could NOT fit into any route. These need manual attention or more trucks.")
    private List<UnassignedBinDTO> urgentUnassignedBins;
}

