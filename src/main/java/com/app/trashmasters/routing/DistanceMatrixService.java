package com.app.trashmasters.routing;

import com.app.trashmasters.bin.model.Location;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class DistanceMatrixService {

    @Value("${mapbox.api.token}")
    private String apiToken;

    private final RestTemplate restTemplate;

    // 12 Origins + 12 Destinations = 24 coordinates (safely under Mapbox's 25 limit)
    private static final int CHUNK_SIZE = 12;

    public DistanceMatrixService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Builds an NxN driving-time matrix (in minutes) for all locations.
     * Node 0 = stationA, Nodes 1+ = bins.
     * Dump is NOT included — dump trips are handled by post-processing.
     * Uses Mapbox Directions Matrix API with chunking for >25 locations.
     */
    public long[][] calculateTimeMatrix(Location stationA, List<Location> binLocations) {

        int totalNodes = binLocations.size() + 1; // station + bins (no dump)
        String[] allAddresses = new String[totalNodes];

        // Mapbox requires: longitude,latitude
        allAddresses[0] = stationA.getLon() + "," + stationA.getLat();

        for (int i = 0; i < binLocations.size(); i++) {
            allAddresses[i + 1] = binLocations.get(i).getLon() + "," + binLocations.get(i).getLat();
        }

        long[][] masterMatrix = new long[totalNodes][totalNodes];
        System.out.println("🗺️ Calling Mapbox API for " + totalNodes + " total locations...");

        try {
            for (int i = 0; i < totalNodes; i += CHUNK_SIZE) {
                int endI = Math.min(i + CHUNK_SIZE, totalNodes);

                for (int j = 0; j < totalNodes; j += CHUNK_SIZE) {
                    int endJ = Math.min(j + CHUNK_SIZE, totalNodes);

                    StringBuilder coordsStr = new StringBuilder();
                    StringBuilder sourcesStr = new StringBuilder();
                    StringBuilder destsStr = new StringBuilder();

                    int urlIndex = 0;

                    for (int o = i; o < endI; o++) {
                        coordsStr.append(allAddresses[o]).append(";");
                        sourcesStr.append(urlIndex).append(";");
                        urlIndex++;
                    }

                    for (int d = j; d < endJ; d++) {
                        coordsStr.append(allAddresses[d]).append(";");
                        destsStr.append(urlIndex).append(";");
                        urlIndex++;
                    }

                    String finalCoords = coordsStr.substring(0, coordsStr.length() - 1);
                    String finalSources = sourcesStr.substring(0, sourcesStr.length() - 1);
                    String finalDests = destsStr.substring(0, destsStr.length() - 1);

                    String url = String.format(
                            "https://api.mapbox.com/directions-matrix/v1/mapbox/driving/%s?sources=%s&destinations=%s&access_token=%s",
                            finalCoords, finalSources, finalDests, apiToken
                    );

                    MapboxMatrixResponse response = restTemplate.getForObject(url, MapboxMatrixResponse.class);

                    if (response == null || !"Ok".equals(response.getCode())) {
                        throw new RuntimeException("Mapbox API failed for a chunk.");
                    }

                    double[][] chunkDurations = response.getDurations();
                    for (int row = 0; row < chunkDurations.length; row++) {
                        for (int col = 0; col < chunkDurations[row].length; col++) {
                            masterMatrix[i + row][j + col] = Math.round(chunkDurations[row][col] / 60.0);
                        }
                    }

                    Thread.sleep(100); // Prevent rate limiting
                }
            }

            System.out.println("✅ Master Mapbox Matrix built successfully!");
            return masterMatrix;

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch Mapbox Matrix", e);
        }
    }
}


