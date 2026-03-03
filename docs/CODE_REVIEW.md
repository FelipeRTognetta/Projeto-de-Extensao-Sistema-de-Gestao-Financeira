# Code Review Guidelines — Financial Management System

## Purpose

This document defines the code review process and criteria for all contributions
to the Financial Management System. Every pull request must be reviewed against
these guidelines before merge, in accordance with
[Constitution Principle IV: Code Review & Documentation](../.specify/memory/constitution.md).

## Review Process

### Before Submitting a PR

1. **Self-review**: Author must review their own diff before requesting review
2. **Tests pass**: All unit tests (`./gradlew testDebugUnitTest`) must pass locally
3. **No lint warnings**: Run `./gradlew lint` and resolve issues
4. **Commit messages**: Clear, imperative-mood messages explaining intent (not just listing changes)
5. **Story reference**: Commit messages should reference the user story (e.g., `[US1] Add patient validation`)

### During Review

Reviewers check the following areas in order of priority:

1. **Correctness** — Does the code do what it claims?
2. **Security** — Are there vulnerabilities or data exposure risks?
3. **Test coverage** — Are new/changed paths tested?
4. **Simplicity** — Is the solution as simple as it can be?
5. **Documentation** — Are public APIs and complex logic documented?

### After Review

- Author addresses all comments or explains why they disagree
- Reviewer re-reviews addressed comments
- PR merged only after at least one approval with no unresolved comments
- Squash-merge preferred for feature branches; merge commit for multi-commit PRs

---

## Constitution Compliance Checklist

Every reviewer must verify these items. All four principles are mandatory.

### I. Test-First Development (NON-NEGOTIABLE)

- [ ] New code has corresponding tests
- [ ] Tests follow Arrange-Act-Assert (AAA) pattern
- [ ] Test names clearly describe the scenario (e.g., `testCreatePaymentWithInactivePatientThrowsException`)
- [ ] Edge cases and error paths are tested, not just happy paths
- [ ] Critical modules (auth, encryption, calculations, validators) maintain 85%+ coverage
- [ ] Overall coverage remains above 75%
- [ ] No `@Ignore` or disabled tests without a linked issue explaining why

### II. Security-First Architecture

- [ ] No hardcoded secrets, credentials, API keys, or encryption keys in code
- [ ] All user input is validated at system boundaries (validators, form screens)
- [ ] Sensitive data uses `BigDecimal` for monetary values (never `Float`/`Double`)
- [ ] Logging uses `AppLogger` — never `android.util.Log` directly
- [ ] No sensitive data (passwords, keys, patient info) appears in log statements
- [ ] Encryption operations use `EncryptionService` / `SecureKeyStore` — no custom crypto
- [ ] SQL queries use Room parameterized queries (no string concatenation)
- [ ] Patient status checks enforce read-only for INACTIVE patients
- [ ] Per-operation auth (Tier 2) required for payment and export operations

### III. Clean Code & Simplicity

- [ ] Functions and classes have a single, clear responsibility
- [ ] No nesting beyond 3 levels of indentation
- [ ] Variable and function names are self-documenting (no unclear abbreviations)
- [ ] No over-engineering: no abstractions for one-time use, no premature optimization
- [ ] YAGNI: no features, flags, or configurability beyond what was requested
- [ ] Constants are in `utils/Constants.kt` (not scattered or magic numbers)
- [ ] Comments explain "why", not "what" — the code itself should be clear
- [ ] No dead code, unused imports, or commented-out code blocks
- [ ] Compose: one composable per file for screens; small helpers can share a file

### IV. Code Review & Documentation

- [ ] Public APIs (repositories, use cases, services) have KDoc if non-obvious
- [ ] Complex business logic has brief inline comments explaining intent
- [ ] Commit messages are clear and explain "why" (not just "what changed")
- [ ] PR description summarizes changes and references the user story
- [ ] README or relevant docs updated if the change affects setup, usage, or architecture
- [ ] Error messages use `ErrorHandler.getMessageForException()` and are in Portuguese (pt-BR)

---

## Common Review Scenarios

### Adding a New Entity Field

1. Update `*Entity.kt` Room annotation
2. Update `*Dao.kt` queries if the field is queryable
3. Update domain model in `domain/models/`
4. Update entity-to-model mapping in repository
5. Update validator if the field requires validation
6. Update form screen and ViewModel
7. Add database migration if modifying existing schema
8. Write unit tests for validation rules
9. Write integration tests for DAO operations

### Adding a New Screen

1. Create screen composable in `ui/screens/`
2. Add route to `AppDestinations.kt`
3. Add navigation entry to `AppNavGraph.kt`
4. Create or update ViewModel with new state flows
5. Add ViewModel factory to `AppModule.kt`
6. Write unit tests for ViewModel logic
7. Write Compose UI tests for screen rendering

### Modifying Business Logic

1. Update or add failing tests first (TDD)
2. Implement the change
3. Verify tests pass
4. Check that related validators are updated
5. Verify `PatientStatusEnforcer` rules still hold
6. Check that balance calculations remain correct (if payment-related)

---

## Severity Levels for Review Findings

| Level | Description | Action Required |
|-------|-------------|-----------------|
| **Blocker** | Security vulnerability, data loss risk, broken functionality | Must fix before merge |
| **Critical** | Missing tests for critical path, incorrect calculations | Must fix before merge |
| **Major** | Code quality issues, missing edge case tests, unclear naming | Should fix before merge |
| **Minor** | Style inconsistencies, missing comments, minor optimization | Can fix in follow-up PR |
| **Suggestion** | Alternative approach, potential improvement | Author's discretion |

---

## Commit Message Format

```
[US#] <imperative verb> <short description>

<optional body explaining why this change was made>

<optional footer with breaking changes or issue references>
```

### Examples

```
[US1] Add phone format validation to PatientValidator

Brazilian phone numbers must match (XX) XXXXX-XXXX format.
International formats also supported via regex pattern.

[US3] Fix balance calculation for mixed payment statuses

Amount Due Now was incorrectly including PENDING payments
with future dates. Now filters by payment_date <= today.

[US5] Enforce read-only mode for inactive patients

PatientStatusEnforcer now blocks appointment and payment
creation for patients with INACTIVE status.
```

### Commit Message Anti-Patterns

- "Fix bug" (no context)
- "Update files" (no specificity)
- "WIP" as a final commit message
- "Refactor" without explaining what or why
- Messages that only describe "what" changed, not "why"

---

## Tools

| Tool | Command | Purpose |
|------|---------|---------|
| Unit tests | `./gradlew testDebugUnitTest` | Run all unit tests |
| Lint | `./gradlew lint` | Static code analysis |
| Coverage | `./gradlew testCoverage` | JaCoCo coverage report |
| Instrumented tests | `./gradlew connectedDebugAndroidTest` | Integration + UI tests |
| Build | `./gradlew assembleDebug` | Verify build succeeds |
