package com.gte619n.healthfitness.data.workout

import android.content.Context
import android.content.SharedPreferences
import com.gte619n.healthfitness.domain.workout.Exercise
import com.gte619n.healthfitness.domain.workout.LoggedSet
import com.gte619n.healthfitness.domain.workout.PlayerStep
import com.gte619n.healthfitness.domain.workout.SessionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.max

enum class WorkoutPhase { Idle, Loading, Working, Resting, Paused, Finishing }

// Snapshot of the live session shared by the player UI and the foreground
// notification service. Mirrors what both surfaces need to render.
data class WorkoutSessionState(
    val loading: Boolean = false,
    val active: Boolean = false,
    val sessionId: String? = null,
    val title: String = "",
    val steps: List<PlayerStep> = emptyList(),
    val index: Int = 0,
    val phase: WorkoutPhase = WorkoutPhase.Idle,
    val currentExercise: Exercise? = null,
    val secondsRemaining: Int? = null,
    val runningVolume: Double = 0.0,
    val setsCompleted: Int = 0,
    val totalSets: Int = 0,
    val finishedSessionId: String? = null,
    val error: String? = null,
) {
    val currentStep: PlayerStep? get() = steps.getOrNull(index)
}

// Single source of truth for an in-progress guided workout. Lives as a process
// singleton so it survives config changes and screen open/close, and is shared
// by the player ViewModel and the foreground WorkoutSessionService (so the
// ongoing notification can drive the same session). Timers are anchored to a
// wall-clock deadline persisted to disk, so the countdown does NOT reset when
// the screen opens/closes — it survives recomposition, backgrounding, and
// process death (resumes from the persisted index + deadline).
@Singleton
class WorkoutSessionController @Inject constructor(
    private val repository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    @ApplicationContext context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("workout_session", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(WorkoutSessionState())
    val state: StateFlow<WorkoutSessionState> = _state.asStateFlow()

    private val logged = mutableMapOf<String, LoggedSet>()
    private val exerciseCache = mutableMapOf<String, Exercise>()
    private var timerJob: Job? = null

    val activeSessionId: String? get() = _state.value.sessionId

    // Begin (or resume) a session. Idempotent: re-invoking for the session
    // that's already active is a no-op, so the player screen and the service
    // can both call it without clobbering an in-flight timer.
    fun start(sessionId: String) {
        if (_state.value.sessionId == sessionId && _state.value.active) return
        _state.value = WorkoutSessionState(loading = true, phase = WorkoutPhase.Loading, sessionId = sessionId)
        logged.clear()
        exerciseCache.clear()
        scope.launch {
            try {
                val session = repository.get(sessionId)
                val steps = SessionEngine.steps(session)
                logged.putAll(session.loggedSets)
                val totalSets = steps.count { it is PlayerStep.PerformSet }
                _state.update {
                    it.copy(
                        loading = false,
                        active = true,
                        title = session.title,
                        steps = steps,
                        totalSets = totalSets,
                        setsCompleted = logged.values.count { l -> l.completed },
                        runningVolume = SessionEngine.volume(logged),
                    )
                }
                // Resume the exact step/deadline if we have a persisted snapshot
                // for this session (process death); otherwise the first
                // not-completed set.
                val snap = readSnapshot(sessionId)
                if (snap != null) {
                    goTo(snap.index, resume = true, snapshot = snap)
                } else {
                    goTo(SessionEngine.resumeIndex(steps, logged))
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Failed to load workout") }
            }
        }
    }

    // ---- navigation ----

    private fun goTo(idx: Int, resume: Boolean = false, snapshot: Snapshot? = null) {
        timerJob?.cancel()
        val steps = _state.value.steps
        if (idx >= steps.size) {
            finish()
            return
        }
        _state.update { it.copy(index = idx, error = null) }

        when (val step = steps[idx]) {
            is PlayerStep.PerformSet -> {
                loadExercise(step.exercise.exerciseId)
                if (step.set.isTimed) {
                    beginTimer(step.set.targetSeconds ?: 0, WorkoutPhase.Working, resume, snapshot) {
                        onTimedSetExpired()
                    }
                } else {
                    clearTimer()
                    persist(idx, deadlineWallMs = null, pausedRemaining = null)
                    _state.update { it.copy(phase = WorkoutPhase.Working, secondsRemaining = null) }
                }
            }
            is PlayerStep.Rest -> {
                loadExercise(step.upNext.exercise.exerciseId)
                beginTimer(step.seconds, WorkoutPhase.Resting, resume, snapshot) { advance() }
            }
        }
    }

    fun advance() = goTo(_state.value.index + 1)

    fun previous() = goTo((_state.value.index - 1).coerceAtLeast(0))

    fun skipRest() {
        if (_state.value.currentStep is PlayerStep.Rest) advance()
    }

    fun addRestTime(seconds: Int) {
        val snap = readSnapshot(_state.value.sessionId)
        val deadline = snap?.deadlineWallMs
        if (deadline != null) {
            persist(_state.value.index, deadline + seconds * 1000L, null)
        }
        _state.update { it.copy(secondsRemaining = (it.secondsRemaining ?: 0) + seconds) }
    }

    // ---- set logging ----

    fun logCurrentSet(reps: Int?, weight: Double?) {
        val step = currentPerformSet() ?: return
        record(step.set.setId, reps, weight)
        advance()
    }

    // Notification "Log set" accepts the prescribed target and advances.
    fun logCurrentSetAtTarget() {
        val step = currentPerformSet() ?: return
        record(step.set.setId, step.set.targetReps, step.set.targetWeight)
        advance()
    }

    fun onTimedSetExpired() {
        val step = currentPerformSet() ?: return
        record(step.set.setId, null, step.set.targetWeight)
        advance()
    }

    private fun record(setId: String, reps: Int?, weight: Double?) {
        logged[setId] = LoggedSet(setId, reps, weight, completed = true, loggedAt = null)
        _state.update {
            it.copy(
                runningVolume = SessionEngine.volume(logged),
                setsCompleted = logged.values.count { l -> l.completed },
            )
        }
        val sid = _state.value.sessionId ?: return
        scope.launch {
            try {
                repository.logSet(sid, setId, reps, weight, true)
            } catch (_: Exception) {
                // Local log retained; complete() reconciles.
            }
        }
    }

    // ---- pause / resume ----

    fun togglePause() {
        val s = _state.value
        if (s.phase == WorkoutPhase.Paused) {
            val snap = readSnapshot(s.sessionId)
            val remaining = snap?.pausedRemaining ?: s.secondsRemaining
            val step = s.currentStep
            when {
                step is PlayerStep.Rest && remaining != null ->
                    resumeFromPaused(remaining, WorkoutPhase.Resting) { advance() }
                step is PlayerStep.PerformSet && step.set.isTimed && remaining != null ->
                    resumeFromPaused(remaining, WorkoutPhase.Working) { onTimedSetExpired() }
                else -> _state.update { it.copy(phase = WorkoutPhase.Working) }
            }
        } else {
            timerJob?.cancel()
            val remaining = s.secondsRemaining
            if (remaining != null) persist(s.index, deadlineWallMs = null, pausedRemaining = remaining)
            _state.update { it.copy(phase = WorkoutPhase.Paused) }
        }
    }

    private fun resumeFromPaused(remaining: Int, runningPhase: WorkoutPhase, onZero: () -> Unit) {
        val deadline = System.currentTimeMillis() + remaining * 1000L
        persist(_state.value.index, deadline, pausedRemaining = null)
        _state.update { it.copy(phase = runningPhase, secondsRemaining = remaining) }
        tick(onZero)
    }

    // ---- finishing ----

    fun finish() {
        timerJob?.cancel()
        clearSnapshot()
        _state.update { it.copy(phase = WorkoutPhase.Finishing) }
        val sid = _state.value.sessionId ?: return
        scope.launch {
            try {
                repository.complete(sid)
                _state.update { it.copy(finishedSessionId = sid, active = false) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Couldn't finish workout") }
            }
        }
    }

    // Leave the player without finishing — keep the session in progress on the
    // backend (the dashboard shows Resume), but stop the live timer.
    fun detach() {
        timerJob?.cancel()
    }

    // Clear everything once the summary has been consumed.
    fun reset() {
        timerJob?.cancel()
        clearSnapshot()
        logged.clear()
        exerciseCache.clear()
        _state.value = WorkoutSessionState()
    }

    // ---- timer (wall-clock deadline; reboot-safe and persistable) ----

    private fun beginTimer(
        totalSeconds: Int,
        runningPhase: WorkoutPhase,
        resume: Boolean,
        snapshot: Snapshot?,
        onZero: () -> Unit,
    ) {
        val now = System.currentTimeMillis()

        if (resume && snapshot?.pausedRemaining != null) {
            _state.update { it.copy(phase = WorkoutPhase.Paused, secondsRemaining = snapshot.pausedRemaining) }
            return
        }

        val deadline = if (resume && snapshot?.deadlineWallMs != null) {
            snapshot.deadlineWallMs
        } else {
            (now + totalSeconds * 1000L).also { persist(_state.value.index, it, pausedRemaining = null) }
        }

        val remaining = max(0, ceil((deadline - now) / 1000.0).toInt())
        _state.update { it.copy(phase = runningPhase, secondsRemaining = remaining) }
        if (remaining <= 0) {
            clearTimer(); onZero(); return
        }
        tick(onZero)
    }

    // Recompute remaining from the wall clock each tick — drift-free even if
    // the coroutine was descheduled while the app was backgrounded.
    private fun tick(onZero: () -> Unit) {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                val deadline = readSnapshot(_state.value.sessionId)?.deadlineWallMs ?: break
                val remaining = ceil((deadline - System.currentTimeMillis()) / 1000.0).toInt()
                if (remaining <= 0) {
                    _state.update { it.copy(secondsRemaining = 0) }
                    break
                }
                _state.update { it.copy(secondsRemaining = remaining) }
                delay(250)
            }
            clearTimer()
            onZero()
        }
    }

    private fun clearTimer() {
        persist(_state.value.index, deadlineWallMs = null, pausedRemaining = null)
    }

    // ---- exercise media ----

    private fun currentPerformSet(): PlayerStep.PerformSet? =
        _state.value.currentStep as? PlayerStep.PerformSet

    private fun loadExercise(exerciseId: String) {
        exerciseCache[exerciseId]?.let { ex ->
            _state.update { it.copy(currentExercise = ex) }
            return
        }
        _state.update { it.copy(currentExercise = null) }
        scope.launch {
            try {
                val ex = exerciseRepository.get(exerciseId)
                exerciseCache[exerciseId] = ex
                if (currentExerciseId() == exerciseId) {
                    _state.update { it.copy(currentExercise = ex) }
                }
            } catch (_: Exception) {
                // No demo — UI falls back to a placeholder.
            }
        }
    }

    private fun currentExerciseId(): String? = when (val step = _state.value.currentStep) {
        is PlayerStep.PerformSet -> step.exercise.exerciseId
        is PlayerStep.Rest -> step.upNext.exercise.exerciseId
        null -> null
    }

    // ---- persistence (resume across process death) ----

    private data class Snapshot(val index: Int, val deadlineWallMs: Long?, val pausedRemaining: Int?)

    private fun persist(index: Int, deadlineWallMs: Long?, pausedRemaining: Int?) {
        val sid = _state.value.sessionId ?: return
        prefs.edit().apply {
            putString(KEY_SESSION, sid)
            putInt(KEY_INDEX, index)
            if (deadlineWallMs != null) putLong(KEY_DEADLINE, deadlineWallMs) else remove(KEY_DEADLINE)
            if (pausedRemaining != null) putInt(KEY_PAUSED, pausedRemaining) else remove(KEY_PAUSED)
        }.apply()
    }

    private fun readSnapshot(sessionId: String?): Snapshot? {
        if (sessionId == null) return null
        if (prefs.getString(KEY_SESSION, null) != sessionId) return null
        if (!prefs.contains(KEY_INDEX)) return null
        val deadline = if (prefs.contains(KEY_DEADLINE)) prefs.getLong(KEY_DEADLINE, 0L) else null
        val paused = if (prefs.contains(KEY_PAUSED)) prefs.getInt(KEY_PAUSED, 0) else null
        return Snapshot(prefs.getInt(KEY_INDEX, 0), deadline, paused)
    }

    private fun clearSnapshot() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_SESSION = "session_id"
        const val KEY_INDEX = "index"
        const val KEY_DEADLINE = "deadline_wall_ms"
        const val KEY_PAUSED = "paused_remaining_sec"
    }
}
