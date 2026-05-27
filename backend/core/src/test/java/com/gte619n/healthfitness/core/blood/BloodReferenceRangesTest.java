package com.gte619n.healthfitness.core.blood;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BloodReferenceRangesTest {

    @Test
    void everyMarkerHasAReferenceRange() {
        // Guards against adding a new BloodMarker enum value without
        // wiring its reference range — the visualization bar would
        // crash with IllegalArgumentException at runtime otherwise.
        for (BloodMarker marker : BloodMarker.values()) {
            assertThat(BloodReferenceRanges.RANGES)
                .as("range registered for %s", marker)
                .containsKey(marker);
        }
    }

    @Test
    void testosteroneRange_adultMale() {
        BloodReferenceRanges.Range range =
            BloodReferenceRanges.rangeFor(BloodMarker.TESTOSTERONE);

        assertThat(range.unit()).isEqualTo("ng/dL");
        assertThat(range.orientation())
            .isEqualTo(BloodReferenceRanges.Orientation.HIGHER_IS_BETTER);
        assertThat(range.goodThreshold()).isEqualTo(264.0);
        assertThat(range.displayMin()).isEqualTo(0.0);
        assertThat(range.displayMax()).isEqualTo(1000.0);
    }

}
