package com.gte619n.healthfitness.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.cloud.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FirestoreMapperTest {

    @Test
    void toInstantHandlesNull() {
        assertThat(FirestoreMapper.toInstant(null)).isNull();
    }

    @Test
    void toInstantUnwrapsFirestoreTimestamp() {
        Instant now = Instant.parse("2026-05-21T10:15:30Z");
        Timestamp ts = Timestamp.ofTimeSecondsAndNanos(now.getEpochSecond(), now.getNano());
        assertThat(FirestoreMapper.toInstant(ts)).isEqualTo(now);
    }

    @Test
    void toInstantRejectsUnexpectedTypes() {
        assertThatThrownBy(() -> FirestoreMapper.toInstant("not a timestamp"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Firestore Timestamp");
    }

    @Test
    void toDocumentIdProducesIsoDate() {
        assertThat(FirestoreMapper.toDocumentId(LocalDate.of(2026, 5, 21)))
            .isEqualTo("2026-05-21");
    }

    @Test
    void fromDocumentIdRoundTrips() {
        LocalDate date = LocalDate.of(2026, 5, 21);
        assertThat(FirestoreMapper.fromDocumentId(FirestoreMapper.toDocumentId(date)))
            .isEqualTo(date);
    }
}
