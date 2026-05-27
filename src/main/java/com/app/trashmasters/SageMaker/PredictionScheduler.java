package com.app.trashmasters.SageMaker;

import com.app.trashmasters.Weather.WeatherService;
import com.app.trashmasters.bin.BinRepository;
import com.app.trashmasters.bin.model.Bin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PredictionScheduler {

    private final BinRepository binRepository;
    private final SageMakerService sageMakerService;
    private final WeatherService weatherService;

    public PredictionScheduler(BinRepository binRepository,
                               SageMakerService sageMakerService,
                               WeatherService weatherService) {
        this.binRepository = binRepository;
        this.sageMakerService = sageMakerService;
        this.weatherService = weatherService;
    }

    /**
     * Runs every hour on the hour. For each bin, calls SageMaker to predict
     * fill levels at 4h, 8h, and 12h ahead. Stores predictions in MongoDB.
     */
//    @Scheduled(cron = "0 0 * * * *")
    public void updateAllBinPredictions() {
        System.out.println("⏳ Starting hourly AI prediction batch job...");

        List<Bin> allBins = binRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        double currentTemp = weatherService.getCurrentTemperatureF();

        int successCount = 0;
        int skipCount = 0;

        for (Bin bin : allBins) {
            // Skip bins with no location or no sensor data
            if (bin.getLocation() == null || bin.getFillLevel() == null) {
                skipCount++;
                continue;
            }

            Map<Integer, Double> futurePredictions = new HashMap<>();
            int[] hoursToPredict = {4, 8, 12};

            double startLevel = bin.getFillLevel();
            double maxForecastSoFar = startLevel;

            for (int hours : hoursToPredict) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("hours_ahead", hours);
                payload.put("dayOfWeek", now.getDayOfWeek().getValue());
                payload.put("hourOfDay", now.getHour());
                payload.put("temperatureF", currentTemp);
                payload.put("lat", bin.getLocation().getLat());
                payload.put("lon", bin.getLocation().getLon());

                Double predictedAddition = sageMakerService.predictFutureFillLevel(payload);

                if (predictedAddition != null) {
                    // AI predicts total trash added from now until 'hours' ahead
                    double rawForecast = startLevel + Math.round(predictedAddition);

                    // Enforce monotonicity: forecast can't be lower than a shorter horizon
                    double finalForecast = Math.max(maxForecastSoFar, rawForecast);

                    // Cap at 100%
                    finalForecast = Math.min(100.0, finalForecast);

                    futurePredictions.put(hours, finalForecast);
                    maxForecastSoFar = finalForecast;
                }
            }

            bin.setFuturePredictions(futurePredictions);
            bin.setLastPredicted(Instant.now());
            binRepository.save(bin);
            successCount++;
        }

        System.out.println("🎉 Prediction batch complete! Updated: " + successCount
                + ", Skipped (no data): " + skipCount);
    }
}