//
//  WorkoutSession.swift
//  CoreDomain
//
//  Plain domain representation of a workout session + its planned exercises.
//  Mirrors shared/schemas/workout-session.schema.json exactly.
//

import Foundation

/// A single planned exercise inside a session.
public struct SessionExercise: Codable, Sendable, Hashable, Identifiable {
    public var id: String { exerciseId }
    public let exerciseId: String
    public var plannedSets: Int
    public var plannedReps: Int
    public var plannedRestSeconds: Int
    public var actualSetsCompleted: Int?
    public var actualRepsCompleted: [Int]?

    public init(
        exerciseId: String,
        plannedSets: Int,
        plannedReps: Int,
        plannedRestSeconds: Int = 60,
        actualSetsCompleted: Int? = nil,
        actualRepsCompleted: [Int]? = nil
    ) {
        self.exerciseId = exerciseId
        self.plannedSets = plannedSets
        self.plannedReps = plannedReps
        self.plannedRestSeconds = plannedRestSeconds
        self.actualSetsCompleted = actualSetsCompleted
        self.actualRepsCompleted = actualRepsCompleted
    }

    enum CodingKeys: String, CodingKey {
        case exerciseId = "exercise_id"
        case plannedSets = "planned_sets"
        case plannedReps = "planned_reps"
        case plannedRestSeconds = "planned_rest_seconds"
        case actualSetsCompleted = "actual_sets_completed"
        case actualRepsCompleted = "actual_reps_completed"
    }
}

/// A scheduled or completed workout session record.
public struct WorkoutSession: Identifiable, Codable, Sendable, Hashable {
    public var id: UUID
    public var userId: UUID
    public var scheduledDate: String   // ISO 8601 date YYYY-MM-DD
    public var splitName: SplitSession
    public var status: SessionStatus
    public var exercises: [SessionExercise]
    public var completedDurationSeconds: Int?
    public var rpeScore: Int?
    public var feedbackTags: [FeelingTag]
    public var feedbackNotes: String?
    public var createdAt: Int

    public init(
        id: UUID = UUID(),
        userId: UUID,
        scheduledDate: String,
        splitName: SplitSession,
        status: SessionStatus = .pending,
        exercises: [SessionExercise] = [],
        completedDurationSeconds: Int? = nil,
        rpeScore: Int? = nil,
        feedbackTags: [FeelingTag] = [],
        feedbackNotes: String? = nil,
        createdAt: Int = Int(Date().timeIntervalSince1970)
    ) {
        self.id = id
        self.userId = userId
        self.scheduledDate = scheduledDate
        self.splitName = splitName
        self.status = status
        self.exercises = exercises
        self.completedDurationSeconds = completedDurationSeconds
        self.rpeScore = rpeScore
        self.feedbackTags = feedbackTags
        self.feedbackNotes = feedbackNotes
        self.createdAt = createdAt
    }

    enum CodingKeys: String, CodingKey {
        case id
        case userId = "user_id"
        case scheduledDate = "scheduled_date"
        case splitName = "split_name"
        case status
        case exercises
        case completedDurationSeconds = "completed_duration_seconds"
        case rpeScore = "rpe_score"
        case feedbackTags = "feedback_tags"
        case feedbackNotes = "feedback_notes"
        case createdAt = "created_at"
    }
}
