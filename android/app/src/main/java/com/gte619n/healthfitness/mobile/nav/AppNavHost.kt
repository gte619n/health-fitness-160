package com.gte619n.healthfitness.mobile.nav

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import com.gte619n.healthfitness.mobile.workouts.WorkoutSessionService
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gte619n.healthfitness.feature.goals.GOAL_ID_ARG
import com.gte619n.healthfitness.feature.goals.GoalRoadmapRoute
import com.gte619n.healthfitness.feature.goals.GoalsChatRoute
import com.gte619n.healthfitness.feature.goals.GoalsListRoute
import com.gte619n.healthfitness.feature.workouts.session.WorkoutOverviewRoute
import com.gte619n.healthfitness.feature.workouts.session.WorkoutPlayerRoute
import com.gte619n.healthfitness.feature.workouts.session.WorkoutSummaryRoute
import com.gte619n.healthfitness.mobile.DashboardRoot

// Minimal app NavHost (IMPL-12 assumption 15). The existing dashboard screens
// remain the start destination as static composables; Goals adds two routes.
object Routes {
    const val DASHBOARD = "dashboard"
    const val GOALS_LIST = "goals"
    const val GOALS_CHAT = "goals/chat"
    const val GOAL_DETAIL = "goals/{$GOAL_ID_ARG}"
    fun goalDetail(goalId: String) = "goals/$goalId"

    const val SESSION_ID_ARG = "sessionId"
    const val WORKOUT_OVERVIEW = "workout/overview/{$SESSION_ID_ARG}"
    const val WORKOUT_PLAYER = "workout/player/{$SESSION_ID_ARG}"
    const val WORKOUT_SUMMARY = "workout/summary/{$SESSION_ID_ARG}"
    fun workoutOverview(sessionId: String) = "workout/overview/$sessionId"
    fun workoutPlayer(sessionId: String) = "workout/player/$sessionId"
    fun workoutSummary(sessionId: String) = "workout/summary/$sessionId"
}

@Composable
fun AppNavHost(widthClass: WindowWidthSizeClass) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardRoot(
                widthClass = widthClass,
                onOpenGoals = { navController.navigate(Routes.GOALS_LIST) },
                onStartWorkout = { sessionId -> navController.navigate(Routes.workoutOverview(sessionId)) },
                onViewWorkoutSummary = { sessionId -> navController.navigate(Routes.workoutSummary(sessionId)) },
            )
        }
        composable(
            route = Routes.WORKOUT_OVERVIEW,
            arguments = listOf(navArgument(Routes.SESSION_ID_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString(Routes.SESSION_ID_ARG).orEmpty()
            WorkoutOverviewRoute(
                sessionId = sessionId,
                onStartWorkout = { id -> navController.navigate(Routes.workoutPlayer(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.WORKOUT_PLAYER,
            arguments = listOf(navArgument(Routes.SESSION_ID_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString(Routes.SESSION_ID_ARG).orEmpty()
            // Promote to a foreground service while the workout is live so the
            // timer keeps running and the ongoing notification (log set / pause
            // / skip rest) is available with the screen off.
            val context = LocalContext.current
            val notifPermission = rememberNotificationPermissionLauncher()
            LaunchedEffect(sessionId) {
                notifPermission()
                WorkoutSessionService.start(context)
            }
            WorkoutPlayerRoute(
                sessionId = sessionId,
                onFinished = { id ->
                    navController.navigate(Routes.workoutSummary(id)) {
                        popUpTo(Routes.WORKOUT_OVERVIEW) { inclusive = true }
                    }
                },
                onExit = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.WORKOUT_SUMMARY,
            arguments = listOf(navArgument(Routes.SESSION_ID_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString(Routes.SESSION_ID_ARG).orEmpty()
            WorkoutSummaryRoute(
                sessionId = sessionId,
                onDone = { navController.popBackStack(Routes.DASHBOARD, inclusive = false) },
            )
        }
        composable(Routes.GOALS_LIST) {
            GoalsListRoute(
                onOpenGoal = { goalId -> navController.navigate(Routes.goalDetail(goalId)) },
                onNewGoal = { navController.navigate(Routes.GOALS_CHAT) },
                onBack = { navController.popBackStack() },
            )
        }
        // Registered BEFORE the parameterized goals/{goalId} route so the
        // static "goals/chat" path matches the chat screen, not the detail.
        composable(Routes.GOALS_CHAT) {
            GoalsChatRoute(
                onBack = { navController.popBackStack() },
                onOpenGoal = { goalId ->
                    // Replace the chat in the back stack with the new roadmap.
                    navController.navigate(Routes.goalDetail(goalId)) {
                        popUpTo(Routes.GOALS_CHAT) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.GOAL_DETAIL,
            arguments = listOf(navArgument(GOAL_ID_ARG) { type = NavType.StringType }),
        ) {
            GoalRoadmapRoute(onBack = { navController.popBackStack() })
        }
    }
}

// Returns a callback that requests POST_NOTIFICATIONS (API 33+) once, so the
// workout's ongoing controls notification can be shown. On older OSes it's a
// no-op (the permission is granted at install time).
@Composable
private fun rememberNotificationPermissionLauncher(): () -> Unit {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return remember { {} }
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* best-effort; the workout proceeds regardless of the grant */ }
    return remember(launcher) {
        { launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    }
}
