package com.gte619n.healthfitness.data.workouts

import com.gte619n.healthfitness.data.workouts.WorkoutsMappers.specsFromMap
import com.gte619n.healthfitness.data.workouts.WorkoutsMappers.specsToMap
import com.gte619n.healthfitness.data.workouts.WorkoutsMappers.toDomain
import com.gte619n.healthfitness.domain.workouts.Amenity
import com.gte619n.healthfitness.domain.workouts.DayOfWeek
import com.gte619n.healthfitness.domain.workouts.EquipmentSpec
import com.gte619n.healthfitness.domain.workouts.SpecSchemaTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WorkoutsMappersTest {

    @Test
    fun `LocationDto with uppercase DayOfWeek keys maps to enum`() {
        val dto = baseLocationDto(
            hours = mapOf(
                "MON" to HoursSlotDto("06:00", "22:00"),
                "SAT" to HoursSlotDto("07:00", "20:00"),
            ),
            amenities = listOf("lockers", "showers"),
        )
        val location = dto.toDomain()
        assertEquals(
            mapOf(DayOfWeek.MON to com.gte619n.healthfitness.domain.workouts.HoursSlot("06:00", "22:00"),
                  DayOfWeek.SAT to com.gte619n.healthfitness.domain.workouts.HoursSlot("07:00", "20:00")),
            location.hours,
        )
        assertEquals(listOf(Amenity.LOCKERS, Amenity.SHOWERS), location.amenities)
    }

    @Test
    fun `unknown amenity ids drop without error`() {
        val dto = baseLocationDto(amenities = listOf("lockers", "future_perk", "showers"))
        assertEquals(listOf(Amenity.LOCKERS, Amenity.SHOWERS), dto.toDomain().amenities)
    }

    @Test
    fun `equipmentSpecs map passes through verbatim`() {
        val specs = mapOf(
            "eq_1" to mapOf("maxWeight" to 250.0),
            "eq_2" to mapOf("availablePlates" to listOf(2.5, 5.0)),
        )
        val location = baseLocationDto(equipmentSpecs = specs).toDomain()
        assertEquals(specs, location.equipmentSpecs)
    }

    @Test
    fun `selectorized specs round-trip through map`() {
        val original = EquipmentSpec.Selectorized(10.0, 200.0, 5.0)
        val asMap = specsToMap(original)
        val restored = specsFromMap(SpecSchemaTag.SELECTORIZED, asMap)
        assertEquals(original, restored)
    }

    @Test
    fun `plate-loaded specs round-trip through map`() {
        val original = EquipmentSpec.PlateLoaded(45.0, listOf(2.5, 5.0, 10.0, 25.0, 45.0))
        val asMap = specsToMap(original)
        val restored = specsFromMap(SpecSchemaTag.PLATE_LOADED, asMap)
        assertEquals(original, restored)
    }

    @Test
    fun `bodyweight has no fields`() {
        val asMap = specsToMap(EquipmentSpec.Bodyweight)
        assertTrue(asMap.isEmpty())
        assertEquals(EquipmentSpec.Bodyweight, specsFromMap(SpecSchemaTag.BODYWEIGHT, asMap))
    }

    @Test
    fun `cable specs round-trip`() {
        val original = EquipmentSpec.Cable(weightStack = 200.0, numStations = 2)
        val asMap = specsToMap(original)
        val restored = specsFromMap(SpecSchemaTag.CABLE, asMap)
        assertEquals(original, restored)
    }

    @Test
    fun `cardio specs round-trip`() {
        val original = EquipmentSpec.Cardio(resistanceLevels = 10, hasIncline = true)
        val asMap = specsToMap(original)
        val restored = specsFromMap(SpecSchemaTag.CARDIO, asMap)
        assertEquals(original, restored)
    }

    @Test
    fun `weight set with only weights round-trips`() {
        val original = EquipmentSpec.WeightSet(
            minWeight = null, maxWeight = null, increment = null,
            weights = listOf(5.0, 10.0, 15.0),
        )
        val asMap = specsToMap(original)
        val restored = specsFromMap(SpecSchemaTag.WEIGHT_SET, asMap)
        assertEquals(original, restored)
    }

    @Test
    fun `weight set with only range round-trips`() {
        val original = EquipmentSpec.WeightSet(
            minWeight = 5.0, maxWeight = 50.0, increment = 5.0, weights = null,
        )
        val asMap = specsToMap(original)
        val restored = specsFromMap(SpecSchemaTag.WEIGHT_SET, asMap)
        assertEquals(original, restored)
    }

    @Test
    fun `numeric fields tolerate Int wire input`() {
        // The reflective Moshi adapter decodes integer JSON literals as
        // Double, but the backend's Map values may arrive as Integer
        // when round-tripped through Jackson maps. The mapper must
        // accept both.
        val restored = specsFromMap(
            SpecSchemaTag.PLATE_LOADED,
            mapOf("barWeight" to 45, "availablePlates" to listOf(2, 5, 10)),
        )
        assertEquals(EquipmentSpec.PlateLoaded(45.0, listOf(2.0, 5.0, 10.0)), restored)
    }

    @Test
    fun `unknown spec schema falls back to bodyweight`() {
        // Catches both unknown-discriminator and missing-discriminator
        // paths — the mapper coerces invalid tags to BODYWEIGHT before
        // calling specsFromMap.
        val restored = specsFromMap(SpecSchemaTag.BODYWEIGHT, mapOf("anything" to "goes"))
        assertEquals(EquipmentSpec.Bodyweight, restored)
    }

    @Test
    fun `EquipmentDto maps to domain projecting specs by schema`() {
        val dto = EquipmentDto(
            equipmentId = "eq_1",
            name = "Olympic Barbell",
            category = "Free Weights",
            subcategory = "Barbells",
            specSchema = "PLATE_LOADED",
            specs = mapOf("barWeight" to 45.0, "availablePlates" to listOf(2.5, 5.0)),
            imageUrl = null,
            imageStatus = "GENERATED",
            ownerId = null,
            status = "ACTIVE",
            contributorId = "u_1",
            exerciseCount = null,
            createdAt = Instant.parse("2026-05-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-05-02T00:00:00Z"),
        )
        val eq = dto.toDomain()
        assertEquals(SpecSchemaTag.PLATE_LOADED, eq.specSchema)
        assertEquals(EquipmentSpec.PlateLoaded(45.0, listOf(2.5, 5.0)), eq.specs)
        assertNull(eq.ownerId)
        assertNull(eq.exerciseCount)
    }

    private fun baseLocationDto(
        hours: Map<String, HoursSlotDto>? = null,
        amenities: List<String>? = null,
        equipmentSpecs: Map<String, Map<String, Any?>>? = null,
    ) = LocationDto(
        locationId = "loc_1",
        name = "Home Gym",
        address = null,
        coverPhotoUrl = null,
        is24Hours = hours == null,
        hours = hours,
        amenities = amenities,
        equipmentIds = emptyList(),
        equipmentSpecs = equipmentSpecs,
        isDefault = false,
        isActive = true,
        createdAt = Instant.parse("2026-05-26T12:00:00Z"),
        updatedAt = Instant.parse("2026-05-26T12:00:00Z"),
    )
}
