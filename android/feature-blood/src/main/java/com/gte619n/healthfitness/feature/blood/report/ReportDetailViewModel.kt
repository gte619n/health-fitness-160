package com.gte619n.healthfitness.feature.blood.report

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.gte619n.healthfitness.domain.blood.BloodTestReport
import com.gte619n.healthfitness.domain.blood.BloodTestReportRepository
import com.gte619n.healthfitness.feature.blood.nav.ReportDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Drives the per-report detail screen.
 *
 * The "View PDF" affordance streams the binary down via
 * [BloodTestReportRepository.downloadPdf], writes it to `cacheDir/blood/`,
 * and returns an `Intent.ACTION_VIEW` the screen launches. We use the
 * existing `androidx.core.content.FileProvider` authority
 * `${packageName}.fileprovider`.
 */
@HiltViewModel
class ReportDetailViewModel @Inject constructor(
    private val reports: BloodTestReportRepository,
    @ApplicationContext private val context: Context,
    savedState: SavedStateHandle,
) : ViewModel() {

    val reportId: String = savedState.toRoute<ReportDetailRoute>().reportId

    private val pdfStatus = MutableStateFlow<PdfStatus>(PdfStatus.Idle)
    val pdfState: StateFlow<PdfStatus> = pdfStatus.asStateFlow()

    val state: StateFlow<UiState> = flow { emit(reports.get(reportId)) }
        .map { UiState.Ready(it) as UiState }
        .catch { emit(UiState.Error(it.localizedMessage ?: "Failed to load report")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            runCatching { reports.delete(reportId) }
                .onSuccess { onDeleted() }
                .onFailure { /* keep UI as-is; toast surfaces via snackbar */ }
        }
    }

    /**
     * Downloads the PDF (if not already cached) and emits an
     * `ACTION_VIEW` intent the screen can hand to `startActivity`.
     * Sets [PdfStatus] so the button can show a spinner.
     */
    fun openPdf(onIntent: (Intent) -> Unit) {
        if (pdfStatus.value is PdfStatus.Downloading) return
        viewModelScope.launch {
            pdfStatus.value = PdfStatus.Downloading
            runCatching {
                val cached = ensurePdfCached()
                buildViewIntent(cached)
            }.onSuccess { intent ->
                pdfStatus.value = PdfStatus.Ready
                onIntent(intent)
            }.onFailure { e ->
                pdfStatus.value = PdfStatus.Error(e.localizedMessage ?: "Could not open PDF")
            }
        }
    }

    private suspend fun ensurePdfCached(): File {
        val dir = File(context.cacheDir, "blood").apply { mkdirs() }
        val file = File(dir, "$reportId.pdf")
        if (!file.exists() || file.length() == 0L) {
            file.writeBytes(reports.downloadPdf(reportId))
        }
        return file
    }

    private fun buildViewIntent(file: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    sealed interface UiState {
        data object Loading : UiState
        data class Ready(val report: BloodTestReport) : UiState
        data class Error(val message: String) : UiState
    }

    sealed interface PdfStatus {
        data object Idle : PdfStatus
        data object Downloading : PdfStatus
        data object Ready : PdfStatus
        data class Error(val message: String) : PdfStatus
    }
}
