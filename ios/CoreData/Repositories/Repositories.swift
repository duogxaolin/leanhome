//
//  Repositories.swift
//  CoreData
//
//  SwiftData repositories wrapping ExerciseModel, UserProfileModel, and
//  WorkoutSessionModel. All APIs are `Sendable`-friendly (`@MainActor`
//  annotations because SwiftData ModelContext is tied to the main actor
//  in the documented usage patterns).
//

import Foundation
import SwiftData
import CoreDomain

@MainActor
public final class ExerciseRepository {
    private let context: ModelContext

    public init(context: ModelContext) {
        self.context = context
    }

    /// All catalog exercises as domain objects.
    public func all() throws -> [Exercise] {
        let models = try context.fetch(FetchDescriptor<ExerciseModel>())
        return models.compactMap { $0.toDomain() }
    }

    /// Slice the catalog as domain exercises.
    public func count() throws -> Int {
        try context.fetchCount(FetchDescriptor<ExerciseModel>())
    }

    public func byID(_ id: String) throws -> Exercise? {
        let descriptor = FetchDescriptor<ExerciseModel>(
            predicate: #Predicate { $0.id == id }
        )
        return try context.fetch(descriptor).first?.toDomain()
    }
}

@MainActor
public final class UserProfileRepository {
    private let context: ModelContext

    public init(context: ModelContext) {
        self.context = context
    }

    /// Persist a domain profile; returns the stored model.
    @discardableResult
    public func save(_ profile: UserProfile) throws -> UserProfileModel {
        let model = UserProfileModel(profile: profile)
        context.insert(model)
        try context.save()
        return model
    }

    /// Fetch the first stored profile (single-user app).
    public func current() throws -> UserProfile? {
        try context.fetch(FetchDescriptor<UserProfileModel>())
            .first?
            .map { $0.toDomain() }
    }

    public func deleteAll() throws {
        try context.delete(model: UserProfileModel.self)
        try context.save()
    }
}

@MainActor
public final class WorkoutSessionRepository {
    private let context: ModelContext

    public init(context: ModelContext) {
        self.context = context
    }

    @discardableResult
    public func save(_ session: WorkoutSession) throws -> WorkoutSessionModel {
        let model = WorkoutSessionModel(session: session)
        context.insert(model)
        try context.save()
        return model
    }

    /// All sessions for `userID`, ordered by scheduled date.
    public func sessions(for userID: UUID) throws -> [WorkoutSession] {
        let descriptor = FetchDescriptor<WorkoutSessionModel>(
            predicate: #Predicate { $0.userId == userID },
            sortBy: [SortDescriptor(\.scheduledDate, order: .reverse)]
        )
        return try context.fetch(descriptor).map { $0.toDomain() }
    }

    /// The most recent `n` completed sessions (for fatigue tracking).
    public func recentCompletedSessions(for userID: UUID, limit: Int = 3) throws -> [WorkoutSession] {
        let descriptor = FetchDescriptor<WorkoutSessionModel>(
            predicate: #Predicate { $0.userId == userID && $0.status == "COMPLETED" },
            sortBy: [SortDescriptor(\.createdAt, order: .reverse)]
        )
        let models = try context.fetch(descriptor).prefix(limit)
        return models.map { $0.toDomain() }
    }

    /// Update session with post-check-in data: RPE, tags, actual sets,
    /// completion status, duration.
    @discardableResult
    public func update(
        session id: UUID,
        rpe: Int,
        feelingTags: [FeelingTag],
        notes: String?,
        actualSets: [SessionExercise],
        completedDurationSeconds: Int
    ) throws -> WorkoutSession? {
        let descriptor = FetchDescriptor<WorkoutSessionModel>(
            predicate: #Predicate { $0.id == id }
        )
        guard let model = try context.fetch(descriptor).first else { return nil }
        model.rpeScore = rpe
        model.feedbackTags = feelingTags.isEmpty
            ? nil
            : (try? String(data: JSONEncoder().encode(feelingTags.map(\.rawValue)), encoding: .utf8))
        model.feedbackNotes = notes
        model.exercises = (try? String(data: JSONEncoder().encode(actualSets), encoding: .utf8)) ?? "[]"
        model.completedDurationSeconds = completedDurationSeconds
        model.status = SessionStatus.completed.rawValue
        try context.save()
        return model.toDomain()
    }
}

@MainActor
public final class WeightLogRepository {
    private let context: ModelContext

    public init(context: ModelContext) {
        self.context = context
    }

    @discardableResult
    public func save(userId: UUID, weightKg: Double, loggedDate: String) throws -> WeightLogModel {
        let model = WeightLogModel(userId: userId, weightKg: weightKg, loggedDate: loggedDate)
        context.insert(model)
        try context.save()
        return model
    }

    /// All weigh-in entries for `userId`, ordered by date ascending.
    public func entries(for userId: UUID) throws -> [WeightLogModel] {
        let descriptor = FetchDescriptor<WeightLogModel>(
            predicate: #Predicate { $0.userId == userId },
            sortBy: [SortDescriptor(\.loggedDate, order: .forward)]
        )
        return try context.fetch(descriptor)
    }

    /// Most recent weigh-in (if any).
    public func latestEntry(for userId: UUID) throws -> WeightLogModel? {
        let descriptor = FetchDescriptor<WeightLogModel>(
            predicate: #Predicate { $0.userId == userId },
            sortBy: [SortDescriptor(\.loggedDate, order: .reverse)]
        )
        return try context.fetch(descriptor).first
    }
}
