package com.gte619n.healthfitness.mobile.workouts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gte619n.healthfitness.data.workout.WorkoutSessionController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// Receives the workout notification's action-button taps and forwards them to
// the shared session controller — so logging a set / pausing / skipping rest
// works from the shade, lock screen, or a paired watch without opening the app.
@AndroidEntryPoint
class WorkoutActionReceiver : BroadcastReceiver() {

    @Inject lateinit var controller: WorkoutSessionController

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WorkoutNotification.ACTION_LOG_SET -> controller.logCurrentSetAtTarget()
            WorkoutNotification.ACTION_PAUSE -> controller.togglePause()
            WorkoutNotification.ACTION_RESUME -> controller.togglePause()
            WorkoutNotification.ACTION_SKIP_REST -> controller.skipRest()
            WorkoutNotification.ACTION_ADD_TIME -> controller.addRestTime(15)
        }
    }
}
