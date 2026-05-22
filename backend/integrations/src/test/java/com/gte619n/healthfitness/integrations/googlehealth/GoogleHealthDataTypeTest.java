package com.gte619n.healthfitness.integrations.googlehealth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gte619n.healthfitness.core.bodycomposition.BodyCompositionMetric;
import org.junit.jupiter.api.Test;

class GoogleHealthDataTypeTest {

    @Test
    void roundTripsAllMetrics() {
        for (BodyCompositionMetric m : BodyCompositionMetric.values()) {
            assertThat(GoogleHealthDataType.forMetric(m).toMetric()).isEqualTo(m);
        }
    }

    @Test
    void fromApiNameMatchesUrlSegmentAndFilterFieldName() {
        assertThat(GoogleHealthDataType.fromApiName("weight")).isEqualTo(GoogleHealthDataType.WEIGHT);
        assertThat(GoogleHealthDataType.fromApiName("body-fat")).isEqualTo(GoogleHealthDataType.BODY_FAT);
        assertThat(GoogleHealthDataType.fromApiName("body_fat")).isEqualTo(GoogleHealthDataType.BODY_FAT);
        assertThat(GoogleHealthDataType.fromApiName("lean-mass")).isEqualTo(GoogleHealthDataType.LEAN_MASS);
        assertThat(GoogleHealthDataType.fromApiName("lean_mass")).isEqualTo(GoogleHealthDataType.LEAN_MASS);
        assertThat(GoogleHealthDataType.fromApiName("bmi")).isEqualTo(GoogleHealthDataType.BMI);
    }

    @Test
    void fromApiNameRejectsUnknown() {
        assertThatThrownBy(() -> GoogleHealthDataType.fromApiName("garbage"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
