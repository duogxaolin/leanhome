package com.leanhome.feature.workout.mvi

/** MVI Intents handled by the WorkoutPlayerViewModel. */
sealed class WorkoutPlayerIntent {
    /** User taps "Done Set" → record set, enter rest interval. */
    object DoneSet : WorkoutPlayerIntent()
    /** Skip remaining rest immediately → move to next exercise or set. */
    object SkipRest : WorkoutPlayerIntent()
    /** Add 30 seconds to rest countdown. */
    object AddThirtySeconds : WorkoutPlayerIntent()
    /** Subtract 15 seconds from rest countdown (floor at 5). */
    object SubtractFifteenSeconds : WorkoutPlayerIntent()
    /** End the workout session immediately → SESSION_COMPLETE. */
    object EndSession : WorkoutPlayerIntent()
    /** One-second tick to decrement rest countdown. */
    object RestTick : WorkoutPlayerIntent()
    /** Skip to an exercise at the given index (used by skip button). */
    data class SkipToExercise(val index: Int) : WorkoutPlayerIntent()
}
