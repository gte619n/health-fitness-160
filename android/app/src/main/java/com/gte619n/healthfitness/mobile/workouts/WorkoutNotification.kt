package com.gte619n.healthfitness.mobile.workouts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.gte619n.healthfitness.mobile.R
import com.gte619n.healthfitness.data.workout.WorkoutPhase
import com.gte619n.healthfitness.data.workout.WorkoutSessionState
import com.gte619n.healthfitness.domain.workout.PlayerStep
import com.gte619n.healthfitness.mobile.MainActivity

// Builds the ongoing workout notification from the shared session state. The
// notification lets the user log a set / pause / skip rest / add time straight
// from the shade or lock screen — and, via WearableExtender, from a paired
// Wear OS watch — without opening the app.
object WorkoutNotification {

    const val CHANNEL_ID = "workout_session"
    const val NOTIFICATION_ID = 4201

    // Broadcast actions handled by WorkoutActionReceiver.
    const val ACTION_LOG_SET = "com.gte619n.healthfitness.workout.LOG_SET"
    const val ACTION_PAUSE = "com.gte619n.healthfitness.workout.PAUSE"
    const val ACTION_RESUME = "com.gte619n.healthfitness.workout.RESUME"
    const val ACTION_SKIP_REST = "com.gte619n.healthfitness.workout.SKIP_REST"
    const val ACTION_ADD_TIME = "com.gte619n.healthfitness.workout.ADD_TIME"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active workout",
            NotificationManager.IMPORTANCE_LOW, // ongoing; quiet, no sound per update
        ).apply {
            description = "Controls for your in-progress workout"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun build(context: Context, state: WorkoutSessionState): Notification {
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        val step = state.currentStep
        val actions = mutableListOf<NotificationCompat.Action>()

        when {
            state.phase == WorkoutPhase.Paused -> {
                builder.setContentTitle("${state.title} · paused")
                builder.setContentText(stepLine(step))
                actions += action(context, "Resume", ACTION_RESUME)
            }
            step is PlayerStep.Rest -> {
                builder.setContentTitle("Rest")
                builder.setContentText("Up next: ${step.upNext.exercise.name} · Set ${step.upNext.setOrdinal}")
                applyCountdown(builder, state.secondsRemaining)
                actions += action(context, "+15s", ACTION_ADD_TIME)
                actions += action(context, "Skip", ACTION_SKIP_REST)
                actions += action(context, "Pause", ACTION_PAUSE)
            }
            step is PlayerStep.PerformSet -> {
                builder.setContentTitle("${step.exercise.name} · Set ${step.setOrdinal} of ${step.setCount}")
                if (step.set.isTimed) {
                    builder.setContentText("Hold ${formatClock(step.set.targetSeconds ?: 0)}")
                    applyCountdown(builder, state.secondsRemaining)
                    actions += action(context, "+15s", ACTION_ADD_TIME)
                    actions += action(context, "Done", ACTION_LOG_SET)
                    actions += action(context, "Pause", ACTION_PAUSE)
                } else {
                    builder.setContentText(targetLine(step))
                    actions += action(context, "Log set", ACTION_LOG_SET)
                    actions += action(context, "Pause", ACTION_PAUSE)
                }
            }
            else -> {
                builder.setContentTitle(state.title.ifBlank { "Workout" })
                builder.setContentText("In progress")
            }
        }

        // Progress sub-text on every state.
        builder.setSubText("${state.setsCompleted}/${state.totalSets} sets")

        actions.forEach { builder.addAction(it) }

        // Mirror the actions onto a paired Wear OS watch (tap-to-advance on the
        // wrist) without a dedicated Wear app.
        if (actions.isNotEmpty()) {
            val wear = NotificationCompat.WearableExtender()
            actions.forEach { wear.addAction(it) }
            builder.extend(wear)
        }

        return builder.build()
    }

    // Live system chronometer counting down to the deadline — no per-second
    // notification rebuild needed; the OS renders the ticking clock.
    private fun applyCountdown(builder: NotificationCompat.Builder, secondsRemaining: Int?) {
        if (secondsRemaining == null) return
        builder.setUsesChronometer(true)
        builder.setChronometerCountDown(true)
        builder.setWhen(System.currentTimeMillis() + secondsRemaining * 1000L)
        builder.setShowWhen(true)
    }

    private fun action(context: Context, title: String, intentAction: String): NotificationCompat.Action {
        val pi = PendingIntent.getBroadcast(
            context,
            intentAction.hashCode(),
            Intent(context, WorkoutActionReceiver::class.java).setAction(intentAction),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(0, title, pi).build()
    }

    private fun stepLine(step: PlayerStep?): String = when (step) {
        is PlayerStep.PerformSet -> "${step.exercise.name} · Set ${step.setOrdinal} of ${step.setCount}"
        is PlayerStep.Rest -> "Rest · up next ${step.upNext.exercise.name}"
        null -> "In progress"
    }

    private fun targetLine(step: PlayerStep.PerformSet): String {
        val reps = step.set.targetReps?.let { "$it reps" } ?: "—"
        val weight = step.set.targetWeight?.let {
            val w = if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
            " @ $w ${step.set.weightUnit.name.lowercase()}"
        } ?: ""
        return "Target $reps$weight"
    }

    private fun formatClock(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "$m:${s.toString().padStart(2, '0')}"
    }
}
