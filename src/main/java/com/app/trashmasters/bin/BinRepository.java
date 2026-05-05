package com.app.trashmasters.bin;


import com.app.trashmasters.bin.model.Bin;
import com.app.trashmasters.bin.model.BinZone;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BinRepository extends MongoRepository<Bin, String> {
    // We need this to find only the bins that NEED pickup (> 70% full)
    List<Bin> findByFillLevelGreaterThan(double level);

    Optional<Bin> findByBinId(String binId);

    List<Bin> findByZone(BinZone zone);

    List<Bin> findByIsFlaggedTrue();

    List<Bin> findByDaysOverdueGreaterThan(int days);


    // Spring Boot automatically translates this method name into a MongoDB query!
// It finds bins that are EITHER currently > 70% OR predicted to be > 90%
//    List<Bin> findByCurrentFillLevelGreaterThanEqualOrPredictedFillLevelGreaterThanEqual(
//            Double currentTarget,
//            Double predictedTarget
//    );
}