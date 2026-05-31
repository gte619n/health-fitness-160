package com.gte619n.healthfitness.feature.workouts.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gte619n.healthfitness.data.workout.ExerciseRepository
import com.gte619n.healthfitness.data.workout.WorkoutRepository
import com.gte619n.healthfitness.domain.workout.Exercise
import com.gte619n.healthfitness.domain.workout.LoggedSet
import com.gte619n.healthfitness.domain.workout.PlayerStep
import com.gte619n.healthfitness.domain.workout.SessionEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PlayerPhase { Loading, Working, Resting, Paused, Finishing }

data class WorkoutPlayerUiState(
    val loading: Boolean = true,
    val title: String = "",
    val steps: List<PlayerStep> = emptyList(),
    val index: Int = 0,
    val phase: PlayerPhase = PlayerPhase.Loading,
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

@HiltViewModel
class WorkoutPlayerViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutPlayerUiState())
    val state: StateFlow<WorkoutPlayerUiState> = _state.asStateFlow()

    private lateinit var sessionId: String
    private val logged = mutableMapOf<String, LoggedSet>()
    private val exerciseCache = mutableMapOf<String, Exercise>()
    private var timerJob: Job? = null
    private var loaded = false

    fun load(id: String) {
        if (loaded) return
        loaded = true
        sessionId = id
        viewModelScope.launch {
            try {
                val session = repository.get(id)
                val steps = SessionEngine.steps(session)
                logged.putAll(session.loggedSets)
                val totalSets = steps.count { it is PlayerStep.PerformSet }
                _state.update {
                    it.copy(
                        loading = false,
                        title = session.title,
                        steps = steps,
                        totalSets = totalSets,
                        setsCompleted = logged.values.count { l -> l.completed },
                        runningVolume = SessionEngine.volume(logged),
                    )
                }
                goTo(SessionEngine.resumeIndex(steps, logged))
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Failed to load workout") }
            }
        }
    }

    // ---- navigation between steps ----

    private fun goTo(idx: Int) {
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
                    _state.update { it.copy(phase = PlayerPhase.Working, secondsRemaining = step.set.targetSeconds) }
                    startCountdown { onTimedSetExpired() }
                } else {
                    _state.update { it.copy(phase = PlayerPhase.Working, secondsRemaining = null) }
                }
            }
            is PlayerStep.Rest -> {
                loadExercise(step.upNext.exercise.exerciseId)
                _state.update { it.copy(phase = PlayerPhase.Resting, secondsRemaining = step.seconds) }
                startCountdown { advance() }
            }
        }
    }

    fun advance() = goTo(_state.value.index + 1)

    fun previous() {
        val prev = (_state.value.index - 1).coerceAtLeast(0)
        goTo(prev)
    }

    fun skipRest() {
        if (_state.value.currentStep is PlayerStep.Rest) advance()
    }

    fun addRestTime(seconds: Int) {
        _state.update { it.copy(secondsRemaining = (it.secondsRemaining ?: 0) + seconds) }
    }

    // ---- set logging ----

    fun logCurrentSet(reps: Int?, weight: Double?) {
        val step = currentPerformSet() ?: return
        record(step.set.setId, reps, weight)
        advance()
    }

    fun onTimedSetExpired() {
        val step = currentPerformSet() ?: return
        record(step.set.setId, null, step.set.targetWeight)
        advance()
    }

    private fun record(setId: String, reps: Int?, weight: Double?) {
        // Optimistic local update; the network write is best-effort and never
        // blocks the workout (errors are swallowed — completion reconciles).
        logged[setId] = LoggedSet(setId, reps, weight, completed = true, loggedAt = null)
        _state.update {
            it.copy(
                runningVolume = SessionEngine.volume(logged),
                setsCompleted = logged.values.count { l -> l.completed },
            )
        }
        viewModelScope.launch {
            try {
                repository.logSet(sessionId, setId, reps, weight, true)
            } catch (_: Exception) {
                // Keep the local log; the complete() call will reconcile.
            }
        }
    }

    // ---- pause / resume ----

    fun togglePause() {
        val s = _state.value
        if (s.phase == PlayerPhase.Paused) {
            when (val step = s.currentStep) {
                is PlayerStep.Rest -> {
                    _state.update { it.copy(phase = PlayerPhase.Resting) }
                    startCountdown { advance() }
                }
                is PlayerStep.PerformSet -> {
                    _state.update { it.copy(phase = PlayerPhase.Working) }
                    if (step.set.isTimed) startCountdown { onTimedSetExpired() }
                }
                null -> {}
            }
        } else {
            timerJob?.cancel()
            _state.update { it.copy(phase = PlayerPhase.Paused) }
        }
    }

    // ---- finishing ----

    fun finishNow() = finish()

    private fun finish() {
        timerJob?.cancel()
        _state.update { it.copy(phase = PlayerPhase.Finishing) }
        viewModelScope.launch {
            try {
                repository.complete(sessionId)
                _state.update { it.copy(finishedSessionId = sessionId) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Couldn't finish workout") }
            }
        }
    }

    // ---- helpers ----

    private fun currentPerformSet(): PlayerStep.PerformSet? =
        _state.value.currentStep as? PlayerStep.PerformSet

    private fun startCountdown(onZero: () -> Unit) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while ((_state.value.secondsRemaining ?: 0) > 0) {
                delay(1000)
                _state.update { it.copy(secondsRemaining = (it.secondsRemaining ?: 0) - 1) }
            }
            onZero()
        }
    }

    private fun loadExercise(exerciseId: String) {
        exerciseCache[exerciseId]?.let { ex ->
            _state.update { it.copy(currentExercise = ex) }
            return
        }
        _state.update { it.copy(currentExercise = null) }
        viewModelScope.launch {
            try {
                val ex = exerciseRepository.get(exerciseId)
                exerciseCache[exerciseId] = ex
                // Only apply if the player is still on a step for this exercise.
                if (currentExerciseId() == exerciseId) {
                    _state.update { it.copy(currentExercise = ex) }
                }
            } catch (_: Exception) {
                // No demo — the screen falls back to a placeholder.
            }
        }
    }

    private fun currentExerciseId(): String? = when (val step = _state.value.currentStep) {
        is PlayerStep.PerformSet -> step.exercise.exerciseId
        is PlayerStep.Rest -> step.upNext.exercise.exerciseId
        null -> null
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
