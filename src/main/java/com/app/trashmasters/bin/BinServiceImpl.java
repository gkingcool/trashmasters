package com.app.trashmasters.bin;

import com.app.trashmasters.bin.dto.BinCreateRequest;
import com.app.trashmasters.bin.model.Bin;
import com.app.trashmasters.bin.model.BinStatus;
import com.app.trashmasters.bin.model.BinZone;
import com.app.trashmasters.bin.model.Location;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;

@Service
public class BinServiceImpl implements BinService {
    private final BinRepository binRepository;

    @Autowired
    public BinServiceImpl(BinRepository binRepository) {
        this.binRepository = binRepository;
    }

    @Override
    public List<Bin> getAllBins() {
        return binRepository.findAll();
    }

    @Override
    public Bin createBin(BinCreateRequest request) {
        Bin bin = new Bin();
        bin.setBinId(request.getBinId());
        bin.setLocationName(request.getLocationName());

        bin.setLocation(request.getLocation());

        bin.setDepthCm(request.getDepthCm());
        bin.setFillLevel(request.getFillLevel());
        bin.setSensorId(request.getSensorId());
        bin.setLastUpdated(Instant.now());
        bin.setCapacityYards(request.getCapacityYards());

        // Zone: default to PUBLIC if not provided
        bin.setZone(request.getZone() != null ? request.getZone() : BinZone.PUBLIC);

        // Status: auto-calculate from fill level if not explicitly set
        if (request.getStatus() != null) {
            bin.setStatus(BinStatus.valueOf(request.getStatus()));
        } else {
            double fill = (request.getFillLevel() != null) ? request.getFillLevel() : 0.0;
            if (fill >= 90) bin.setStatus(BinStatus.CRITICAL);
            else if (fill >= 70) bin.setStatus(BinStatus.FULL);
            else bin.setStatus(BinStatus.NORMAL);
        }

        bin.setFlagged(false);
        bin.setDaysOverdue(0);

        return binRepository.save(bin);
    }

    @Override
    public Bin getBinByBinId(String id) {
        return binRepository.findByBinId(id)
                .orElseThrow(() -> new RuntimeException("Bin not found with id: " + id));
    }

    @Override
    public Bin getBinById(String id) {
        return binRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bin not found with id: " + id));
    }

    @Override
    public Bin setFlag(String binId, boolean isFlagged, String issue) {
        Bin bin = binRepository.findByBinId(binId)
                .orElseThrow(() -> new RuntimeException("Bin not found"));

        bin.setFlagged(isFlagged);

        if (isFlagged) {
            // If flagging: Save the issue and force status to MAINTENANCE
            bin.setIssue(issue != null ? issue : "Manually flagged by Admin");
            bin.setStatus(BinStatus.MAINTENANCE);
        } else {
            // If unflagging: Clear the issue and RECALCULATE the status
            bin.setIssue(null);

            double fill = (bin.getFillLevel() != null) ? bin.getFillLevel() : 0.0;
            if (fill >= 90) {
                bin.setStatus(BinStatus.CRITICAL);
            } else if (fill >= 70) {
                bin.setStatus(BinStatus.FULL);
            } else {
                bin.setStatus(BinStatus.NORMAL);
            }
        }

        bin.setLastUpdated(Instant.now());
        return binRepository.save(bin);
    }

    @Override
    public List<Bin> getFullBins(int threshold) {
        return binRepository.findByFillLevelGreaterThan(threshold);
    }

    @Override
    public Bin updateBin(String id, BinCreateRequest request) {
        Bin bin = getBinByBinId(id);
        bin.setLocationName(request.getLocationName());

        bin.setLocation(request.getLocation());

        bin.setDepthCm(request.getDepthCm());
        if (request.getFillLevel() != null) bin.setFillLevel(request.getFillLevel());
        bin.setSensorId(request.getSensorId());
        bin.setCapacityYards(request.getCapacityYards());
        bin.setLastUpdated(Instant.now());

        return binRepository.save(bin);
    }

    @Override
    public void deleteBin(String id) {
        Bin bin = getBinByBinId(id);
        binRepository.delete(bin);
    }
}