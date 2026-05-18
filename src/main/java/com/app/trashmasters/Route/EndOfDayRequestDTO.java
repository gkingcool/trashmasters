package com.app.trashmasters.Route;

import lombok.Data;
import java.util.List;

@Data
public class EndOfDayRequestDTO {
    // The routes that were actually completed today
    private List<RouteDTO> completedRoutes;

    // The bins the AI (or the driver) had to skip
    private List<String> skippedBinIds;
}

