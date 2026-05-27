package com.gte619n.healthfitness.feature.blood.nav

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import com.gte619n.healthfitness.feature.blood.add.AddReadingScreen
import com.gte619n.healthfitness.feature.blood.detail.MarkerDetailScreen
import com.gte619n.healthfitness.feature.blood.overview.BloodOverviewScreen
import com.gte619n.healthfitness.feature.blood.report.ReportDetailScreen
import com.gte619n.healthfitness.feature.blood.upload.UploadLabReportScreen
import kotlinx.serialization.Serializable

/**
 * Type-safe blood-feature nav routes. Matches the medications module's
 * pattern: each route is a `@Serializable data object` (or data class
 * when it carries args) so `NavHost` round-trips it without a manual
 * `NavType` adapter.
 *
 * Plugged into the phone app's `AppNavHost` via [bloodGraph].
 */
@Serializable
data object BloodOverviewRoute

@Serializable
data class MarkerDetailRoute(val markerKey: String)

@Serializable
data class ReportDetailRoute(val reportId: String)

@Serializable
data object AddReadingRoute

@Serializable
data object UploadReportRoute

fun NavGraphBuilder.bloodGraph(
    onBack: () -> Unit,
    navigateToMarker: (markerKey: String) -> Unit,
    navigateToReport: (reportId: String) -> Unit,
    navigateToAddReading: () -> Unit,
    navigateToUpload: () -> Unit,
) {
    composable<BloodOverviewRoute> {
        BloodOverviewScreen(
            onMarkerClick = navigateToMarker,
            onReportClick = navigateToReport,
            onAddReading = navigateToAddReading,
            onUploadPdf = navigateToUpload,
        )
    }
    composable<MarkerDetailRoute> {
        MarkerDetailScreen(onBack = onBack)
    }
    composable<ReportDetailRoute> {
        ReportDetailScreen(onBack = onBack)
    }
    dialog<AddReadingRoute> {
        AddReadingScreen(onDone = onBack, onDismiss = onBack)
    }
    dialog<UploadReportRoute> {
        UploadLabReportScreen(
            onComplete = { reportId ->
                onBack()
                navigateToReport(reportId)
            },
            onDismiss = onBack,
        )
    }
}
