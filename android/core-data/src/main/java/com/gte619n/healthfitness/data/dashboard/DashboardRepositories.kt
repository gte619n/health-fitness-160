package com.gte619n.healthfitness.data.dashboard

import com.gte619n.healthfitness.data.di.IoDispatcher
import com.gte619n.healthfitness.domain.dashboard.BloodMarkerSummary
import com.gte619n.healthfitness.domain.dashboard.BloodMarkerSummaryRepository
import com.gte619n.healthfitness.domain.dashboard.BodyCompositionRepository
import com.gte619n.healthfitness.domain.dashboard.TodaysDoseSummary
import com.gte619n.healthfitness.domain.dashboard.TodaysDosesRepository
import com.gte619n.healthfitness.domain.dashboard.WeightSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class BodyCompositionRepositoryImpl @Inject constructor(
    private val api: DashboardApi,
    @IoDispatcher private val io: CoroutineDispatcher,
) : BodyCompositionRepository {
    override suspend fun loadRecent(): WeightSummary? = withContext(io) {
        BodyCompositionMapper.toWeightSummary(api.bodyComposition())
    }
}

@Singleton
internal class BloodMarkerSummaryRepositoryImpl @Inject constructor(
    private val api: DashboardApi,
    @IoDispatcher private val io: CoroutineDispatcher,
) : BloodMarkerSummaryRepository {
    override suspend fun loadDashboardMarkers(): List<BloodMarkerSummary> = withContext(io) {
        BloodMarkerSummaryMapper.toDashboardMarkers(api.bloodReadings())
    }
}

@Singleton
internal class TodaysDosesRepositoryImpl @Inject constructor(
    private val api: DashboardApi,
    @IoDispatcher private val io: CoroutineDispatcher,
) : TodaysDosesRepository {
    override suspend fun loadToday(): List<TodaysDoseSummary> = withContext(io) {
        api.todaysDoses().map(TodaysDosesMapper::toDomain)
    }
}
