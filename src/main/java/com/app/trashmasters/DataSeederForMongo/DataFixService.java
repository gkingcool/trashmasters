package com.app.trashmasters.DataSeederForMongo;

import com.app.trashmasters.ManageSensor.Sensor;
import com.app.trashmasters.ManageSensor.SensorRepository;
import com.app.trashmasters.ManageSensor.model.SensorStatus;
import com.app.trashmasters.bin.BinRepository;
import com.app.trashmasters.bin.model.Bin;
import com.app.trashmasters.bin.model.BinStatus;
import com.app.trashmasters.bin.model.BinZone;
import com.app.trashmasters.bin.model.Location;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * One-time migration service:
 *  - Reassigns coordinates for all bins (10 North Bellevue, 10 South Bellevue, rest Downtown)
 *  - Creates IOT-001 … IOT-NNN sensors and links each to a bin
 */
@Service
public class DataFixService {

    @Autowired private BinRepository binRepository;
    @Autowired private SensorRepository sensorRepository;

    @Autowired
    private com.app.trashmasters.Truck.TruckRepository truckRepository;

    // ── Bellevue coordinate regions ─────────────────────────────────────────
    // North Bellevue  (~47.640–47.655, -122.165 to -122.185)
    private static final double NORTH_LAT  = 47.648;
    private static final double NORTH_LON  = -122.175;

    // South Bellevue  (~47.565–47.580, -122.185 to -122.205)
    private static final double SOUTH_LAT  = 47.572;
    private static final double SOUTH_LON  = -122.195;

    // Downtown Bellevue (~47.610–47.618, -122.198 to -122.210)
    private static final double DOWNTOWN_LAT = 47.614;
    private static final double DOWNTOWN_LON = -122.204;

    // Small jitter radius in degrees (~200 m)
    private static final double JITTER = 0.002;

    public String fixData() {
        List<Bin> bins = binRepository.findAll();
        if (bins.isEmpty()) {
            return "No bins found — seed the database first.";
        }

        Random rng = new Random(42);

        // Delete existing sensors so we don't get duplicate-key errors on re-run
        sensorRepository.deleteAll();

        List<Sensor> sensors = new ArrayList<>();
        int total = bins.size();

        for (int i = 0; i < total; i++) {
            Bin bin = bins.get(i);

            // ── Assign location zone ────────────────────────────────────────
            double baseLat, baseLon;
            BinZone zone;
            if (i < 10) {
                baseLat = NORTH_LAT;
                baseLon = NORTH_LON;
                zone = BinZone.PUBLIC;
                bin.setLocationName("North Bellevue - Zone " + (i + 1));
            } else if (i < 20) {
                baseLat = SOUTH_LAT;
                baseLon = SOUTH_LON;
                zone = BinZone.PUBLIC;
                bin.setLocationName("South Bellevue - Zone " + (i - 9));
            } else {
                baseLat = DOWNTOWN_LAT;
                baseLon = DOWNTOWN_LON;
                zone = BinZone.PUBLIC;
                bin.setLocationName("Downtown Bellevue - Zone " + (i - 19));
            }

            // Add small random jitter so bins aren't stacked exactly on top of each other
            double lat = baseLat + (rng.nextDouble() * 2 - 1) * JITTER;
            double lon = baseLon + (rng.nextDouble() * 2 - 1) * JITTER;
            bin.setLocation(new Location(lat, lon));
            bin.setZone(zone);

            // ── Create matching sensor ───────────────────────────────────────
            String sensorId = String.format("IOT-%03d", i + 1);
            bin.setSensorId(sensorId);

            Sensor sensor = new Sensor(sensorId);
            sensor.setBinId(bin.getBinId());
            sensor.setStatus(SensorStatus.ACTIVE);
            sensor.setBatteryLevel(90 + rng.nextInt(11)); // 90–100
            sensor.setLastUpdated(Instant.now());
            sensors.add(sensor);
        }

        binRepository.saveAll(bins);
        sensorRepository.saveAll(sensors);

        return String.format(
                "Migration complete: updated %d bins (10 North Bellevue, 10 South Bellevue, %d Downtown) " +
                "and created %d sensors (IOT-001 … IOT-%03d).",
                total, total - 20, sensors.size(), sensors.size());
    }

    /**
     * Boosts fill levels across all bins so that almost all are ≥ 75%.
     * Distribution:
     *  - 70% of bins  → 75–100%  (high priority)
     *  - 20% of bins  → 85–100%  (critical / overdue candidates)
     *  - 10% of bins  → 30–74%   (a few lower ones to keep data realistic)
     * Status is updated automatically based on fill level.
     */
    public String boostFillLevels() {
        List<Bin> bins = binRepository.findAll();
        if (bins.isEmpty()) {
            return "No bins found.";
        }

        Random rng = new Random();
        int total = bins.size();
        int critical = 0, high = 0, normal = 0;

        for (int i = 0; i < total; i++) {
            Bin bin = bins.get(i);
            double fill;

            double roll = (double) i / total; // deterministic spread across the list
            if (roll < 0.20) {
                // 20% → critical range 88–100
                fill = 88 + rng.nextDouble() * 12;
            } else if (roll < 0.90) {
                // 70% → high range 75–87
                fill = 75 + rng.nextDouble() * 12;
            } else {
                // 10% → lower range 30–74  (keeps routing interesting)
                fill = 30 + rng.nextDouble() * 44;
            }

            fill = Math.min(100, Math.max(0, fill));
            fill = Math.round(fill * 10.0) / 10.0; // one decimal place

            bin.setFillLevel(fill);
            bin.setLastUpdated(Instant.now());

            // Sync status
            if (fill >= 90) {
                bin.setStatus(BinStatus.CRITICAL);
                critical++;
            } else if (fill >= 70) {
                bin.setStatus(BinStatus.FULL);
                high++;
            } else {
                bin.setStatus(BinStatus.NORMAL);
                normal++;
            }
        }

        binRepository.saveAll(bins);

        return String.format(
                "Fill levels updated for %d bins — CRITICAL (≥90%%): %d, FULL (70–89%%): %d, NORMAL (<70%%): %d.",
                total, critical, high, normal);
    }

    /**
     * One-time migration: updates lat/lon for BEL-BIN-001 through BEL-BIN-070
     * using the provided coordinate list (in order).
     */
    public String updateBinCoordinates() {
        double[][] coords = {
            {47.61498,  -122.177784},
            {47.614102, -122.176844},
            {47.614281, -122.178174},
            {47.613763, -122.179466},
            {47.615298, -122.179394},
            {47.614137, -122.177094},
            {47.618207, -122.172435},
            {47.618383, -122.173034},
            {47.618043, -122.170323},
            {47.618014, -122.169807},
            {47.617756, -122.177792},
            {47.618037, -122.178255},
            {47.616388, -122.182719},
            {47.616384, -122.180971},
            {47.613806, -122.183789},
            {47.614747, -122.188002},
            {47.618467, -122.18503},
            {47.620455, -122.184198},
            {47.6181,   -122.191721},
            {47.617018, -122.19215},
            {47.618714, -122.195754},
            {47.616055, -122.197404},
            {47.61337,  -122.199732},
            {47.613259, -122.202159},
            {47.609755, -122.200887},
            {47.610352, -122.191938},
            {47.605681, -122.166809},
            {47.603272, -122.171568},
            {47.603747, -122.171598},
            {47.598889, -122.188537},
            {47.597335, -122.188116},
            {47.595803, -122.191143},
            {47.596327, -122.197804},
            {47.593236, -122.201947},
            {47.589091, -122.199364},
            {47.589111, -122.197392},
            {47.584094, -122.149274},
            {47.58495,  -122.149754},
            {47.585987, -122.149842},
            {47.586088, -122.143789},
            {47.585729, -122.142391},
            {47.581778, -122.140135},
            {47.582229, -122.138798},
            {47.581577, -122.137998},
            {47.581354, -122.136451},
            {47.582022, -122.135335},
            {47.580568, -122.134531},
            {47.576324, -122.13779},
            {47.576904, -122.135966},
            {47.578016, -122.136986},
            {47.580089, -122.134050},
            {47.577166, -122.170123},
            {47.578116, -122.166902},
            {47.574831, -122.169735},
            {47.57458,  -122.169054},
            {47.57373,  -122.172727},
            {47.569321, -122.172408},
            {47.58419,  -122.175535},
            {47.585312, -122.176081},
            {47.584167, -122.179991},
            {47.586299, -122.180791},
            {47.581545, -122.178371},
            {47.597815, -122.153154},
            {47.597132, -122.152243},
            {47.597163, -122.148578},
            {47.600323, -122.153899},
            {47.601213, -122.154416},
            {47.605002, -122.150859},
            {47.60503,  -122.152023},
            {47.603861, -122.149340}
        };

        List<Bin> bins = binRepository.findAll();
        int updated = 0;
        int skipped = 0;

        for (int i = 0; i < coords.length; i++) {
            String binId = String.format("BEL-BIN-%03d", i + 1);
            final String targetId = binId;
            java.util.Optional<Bin> opt = bins.stream()
                    .filter(b -> b.getBinId().equals(targetId))
                    .findFirst();

            if (opt.isPresent()) {
                Bin bin = opt.get();
                bin.setLocation(new Location(coords[i][0], coords[i][1]));
                binRepository.save(bin);
                updated++;
            } else {
                System.out.println("⚠️  Bin not found, skipping: " + binId);
                skipped++;
            }
        }

        return String.format("Coordinates updated: %d bins updated, %d skipped (not found in DB).", updated, skipped);
    }

    /**
     * Resets currentCompactedYards to 0 for all trucks.
     * Use this at the start of a new day if trucks weren't cleared via end-of-day.
     */
    public String resetTruckLoads() {
        List<com.app.trashmasters.Truck.Truck> trucks = truckRepository.findAll();
        if (trucks.isEmpty()) return "No trucks found.";

        int reset = 0;
        for (com.app.trashmasters.Truck.Truck truck : trucks) {
            double prev = truck.getCurrentCompactedYards() != null ? truck.getCurrentCompactedYards() : 0.0;
            truck.setCurrentCompactedYards(0.0);
            truckRepository.save(truck);
            System.out.println("🔄 Reset truck " + truck.getTruckId() + " from " + prev + " → 0 yards");
            reset++;
        }
        return String.format("Reset %d trucks to 0 yards. Routes will now start with empty trucks.", reset);
    }
}

