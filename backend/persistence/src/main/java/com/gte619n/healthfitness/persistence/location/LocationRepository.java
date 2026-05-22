package com.gte619n.healthfitness.persistence.location;

import com.gte619n.healthfitness.core.location.Location;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

// Scaffold. Persists to users/{userId}/locations/{locationId}. Real reads
// and writes land with the location-management UI in a later IMPL.
@Repository
public class LocationRepository implements com.gte619n.healthfitness.core.location.LocationRepository {

    @Override
    public Optional<Location> findById(String userId, String locationId) {
        throw new UnsupportedOperationException("location reads land with a later IMPL");
    }

    @Override
    public List<Location> findByUser(String userId) {
        throw new UnsupportedOperationException("location reads land with a later IMPL");
    }

    @Override
    public void save(Location location) {
        throw new UnsupportedOperationException("location writes land with a later IMPL");
    }

    @Override
    public void delete(String userId, String locationId) {
        throw new UnsupportedOperationException("location writes land with a later IMPL");
    }
}
