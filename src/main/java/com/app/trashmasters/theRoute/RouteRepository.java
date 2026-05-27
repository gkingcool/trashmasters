package com.app.trashmasters.theRoute;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RouteRepository extends MongoRepository<Route, String> {
    List<Route> findByDriverIdAndStatus(String driverId, String status);
    List<Route> findByStatus(String status);
    List<Route> findByRouteDate(LocalDate routeDate);
    void deleteByRouteDate(LocalDate routeDate);
}