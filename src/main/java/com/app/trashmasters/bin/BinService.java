package com.app.trashmasters.bin;

import com.app.trashmasters.bin.dto.BinCreateRequest;
import com.app.trashmasters.bin.model.Bin;
import java.time.LocalDateTime;
import java.util.List;

public interface BinService {
    List<Bin> getAllBins();
    Bin createBin(BinCreateRequest request);
    Bin getBinByBinId(String id);
    Bin getBinById(String id);
    Bin setFlag(String binId, boolean isFlagged, String issueDescription);
    List<Bin> getFullBins(int threshold);
    Bin updateBin(String id, BinCreateRequest request);
    void deleteBin(String id);
}