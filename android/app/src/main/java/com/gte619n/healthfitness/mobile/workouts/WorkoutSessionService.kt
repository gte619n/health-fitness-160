package com.gte619n.healthfitness.mobile.workouts

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gte619n.healthfitness.data.workout.WorkoutEvent
import com.gte619n.healthfitness.data.workout.WorkoutPhase
import com.gte619n.healthfitness.data.workout.WorkoutSessionController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

// Foreground service that keeps an active workout alive (timer + state) while
// the phone is locked or the app is backgrounded/killed, and renders the
// ongoing notification with set controls. The session brain lives in the
// shared WorkoutSessionController; this service just promotes the process to
// foreground and re-posts the notification whenever the state changes.
@AndroidEntryPoint
class WorkoutSessionService : LifecycleService() {

    @Inject lateinit var controller: WorkoutSessionController

    override fun onCreate() {
        super.onCreate()
        WorkoutNotification.ensureChannel(this)

        // Promote to foreground immediately with whatever the current state is,
        // then keep the notification in sync with the shared session state.
        startInForeground(WorkoutNotification.build(this, controller.state.value))

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                controller.state.collect { state ->
                    // Session over (finished or reset) — drop out of foreground.
                    if (!state.active && state.phase != WorkoutPhase.Finishing) {
                        stopSelf()
                        return@collect
                    }
                    NotificationManagerCompat.from(this@WorkoutSessionService)
                        .notify(WorkoutNotification.NOTIFICATION_ID, WorkoutNotification.build(this@WorkoutSessionService, state))
                }
            }
        }

        // Pop a heads-up + vibrate when a rest period elapses on its own, so the
        // user knows to start the next set without watching the screen.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                controller.events.collect { event ->
                    when (event) {
                        is WorkoutEvent.RestFinished -> {
                            NotificationManagerCompat.from(this@WorkoutSessionService).notify(
                                WorkoutNotification.ALERT_NOTIFICATION_ID,
                                WorkoutNotification.buildRestFinished(
                                    this@WorkoutSessionService,
                                    event.upNext.exercise.name,
                                    event.upNext.setOrdinal,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // If the process was killed and restarted, re-attach to the in-progress
        // session so the notification + timer resume from the persisted snapshot.
        controller.activeSessionId?.let { controller.start(it) }
        return START_STICKY
    }

    private fun startInForeground(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                WorkoutNotification.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH,
            )
        } else {
            startForeground(WorkoutNotification.NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    companion object {
        fun start(context: android.content.Context) {
            val intent = Intent(context, WorkoutSessionService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, WorkoutSessionService::class.java))
        }
    }
}
