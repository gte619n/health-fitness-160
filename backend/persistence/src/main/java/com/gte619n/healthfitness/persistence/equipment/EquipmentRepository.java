package com.gte619n.healthfitness.persistence.equipment;

import com.gte619n.healthfitness.core.equipment.Equipment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

// Scaffold. Persists to the top-level equipment/{equipmentId} collection.
// Equipment is shared across users (ownerId == null = system catalog;
// ownerId set = user-owned custom entry). Real reads and writes land with
// the equipment-management UI in a later IMPL.
@Repository
public class EquipmentRepository implements com.gte619n.healthfitness.core.equipment.EquipmentRepository {

    @Override
    public Optional<Equipment> findById(String equipmentId) {
        throw new UnsupportedOperationException("equipment reads land with a later IMPL");
    }

    @Override
    public List<Equipment> findByIds(Collection<String> equipmentIds) {
        throw new UnsupportedOperationException("equipment reads land with a later IMPL");
    }

    @Override
    public List<Equipment> findSystemCatalog() {
        throw new UnsupportedOperationException("equipment reads land with a later IMPL");
    }

    @Override
    public List<Equipment> findByOwner(String ownerId) {
        throw new UnsupportedOperationException("equipment reads land with a later IMPL");
    }

    @Override
    public void save(Equipment equipment) {
        throw new UnsupportedOperationException("equipment writes land with a later IMPL");
    }

    @Override
    public void delete(String equipmentId) {
        throw new UnsupportedOperationException("equipment writes land with a later IMPL");
    }
}
