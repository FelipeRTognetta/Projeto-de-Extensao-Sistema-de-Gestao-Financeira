# Code Coverage Report — Financial Management System

**Version**: 1.0.0-pre-release
**Date**: 2026-02-26
**Analysis method**: Static audit (Phase 11.7) — JaCoCo report pending build environment setup
**Branch**: `001-financial-management-app`

---

## How to Generate the Coverage Report

### Prerequisites

1. **Android Studio** (Hedgehog 2023.1.1+) or **Android SDK** (API 35)
2. **JDK 17+** (`$JAVA_HOME` set)
3. The gradle wrapper jar must be bootstrapped:

```bash
# From project root
cd android
gradle wrapper --gradle-version=8.4  # generates gradle/wrapper/gradle-wrapper.jar
```

### Running Coverage

```bash
# From android/ directory
cd android

# Unit tests + JaCoCo HTML report (no device needed)
./gradlew testCoverage
# Report: app/build/reports/jacoco/test/html/index.html

# Unit tests only (faster)
./gradlew testDebugUnitTest

# Instrumented tests (requires emulator or device)
./gradlew connectedDebugAndroidTest
```

---

## Coverage Targets

| Scope | Target | Rationale |
|-------|--------|-----------|
| Overall | 75% | SOC 2 / PCI DSS compliance baseline |
| Auth module | 85% | Critical security path |
| Encryption module | 85% | Critical security path |
| Financial calculations | 85% | Business correctness |
| Validators | 85% | Input boundary protection |
| ViewModels | 75% | UI logic orchestration |
| Repositories | 75% | Data access correctness |

---

## Current Coverage Assessment (Static Audit)

> **Note**: The following estimates are derived from a manual code audit (Phase 11.5)
> conducted against 102 Kotlin source files and 46 test files (19 unit + 27 instrumented).
> Actual JaCoCo line/branch coverage will differ slightly. Run `./gradlew testCoverage`
> for authoritative numbers.

### Summary

| Category | Total Files | With Unit Tests | Est. Unit Coverage | Status |
|----------|-------------|-----------------|-------------------|--------|
| Services | 13 | 8 | ~62% | ⚠ Below 85% target |
| Use Cases | 10 | 2 | ~20% | ✗ Critical gap |
| Validators | 3 | 3 | ~95% | ✓ Exceeds target |
| ViewModels | 7 | 5 | ~71% | ⚠ Near target |
| Repositories | 5 | 0 (unit) | ~0% (unit only) | ✗ Integration-only |
| **Overall (est.)** | **102** | — | **~60–65%** | ⚠ Below 75% target |

### Module Detail

#### Auth Module — Target: 85%

| File | Unit Tests | Instrumented Tests | Est. Coverage |
|------|-----------|-------------------|---------------|
| `BiometricAuthManager.kt` | `BiometricAuthManagerTest.kt` (20+ tests) | `AuthenticationFlowTest.kt` | ~85% ✓ |
| `SessionManager.kt` | `SessionManagerTest.kt` | `AuthenticationFlowTest.kt` | ~85% ✓ |
| `PerOperationAuthManager.kt` | **NONE** | `PerOperationAuthTest.kt` | ~40% ✗ |
| `AuthenticationViewModel.kt` | **NONE** | — | ~0% ✗ |

**Module status: PARTIAL — `PerOperationAuthManager` and `AuthenticationViewModel` lack unit tests**

#### Encryption Module — Target: 85%

| File | Unit Tests | Instrumented Tests | Est. Coverage |
|------|-----------|-------------------|---------------|
| `EncryptionService.kt` | `EncryptionServiceTest.kt` (30+ tests) | `DatabaseEncryptionTest.kt` | ~90% ✓ |
| `SecureKeyStore.kt` | **NONE** | `SecureKeyStoreTest.kt` | ~55% ⚠ |
| `DatabaseEncryptionManager.kt` | **NONE** | `DatabaseEncryptionTest.kt` | ~55% ⚠ |
| `KeyRotationService.kt` | `KeyRotationServiceTest.kt` (30+ tests) | — | ~90% ✓ |

**Module status: PARTIAL — `SecureKeyStore` and `DatabaseEncryptionManager` lack unit tests**

#### Financial Calculations — Target: 85%

| File | Unit Tests | Instrumented Tests | Est. Coverage |
|------|-----------|-------------------|---------------|
| `BalanceCalculator.kt` | `BalanceCalculatorTest.kt` (40+ tests) | — | ~95% ✓ |
| `BillableHoursCalculator.kt` | `BillableHoursCalculatorTest.kt` | — | ~90% ✓ |
| `MetricsAggregator.kt` | `MetricsAggregatorTest.kt` | — | ~85% ✓ |

**Module status: PASS — all calculation classes have comprehensive unit tests**

#### Validators — Target: 85%

| File | Unit Tests | Est. Coverage |
|------|-----------|---------------|
| `PatientValidator.kt` | `PatientValidatorTest.kt` (32 tests) | ~95% ✓ |
| `PaymentValidator.kt` | `PaymentValidatorTest.kt` (50+ tests) | ~95% ✓ |
| `AppointmentValidator.kt` | `AppointmentValidatorTest.kt` (35+ tests) | ~90% ✓ |
| `DecimalValidator.kt` | Covered via `PaymentValidatorTest.kt` | ~80% ✓ |

**Module status: PASS — validators are the best-covered module in the project**

#### Use Cases — Target: 75%

| File | Unit Tests | Status |
|------|-----------|--------|
| `CreatePatientUseCase.kt` | `CreatePatientUseCaseTest.kt` (26 tests) | ✓ |
| `MarkPatientInactiveUseCase.kt` | `MarkPatientInactiveUseCaseTest.kt` (25+ tests) | ✓ |
| `ReactivatePatientUseCase.kt` | Partial (via MarkInactive test) | ⚠ |
| `CreateAppointmentUseCase.kt` | **NONE** | ✗ |
| `CreatePaymentUseCase.kt` | **NONE** | ✗ |
| `GetAllPatientsUseCase.kt` | **NONE** | ✗ |
| `GetPatientAppointmentsUseCase.kt` | **NONE** | ✗ |
| `GetPatientPaymentsUseCase.kt` | **NONE** | ✗ |
| `GetDashboardMetricsUseCase.kt` | **NONE** | ✗ |
| `ExportDataUseCase.kt` | **NONE** | ✗ |

**Module status: CRITICAL GAP — 8 of 10 use cases have no unit tests**

#### ViewModels — Target: 75%

| File | Unit Tests | Status |
|------|-----------|--------|
| `PatientViewModel.kt` | `PatientViewModelTest.kt` (28 tests) | ✓ |
| `AppointmentViewModel.kt` | `AppointmentViewModelTest.kt` | ✓ |
| `PaymentViewModel.kt` | `PaymentViewModelTest.kt` | ✓ |
| `DashboardViewModel.kt` | `DashboardViewModelTest.kt` | ✓ |
| `ExportViewModel.kt` | `ExportViewModelTest.kt` | ✓ |
| `AuthenticationViewModel.kt` | **NONE** | ✗ |
| `BaseViewModel.kt` | **NONE** | ✗ |

**Module status: PARTIAL — 5/7 ViewModels covered, base class untested**

#### Repositories — Target: 75%

| File | Unit Tests | Instrumented Tests | Status |
|------|-----------|-------------------|--------|
| `PatientRepository.kt` | **NONE** | `PatientRepositoryTest.kt` + `PatientDaoTest.kt` | ⚠ Integration only |
| `AppointmentRepository.kt` | **NONE** | `AppointmentRepositoryTest.kt` + `AppointmentDaoTest.kt` | ⚠ Integration only |
| `PaymentRepository.kt` | **NONE** | `PaymentRepositoryTest.kt` + `PaymentDaoTest.kt` | ⚠ Integration only |
| `DashboardRepository.kt` | **NONE** | `DashboardRepositoryTest.kt` | ⚠ Integration only |
| `ExportRepository.kt` | **NONE** | `CsvExportIntegrationTest.kt` | ⚠ Integration only |

**Module status: INTEGRATION-TESTED — no mocked-DAO unit tests exist for repositories**

> **Note on repositories**: The instrumented tests exercise repositories with a real in-memory
> Room database, which is thorough but requires a device/emulator to run. Adding unit tests
> with mocked DAOs enables faster feedback without a device.

#### Services (Other) — Target: 75%

| File | Unit Tests | Status |
|------|-----------|--------|
| `CsvExportService.kt` | `CsvExportServiceTest.kt` (35+ tests) | ✓ |
| `PatientStatusEnforcer.kt` | `PatientStatusEnforcerTest.kt` (30+ tests) | ✓ |
| `FileStorageManager.kt` | **NONE** | ✗ |

---

## Total Test Count

| Category | Count |
|----------|-------|
| Unit test files | 19 |
| Instrumented test files | 27 |
| **Total test files** | **46** |
| Estimated test cases (unit) | 285+ |
| Estimated test cases (instrumented) | 200+ |
| **Total estimated test cases** | **485+** |

---

## Gaps Requiring Remediation

The following gaps must be closed to meet the 75% overall / 85% critical target:

### Priority 1 — Critical (blocks 85% target)

| # | Gap | Action | Est. Tests |
|---|-----|--------|-----------|
| 1 | `PerOperationAuthManager.kt` — no unit tests | Create `PerOperationAuthManagerTest.kt` | 15–20 |
| 2 | 8 Use Cases — no unit tests | Create test files for each (see T193–T200) | 15–20 each |
| 3 | `AuthenticationViewModel.kt` — no unit tests | Create `AuthenticationViewModelTest.kt` | 15–20 |

### Priority 2 — High (needed for 75% overall)

| # | Gap | Action | Est. Tests |
|---|-----|--------|-----------|
| 4 | `SecureKeyStore.kt` — unit tests | Create `SecureKeyStoreUnitTest.kt` with mocked DataStore | 10–15 |
| 5 | `DatabaseEncryptionManager.kt` — unit tests | Create `DatabaseEncryptionManagerTest.kt` | 10–15 |
| 6 | `BaseViewModel.kt` — unit tests | Create `BaseViewModelTest.kt` | 8–12 |
| 7 | Repositories — mocked-DAO unit tests | Create unit tests for all 5 repos | 10–15 each |

### Priority 3 — Medium

| # | Gap | Action | Est. Tests |
|---|-----|--------|-----------|
| 8 | `FileStorageManager.kt` — no tests | Create `FileStorageManagerTest.kt` | 10–15 |
| 9 | `DecimalValidator.kt` — partial coverage | Expand `PaymentValidatorTest.kt` | 5–8 |
| 10 | `ReactivatePatientUseCase.kt` — partial | Add dedicated `ReactivatePatientUseCaseTest.kt` | 10–15 |

---

## Path to 75% Overall / 85% Critical Coverage

Addressing Priority 1 + Priority 2 gaps would add ~150–200 test cases and bring:
- Overall coverage: **60% → ~78%** (exceeds 75% target)
- Auth module: **~62% → ~88%** (exceeds 85% target)
- Encryption module: **~70% → ~88%** (exceeds 85% target)
- Use Cases: **~20% → ~82%** (exceeds 75% target)

See follow-up tasks T193–T207 in `tasks.md` for the implementation plan.

---

## Coverage Configuration Reference

The JaCoCo task is configured in `android/app/build.gradle.kts`:

```kotlin
tasks.register<JacocoReport>("jacocoTestReport") {
    reports {
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/test/html"))
    }
    // Excludes: generated Room code, data classes, UI composables
    classDirectories.setFrom(...)
}

// Shorthand task
tasks.register("testCoverage") {
    dependsOn("testDebugUnitTest", "jacocoTestReport")
}
```

Report output: `android/app/build/reports/jacoco/test/html/index.html`

---

**Next review**: After closing Priority 1 gaps (T193–T200)
