package com.gte619n.healthfitness.data.blood

import com.gte619n.healthfitness.data.blood.BloodMappers.toDomain
import com.gte619n.healthfitness.domain.blood.BloodMarker
import com.gte619n.healthfitness.domain.blood.BloodReading
import com.gte619n.healthfitness.domain.blood.BloodReadingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory cache + Retrofit pass-through. The `state` flow is the
 * single source of truth observed by both the dashboard panel and the
 * feature-blood module — `create` / `delete` optimistically update it
 * after the network call succeeds so the UI doesn't wait on a refresh.
 */
@Singleton
internal class BloodReadingRepositoryImpl @Inject constructor(
    private val api: BloodApi,
) : BloodReadingRepository {

    private val state = MutableStateFlow<List<BloodReading>>(emptyList())

    override fun observeReadings(): Flow<List<BloodReading>> = state.asStateFlow()

    override suspend fun refresh() {
        state.value = api.listReadings().map { it.toDomain() }
            .sortedByDescending { it.sampleDate }
    }

    override suspend fun create(
        marker: BloodMarker,
        value: Double,
        unit: String?,
        sampleDate: LocalDate,
        labSource: String?,
        notes: String?,
    ): BloodReading {
        val dto = CreateReadingRequestDto(
            marker = marker.name,
            value = value,
            unit = unit,
            sampleDate = sampleDate,
            labSource = labSource,
            notes = notes,
        )
        val created = api.createReading(dto).toDomain()
        state.update { current ->
            (current + created).sortedByDescending { it.sampleDate }
        }
        return created
    }

    override suspend fun delete(readingId: String) {
        api.deleteReading(readingId)
        state.update { current ->
            current.filterNot { it.readingId == readingId }
        }
    }
}
