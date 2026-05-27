package com.gte619n.healthfitness.domain.workouts

import java.time.Instant

/**
 * IMPL-AND-06: Mirrors the backend's `core.location.DayOfWeek` enum
 * exactly (uppercase Mon..Sun), which is also what the LocationResponse
 * map keys serialise as.
 *
 * Avoids importing `java.time.DayOfWeek` because the wire format
 * (3-letter uppercase) doesn't match Java's enum names and a custom
 * adapter would be more friction than benefit at this size.
 */
enum class DayOfWeek { MON, TUE, WED, THU, FRI, SAT, SUN }

/**
 * Single open-close window for one day of the week. The strings are
 * `HH:mm` (24-hour). Backend validates the format; the Android UI uses
 * Material3 `TimePicker` to produce the same shape.
 */
data class HoursSlot(val open: String, val close: String)

/**
 * Hardcoded list of 10 amenities. Mirrors `web/lib/types/gym.ts`
 * AMENITIES exactly. Stored on the wire as `List<String>` of the
 * lowercase [id]s. Labels are English-only this stage.
 */
enum class Amenity(val id: String, val label: String) {
    TWENTY_FOUR_HR("24hr", "24-Hour Access"),
    LOCKERS("lockers", "Lockers"),
    SHOWERS("showers", "Showers"),
    PARKING("parking", "Parking"),
    WIFI("wifi", "WiFi"),
    TOWELS("towels", "Towels"),
    SAUNA("sauna", "Sauna"),
    POOL("pool", "Pool"),
    CHILDCARE("childcare", "Childcare"),
    TRAINING("training", "Personal Training");

    companion object {
        fun fromId(id: String): Amenity? = entries.firstOrNull { it.id == id }
    }
}

/**
 * A user-owned gym / workout location.
 *
 *  - [equipmentIds] are catalog references; the gym detail screen
 *    resolves each to an [Equipment] via [EquipmentRepository.get].
 *  - [equipmentSpecs] holds per-location overrides (free-form map
 *    keyed by equipmentId). When absent the catalog's default specs
 *    apply.
 *  - [hours] is null when [is24Hours] is true.
 */
data class Location(
    val locationId: String,
    val name: String,
    val address: String?,
    val coverPhotoUrl: String?,
    val is24Hours: Boolean,
    val hours: Map<DayOfWeek, HoursSlot>?,
    val amenities: List<Amenity>,
    val equipmentIds: List<String>,
    val equipmentSpecs: Map<String, Map<String, Any?>>,
    val isDefault: Boolean,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
