package com.gte619n.healthfitness.data.bodycomposition

import com.gte619n.healthfitness.data.bodycomposition.BodyCompositionMappers.buildSnapshot
import com.gte619n.healthfitness.data.bodycomposition.BodyCompositionMappers.toDomain
import com.gte619n.healthfitness.domain.bodycomposition.BodyCompositionMetric
import com.gte619n.healthfitness.domain.bodycomposition.BodyCompositionPoint
import com.gte619n.healthfitness.domain.bodycomposition.BodyCompositionRepository
import com.gte619n.healthfitness.domain.bodycomposition.BodyCompositionSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads `GET /api/me/body-composition`, builds a snapshot, and replays
 * it to observers. The hot replay-1 flow lets the dashboard hero and the
 * body-composition overview screen subscribe independently — both see
 * the latest snapshot the moment they collect.
 */
@Singleton
internal class BodyCompositionRepositoryImpl @Inject constructor(
    private val api: BodyCompositionApi,
) : BodyCompositionRepository {

    private val snapshot = MutableSharedFlow<BodyCompositionSnapshot>(replay = 1)

    override fun observeSnapshot(): Flow<BodyCompositionSnapshot> = snapshot.asSharedFlow()

    override suspend fun refresh() {
        val points = api.list().map { it.toDomain() }
        snapshot.emit(buildSnapshot(points))
    }

    override suspend fun pointsInRange(
        metric: BodyCompositionMetric,
        from: Instant,
        to: Instant,
    ): List<BodyCompositionPoint> =
        api.list(from = from.toString(), to = to.toString(), metric = metric.name)
            .map { it.toDomain() }
}
