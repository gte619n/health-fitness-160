package com.gte619n.healthfitness.mobile.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gte619n.healthfitness.domain.workout.SessionStatus
import com.gte619n.healthfitness.domain.workout.WorkoutSession
import com.gte619n.healthfitness.ui.theme.Hf
import com.gte619n.healthfitness.ui.theme.type

@Composable
fun TodaysWorkoutCard(
    state: TodayWorkoutViewModel.UiState,
    onStart: (String) -> Unit,
    onViewSummary: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    HfCard(modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("Today's workout")
                when (state) {
                    is TodayWorkoutViewModel.UiState.Ready -> StatusPill(state.session.status)
                    else -> {}
                }
            }
            Spacer(Modifier.height(11.dp))

            when (state) {
                is TodayWorkoutViewModel.UiState.Loading ->
                    Text("Loading…", style = Hf.type.bodySm, color = Hf.colors.textTertiary)

                is TodayWorkoutViewModel.UiState.RestDay ->
                    Text(
                        "Rest day — nothing scheduled. Recover well.",
                        style = Hf.type.bodyMd.copy(fontSize = 13.sp),
                        color = Hf.colors.textSecondary,
                    )

                is TodayWorkoutViewModel.UiState.Error ->
                    Text(state.message, style = Hf.type.bodySm, color = Hf.colors.alert)

                is TodayWorkoutViewModel.UiState.Ready ->
                    ReadyBody(state.session, onStart, onViewSummary)
            }
        }
    }
}

@Composable
private fun ReadyBody(
    session: WorkoutSession,
    onStart: (String) -> Unit,
    onViewSummary: (String) -> Unit,
) {
    Text(
        text = session.title,
        style = Hf.type.headingLg.copy(fontSize = 16.sp),
        color = Hf.colors.textPrimary,
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = subtitle(session),
        style = Hf.type.monoSm,
        color = Hf.colors.textTertiary,
    )
    Spacer(Modifier.height(12.dp))

    when (session.status) {
        SessionStatus.COMPLETED -> {
            val summary = session.summary
            if (summary != null) {
                Text(
                    "${summary.setsCompleted} sets · ${trimNumber(summary.totalVolume)} lb · ${summary.estimatedCalories} cal",
                    style = Hf.type.monoSm,
                    color = Hf.colors.textSecondary,
                )
                Spacer(Modifier.height(6.dp))
            }
            TextButton(
                onClick = { onViewSummary(session.sessionId) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text("View summary", color = Hf.colors.accent, style = Hf.type.bodyMd.copy(fontSize = 13.sp))
            }
        }
        else -> {
            val label = if (session.status == SessionStatus.IN_PROGRESS) "Resume workout" else "Start workout"
            Button(
                onClick = { onStart(session.sessionId) },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Hf.colors.accent,
                    contentColor = Hf.colors.textInverse,
                ),
            ) {
                Text(label, style = Hf.type.bodyMd.copy(fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun StatusPill(status: SessionStatus) {
    when (status) {
        SessionStatus.IN_PROGRESS -> Pill("In progress", Tone.Warn)
        SessionStatus.COMPLETED -> Pill("Done", Tone.Good)
        SessionStatus.SKIPPED -> Pill("Skipped", Tone.Neutral)
        SessionStatus.SCHEDULED -> {}
    }
}

private fun subtitle(session: WorkoutSession): String {
    val sets = session.blocks.sumOf { b -> b.exercises.sumOf { it.prescribedSets.size } }
    val parts = mutableListOf("${session.exerciseCount} exercises")
    if (sets > 0) parts += "$sets sets"
    if (session.estimatedMinutes > 0) parts += "~${session.estimatedMinutes} min"
    return parts.joinToString(" · ")
}

private fun trimNumber(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)
