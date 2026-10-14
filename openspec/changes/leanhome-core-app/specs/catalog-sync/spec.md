# Spec Delta: Catalog Delta Synchronization

## Purpose
Provides a lightweight backend REST/JSON API that mobile clients query for exercise catalog updates (delta sync) and use to back up user profile, session, and weight-log data. The server is minimal in dependencies (Node.js + Express + SQLite) and Cloudflare R2 (or any S3-compatible storage) compatible for future media delivery.

## ADDED Requirements

### Requirement: Catalog Delta Synchronization
The system SHALL provide a backend API that clients can query for catalog updates. The API SHALL expose:
1. A version endpoint returning the current catalog version
2. A delta endpoint returning exercises added/modified/removed since a given version
3. A profile sync endpoint accepting user profile and session data for backup

#### Scenario: Client checks for catalog update
- **WHEN** client sends GET /api/v1/catalog/version with no auth
- **THEN** server returns current catalog version number and last updated timestamp

#### Scenario: Client requests delta payload
- **WHEN** client sends GET /api/v1/catalog/delta?since=1 and current version is 2
- **THEN** server returns all exercises in version 2 (full refresh for V1)

#### Scenario: Client is already up to date
- **WHEN** client sends GET /api/v1/catalog/delta?since=<current_version> where since equals the current version
- **THEN** server returns an empty exercises array and an empty deleted_ids array with the current version number

### Requirement: Catalog Version Determinism
The catalog version SHALL be deterministically derived from the content hash of the seed catalog file (`shared/catalog/exercises_seed.json`) so that the version changes if and only if the catalog content changes. No manual version bump or database lookup is required.

#### Scenario: Seed file is unchanged across restarts
- **WHEN** the server starts twice without any modification to the seed catalog
- **THEN** both starts report the same { "version": <int>, "updated_at": <epoch_seconds> } on the version endpoint

### Requirement: Profile Backup Storage
The profile sync endpoint SHALL persist every received payload (user profile, workout sessions, and weight logs) to a local SQLite database keyed by `user_id`, recording a server-side `synced_at` epoch timestamp. The server SHALL return `{ "synced_at": <epoch_seconds>, "status": "ok" }` on success.

#### Scenario: Client backs up profile
- **WHEN** client sends POST /api/v1/profiles/sync with { "user_id": "<uuid>", "profile": {...}, "sessions": [...], "weight_logs": [...] }
- **THEN** server stores the payload and returns { "synced_at": <epoch_seconds>, "status": "ok" }

#### Scenario: Missing user_id
- **WHEN** client sends POST /api/v1/profiles/sync without a user_id field
- **THEN** server returns HTTP 400 with an error message indicating user_id is required

### Requirement: Health Check
The API SHALL expose a health check endpoint at GET /api/v1/health that returns `{ "status": "ok" }` without requiring authentication, so load balancers and container orchestrators can verify liveness.

#### Scenario: Health probe
- **WHEN** any client sends GET /api/v1/health
- **THEN** server returns { "status": "ok" } with HTTP 200
