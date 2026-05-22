package com.gte619n.healthfitness.persistence.bodycomposition;

import static org.assertj.core.api.Assertions.assertThat;

import com.gte619n.healthfitness.core.bodycomposition.BodyCompositionMeasurement;
import com.gte619n.healthfitness.core.bodycomposition.BodyCompositionMetric;
import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.FirestoreEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class BodyCompositionRepositoryIntegrationTest {

    @Container
    static FirestoreEmulatorContainer emulator = new FirestoreEmulatorContainer(
        DockerImageName.parse("gcr.io/google.com/cloudsdktool/cloud-sdk:emulators")
    );

    private static Firestore firestore;
    private static BodyCompositionRepository repo;

    @BeforeAll
    static void setUp() {
        firestore = FirestoreOptions.newBuilder()
            .setProjectId("health-fitness-test")
            .setEmulatorHost(emulator.getEmulatorEndpoint())
            .build()
            .getService();
        repo = new BodyCompositionRepository(firestore);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (firestore != null) firestore.close();
    }

    @BeforeEach
    void clear() throws Exception {
        // Wipe everything across all users.
        try {
            for (var userDoc : firestore.collection("users").listDocuments()) {
                for (var measurement : userDoc.collection("bodyComposition").listDocuments()) {
                    measurement.delete().get();
                }
                userDoc.delete().get();
            }
        } catch (NotFoundException ignored) {}
    }

    @Test
    void saveAndFindRoundTripsAllFourMetrics() {
        Instant t = Instant.parse("2026-05-20T07:45:00Z");
        repo.save(measurement("u1", "r-w", BodyCompositionMetric.WEIGHT_KG, 82.4, t));
        repo.save(measurement("u1", "r-bf", BodyCompositionMetric.BODY_FAT_PERCENT, 19.2, t));
        repo.save(measurement("u1", "r-lm", BodyCompositionMetric.LEAN_MASS_KG, 65.5, t));
        repo.save(measurement("u1", "r-bmi", BodyCompositionMetric.BMI, 24.3, t));

        assertThat(repo.findById("u1", "r-w")).isPresent()
            .get().satisfies(m -> assertThat(m.metric()).isEqualTo(BodyCompositionMetric.WEIGHT_KG));
        assertThat(repo.findByUser("u1")).hasSize(4);
    }

    @Test
    void findByUserAndRangeFiltersByMetricAndTime() {
        Instant t1 = Instant.parse("2026-05-01T08:00:00Z");
        Instant t2 = Instant.parse("2026-05-15T08:00:00Z");
        Instant t3 = Instant.parse("2026-05-30T08:00:00Z");

        repo.saveAll(List.of(
            measurement("u2", "r1", BodyCompositionMetric.WEIGHT_KG, 80, t1),
            measurement("u2", "r2", BodyCompositionMetric.WEIGHT_KG, 81, t2),
            measurement("u2", "r3", BodyCompositionMetric.WEIGHT_KG, 82, t3),
            measurement("u2", "r4", BodyCompositionMetric.BODY_FAT_PERCENT, 18.5, t2)
        ));

        List<BodyCompositionMeasurement> weightInMid = repo.findByUserAndRange(
            "u2",
            BodyCompositionMetric.WEIGHT_KG,
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-20T00:00:00Z"));

        assertThat(weightInMid).extracting(BodyCompositionMeasurement::recordId)
            .containsExactly("r2");
    }

    @Test
    void saveIsIdempotentByRecordId() {
        Instant t = Instant.parse("2026-05-20T07:45:00Z");
        repo.save(measurement("u3", "shared-id", BodyCompositionMetric.WEIGHT_KG, 80.0, t));
        repo.save(measurement("u3", "shared-id", BodyCompositionMetric.WEIGHT_KG, 81.2, t));

        assertThat(repo.findByUser("u3")).singleElement()
            .satisfies(m -> assertThat(m.value()).isEqualTo(81.2));
    }

    @Test
    void saveAllBatchesBeyondTheBatchLimit() {
        Instant t = Instant.parse("2026-05-20T07:45:00Z");
        // 600 records — exceeds the 500-doc batch limit so the impl chunks.
        List<BodyCompositionMeasurement> many = java.util.stream.IntStream.range(0, 600)
            .mapToObj(i -> measurement("u4", "r-" + i,
                BodyCompositionMetric.WEIGHT_KG, 70 + i * 0.01, t.plusSeconds(i)))
            .toList();
        repo.saveAll(many);
        assertThat(repo.findByUser("u4")).hasSize(500); // findByUser is capped at 500
    }

    @Test
    void deleteRemovesARecord() {
        Instant t = Instant.parse("2026-05-20T07:45:00Z");
        repo.save(measurement("u5", "to-delete", BodyCompositionMetric.WEIGHT_KG, 80, t));
        repo.delete("u5", "to-delete");
        assertThat(repo.findById("u5", "to-delete")).isEmpty();
    }

    @Test
    void deleteByUserMetricAndRangeRemovesMatchingRecords() {
        Instant t1 = Instant.parse("2026-05-01T08:00:00Z");
        Instant t2 = Instant.parse("2026-05-15T08:00:00Z");
        Instant t3 = Instant.parse("2026-05-30T08:00:00Z");

        repo.saveAll(List.of(
            measurement("u6", "r1", BodyCompositionMetric.WEIGHT_KG, 80, t1),
            measurement("u6", "r2", BodyCompositionMetric.WEIGHT_KG, 81, t2),
            measurement("u6", "r3", BodyCompositionMetric.WEIGHT_KG, 82, t3),
            measurement("u6", "r4", BodyCompositionMetric.BODY_FAT_PERCENT, 18.5, t2)
        ));

        repo.deleteByUserMetricAndRange(
            "u6",
            BodyCompositionMetric.WEIGHT_KG,
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-20T00:00:00Z"));

        // Only r2 (mid-range weight) deleted. r4 (body-fat in range) stays.
        // r1, r3 (weights outside range) stay.
        assertThat(repo.findByUser("u6")).hasSize(3);
        assertThat(repo.findById("u6", "r2")).isEmpty();
    }

    private static BodyCompositionMeasurement measurement(
        String userId, String recordId, BodyCompositionMetric metric,
        double value, Instant sampleTime
    ) {
        return new BodyCompositionMeasurement(
            userId, recordId, metric, value, sampleTime,
            "FITBIT", "AUTOMATIC", null, null);
    }
}
