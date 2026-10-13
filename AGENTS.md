# AGENTS.md — Multi-Agent Collaboration & Orchestration Protocols

## Mission
This document governs autonomous and semi-autonomous AI agents contributing to the **LeanHome** repository. Agents operating in this codebase MUST follow these instructions strictly.

---

## Agent Roles & Specializations

| Agent Role | Domain & Responsibilities | Boundary & Guardrails |
| :--- | :--- | :--- |
| **`orchestrator`** | Requirements exploration, task decomposition, and OpenSpec pipeline control. | Reads, plans, validates. MUST NOT implement code directly. |
| **`android-specialist`** | Android Native development (`android/`): Kotlin, Compose, Room, Media3. | Does not modify iOS code. Follows Kotlin MVI patterns. |
| **`ios-specialist`** | iOS Native development (`ios/`): Swift 6, SwiftUI, SwiftData, AVFoundation. | Does not modify Android code. Adheres to Swift Concurrency. |
| **`math-engine-specialist`** | Energy balance engine, Mifflin-St Jeor formulas, and canonical test vectors (`shared/`). | Ensures 100% calculation parity between Swift and Kotlin. |
| **`database-specialist`** | Exercise catalog curation, SQLite schema design, and seed database generation. | Maintains seed integrity and constraint tagging correctness. |
| **`verifier`** | Independent verification against `openspec` requirements and test execution. | Report-only; does not modify code or fix bugs directly. |

---

## Orchestration & Pipeline Workflows

### 1. Specification-Driven Development
All feature and architecture work must trace back to OpenSpec changes in `openspec/changes/<change-name>/`:
- **Proposals**: `proposal.md` must establish *why* and list capabilities.
- **Specs**: Every capability spec in `specs/<capability>/spec.md` must define observable requirements with RFC 2119 keywords (`SHALL`, `MUST`) and BDD scenarios (`WHEN` / `THEN`).
- **Design**: `design.md` specifies technical decisions, trade-offs, schemas, and diagrams.
- **Tasks**: `tasks.md` provides trackable checkboxes (`- [ ] X.Y ...`) with `← (verify: ...)` points on critical paths.

### 2. Validation Gate
Every agent MUST run and verify OpenSpec validity before declaring planning or spec updates complete:
```bash
openspec validate --all --strict
```
Zero warnings and zero errors are required to pass.

---

## Scope Discipline & Safety Invariants

1. **Parity Enforcement**:
   - Any modification to BMR, TDEE, deficit calculation, or split generation logic MUST update `shared/math_engine_test_vectors.json`.
   - Both Android and iOS test runners must execute against this file. Divergence between platforms is treated as a CRITICAL bug.
2. **Offline-First Guarantee**:
   - Agents must never introduce network dependencies to core onboarding, routine generation, or session playback paths.
   - Network failure must degrade gracefully to bundled seed data and SVG anatomical illustrations.
3. **Medical Safety Limits**:
   - Deficit calculations must respect the hard limit of $\le 25\%$ TDEE ($1000\text{ kcal/day}$) and $\le 1\%$ body weight loss per week.
   - Under no circumstances should an agent remove or loosen clinical threshold warnings.
4. **Clean File Scope**:
   - Agents must never delete or edit files outside the declared task scope.
   - Deleted files require explicit confirmation.
