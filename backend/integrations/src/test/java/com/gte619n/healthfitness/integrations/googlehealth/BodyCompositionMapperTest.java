package com.gte619n.healthfitness.integrations.googlehealth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BodyCompositionMapperTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesWeightDataPoint() throws Exception {
        JsonNode json = mapper.readTree("""
            {
              "name": "users/2515055256096816351/dataTypes/weight/dataPoints/8896720705097069096",
              "dataSource": { "recordingMethod": "AUTOMATIC", "platform": "FITBIT" },
              "weight": {
                "kilograms": 82.4,
                "sampleTime": { "physicalTime": "2026-05-20T07:45:00Z" }
              },
              "updateTime": "2026-05-20T07:45:01Z"
            }
            """);

        GoogleHealthDataPoint point = BodyCompositionMapper.fromJson(json, GoogleHealthDataType.WEIGHT);

        assertThat(point.healthUserId()).isEqualTo("2515055256096816351");
        assertThat(point.recordId()).isEqualTo("8896720705097069096");
        assertThat(point.dataType()).isEqualTo(GoogleHealthDataType.WEIGHT);
        assertThat(point.value()).isEqualTo(82.4);
        assertThat(point.sampleTime()).isEqualTo(Instant.parse("2026-05-20T07:45:00Z"));
        assertThat(point.sourcePlatform()).isEqualTo("FITBIT");
        assertThat(point.recordingMethod()).isEqualTo("AUTOMATIC");
    }

    @Test
    void parsesBodyFatPercentage() throws Exception {
        JsonNode json = mapper.readTree("""
            {
              "name": "users/abc/dataTypes/body-fat/dataPoints/xyz",
              "dataSource": { "recordingMethod": "MANUAL", "platform": "WITHINGS" },
              "bodyFat": {
                "percentage": 18.7,
                "sampleTime": { "physicalTime": "2026-05-20T08:00:00Z" }
              }
            }
            """);

        GoogleHealthDataPoint point = BodyCompositionMapper.fromJson(json, GoogleHealthDataType.BODY_FAT);
        assertThat(point.value()).isEqualTo(18.7);
        assertThat(point.dataType()).isEqualTo(GoogleHealthDataType.BODY_FAT);
    }

    @Test
    void parsesLeanMassAndBmi() throws Exception {
        JsonNode leanJson = mapper.readTree("""
            { "name": "users/u/dataTypes/lean-mass/dataPoints/r",
              "dataSource": { "recordingMethod": "AUTOMATIC", "platform": "FITBIT" },
              "leanMass": { "kilograms": 65.1,
                "sampleTime": { "physicalTime": "2026-05-20T08:00:00Z" } } }
            """);
        assertThat(BodyCompositionMapper.fromJson(leanJson, GoogleHealthDataType.LEAN_MASS).value())
            .isEqualTo(65.1);

        JsonNode bmiJson = mapper.readTree("""
            { "name": "users/u/dataTypes/bmi/dataPoints/r2",
              "dataSource": { "recordingMethod": "AUTOMATIC", "platform": "FITBIT" },
              "bmi": { "value": 24.3,
                "sampleTime": { "physicalTime": "2026-05-20T08:00:00Z" } } }
            """);
        assertThat(BodyCompositionMapper.fromJson(bmiJson, GoogleHealthDataType.BMI).value())
            .isEqualTo(24.3);
    }

    @Test
    void fallsBackToUpdateTimeWhenSampleTimeMissing() throws Exception {
        JsonNode json = mapper.readTree("""
            { "name": "users/u/dataTypes/weight/dataPoints/r",
              "dataSource": { "platform": "FITBIT" },
              "weight": { "kilograms": 80 },
              "updateTime": "2026-05-20T07:45:01Z" }
            """);
        GoogleHealthDataPoint point = BodyCompositionMapper.fromJson(json, GoogleHealthDataType.WEIGHT);
        assertThat(point.sampleTime()).isEqualTo(Instant.parse("2026-05-20T07:45:01Z"));
    }

    @Test
    void resourceNameParserExtractsHealthUserIdAndRecordId() {
        String name = "users/12345/dataTypes/weight/dataPoints/abc";
        assertThat(BodyCompositionMapper.parseHealthUserId(name)).isEqualTo("12345");
        assertThat(BodyCompositionMapper.parseRecordId(name)).isEqualTo("abc");
    }

    @Test
    void resourceNameParserRejectsMalformed() {
        assertThatThrownBy(() -> BodyCompositionMapper.parseHealthUserId("garbage"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BodyCompositionMapper.parseRecordId("users/12345/wrong"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
