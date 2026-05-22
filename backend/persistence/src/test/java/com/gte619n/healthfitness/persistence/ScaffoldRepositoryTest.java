package com.gte619n.healthfitness.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gte619n.healthfitness.persistence.equipment.EquipmentRepository;
import com.gte619n.healthfitness.persistence.location.LocationRepository;
import com.gte619n.healthfitness.persistence.metric.DailyMetricRepository;
import com.gte619n.healthfitness.persistence.workout.WorkoutRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

// Marks the four scaffold repositories as deliberately empty until later
// IMPLs land their writes. Replace these asserts with real coverage when
// the throw lines come out.
class ScaffoldRepositoryTest {

    @Test
    void workoutRepositoryThrowsOnAllMethods() {
        WorkoutRepository repo = new WorkoutRepository();
        assertThatThrownBy(() -> repo.findById("u", "w")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> repo.findByUser("u")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> repo.save(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void dailyMetricRepositoryThrowsOnAllMethods() {
        DailyMetricRepository repo = new DailyMetricRepository();
        assertThatThrownBy(() -> repo.findByDate("u", LocalDate.now())).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> repo.findByDateRange("u", LocalDate.now(), LocalDate.now())).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> repo.save(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void locationRepositoryThrowsOnAllMethods() {
        LocationRepository repo = new LocationRepository();
        assertThatThrownBy(() -> repo.findById("u", "l")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> repo.findByUser("u")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> repo.save(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> repo.delete("u", "l")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void equipmentRepositoryThrowsOnAllMethods() {
        EquipmentRepository repo = new EquipmentRepository();
        assertThatThrownBy(() -> repo.findById("e")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> repo.findByIds(List.of("e"))).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> repo.findSystemCatalog()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> repo.findByOwner("u")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> repo.save(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> repo.delete("e")).isInstanceOf(UnsupportedOperationException.class);
    }
}
