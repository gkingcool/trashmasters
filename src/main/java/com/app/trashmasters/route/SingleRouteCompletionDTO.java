package com.app.trashmasters.route;


import lombok.Data;
import java.util.List;

@Data
public class SingleRouteCompletionDTO {
    // The specific route this driver just finished
    private RouteDTO completedRoute;

    // Any bins on THEIR route they couldn't get to (e.g., car blocking the dumpster)
    private List<String> skippedBinIds;
}