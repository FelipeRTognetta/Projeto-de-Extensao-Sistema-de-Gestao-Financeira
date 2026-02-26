# Testing Strategy — Financial Management System

## Overview

Test-first development (TDD) is a **non-negotiable** principle per the project
constitution. All new features must have tests written before or alongside
the implementation code.

## Test Pyramid

```
        ╱ UI Tests ╲          10%   Compose Testing (screen rendering, navigation)
       ╱────────────╲
      ╱  Integration  ╲       20%   AndroidJUnit4 (Room, Encryption, Auth flows)
     ╱────────────────╲
    ╱    Unit Tests     ╲      70%   JUnit 4 + Mockito (VMs, UseCases, Validators)
   ╱────────────────────╲
```

## Coverage Targets

| Scope | Target | Tool |
|-------|--------|------|
| Overall | 75% | JaCoCo |
| Auth module (BiometricAuthManager, SessionManager) | 85% | JaCoCo |
| Encryption module (EncryptionService, SecureKeyStore) | 85% | JaCoCo |
| Financial calculations (BalanceCalculator, BillableHours) | 85% | JaCoCo |
| Validators (Patient, Payment, Appointment) | 85% | JaCoCo |
| ViewModels | 75% | JaCoCo |
| Repositories | 75% | JaCoCo |

## Running Tests

### Unit tests

```bash
# All unit tests
./gradlew testDebugUnitTest

# Specific test class
./gradlew testDebugUnitTest --tests "com.psychologist.financial.services.EncryptionServiceTest"

# With coverage report
./gradlew testCoverage
# HTML report: build/reports/jacoco/test/html/index.html
```

### Instrumented tests (requires emulator or device)

```bash
# Start an emulator first
emulator -avd Pixel_7_API_35

# All instrumented tests
./gradlew connectedDebugAndroidTest

# Specific test class
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.psychologist.financial.SecureKeyStoreTest
```

### Coverage report

```bash
./gradlew testCoverage
# Opens: build/reports/jacoco/test/html/index.html
```

## Test Directory Structure

```
android/app/src/
├── test/kotlin/com/psychologist/financial/
│   ├── services/
│   │   ├── EncryptionServiceTest.kt         # AES-256-GCM, key management
│   │   ├── KeyRotationServiceTest.kt        # Rotation detection, scheduling, policy
│   │   ├── BiometricAuthManagerTest.kt      # Availability, enrollment, session timeout
│   │   └── SessionManagerTest.kt            # Session lifecycle, state transitions
│   ├── viewmodel/
│   │   ├── PatientViewModelTest.kt          # CRUD operations, state management
│   │   ├── AppointmentViewModelTest.kt      # Form validation, list states
│   │   ├── PaymentViewModelTest.kt          # Balance calculations, status filters
│   │   ├── DashboardViewModelTest.kt        # Metrics aggregation, monthly trends
│   │   └── ExportViewModelTest.kt           # Export flows, error handling
│   ├── domain/
│   │   ├── PatientValidatorTest.kt          # Name, phone, email validation
│   │   ├── PaymentValidatorTest.kt          # Amount, method, status validation
│   │   ├── AppointmentValidatorTest.kt      # Date, time, duration validation
│   │   └── BalanceCalculatorTest.kt         # BigDecimal arithmetic
│   └── data/
│       ├── PatientRepositoryTest.kt         # CRUD, uniqueness
│       ├── AppointmentRepositoryTest.kt     # Insert, query, date range
│       └── PaymentRepositoryTest.kt         # Insert, status transitions
│
└── androidTest/kotlin/com/psychologist/financial/
    ├── AuthenticationFlowTest.kt            # Complete auth flow integration
    ├── PerOperationAuthTest.kt              # Payment/export biometric auth
    ├── DatabaseEncryptionTest.kt            # SQLCipher + key management
    ├── SecureKeyStoreTest.kt                # Keystore + DataStore integration
    ├── PatientIntegrationTest.kt            # Patient CRUD with Room
    ├── AppointmentIntegrationTest.kt        # Appointment CRUD with Room
    ├── PaymentIntegrationTest.kt            # Payment CRUD with Room
    └── ui/
        ├── PatientListScreenTest.kt         # Compose rendering tests
        ├── AppointmentListScreenTest.kt     # List rendering, empty states
        ├── PaymentListScreenTest.kt         # Balance display, filters
        └── DashboardScreenTest.kt           # Metrics cards rendering
```

## Test Patterns

### Unit test structure (AAA pattern)

```kotlin
@Test
fun testCreatePatientWithValidData() {
    // Arrange
    val patient = Patient(name = "João Silva", phone = "11999999999")
    `when`(repository.createPatient(patient)).thenReturn(1L)

    // Act
    val result = useCase(patient)

    // Assert
    assertEquals(1L, result)
    verify(repository).createPatient(patient)
}
```

### ViewModel test with StateFlow

```kotlin
@Test
fun testLoadPatientsUpdatesState() = runTest {
    val patients = listOf(Patient(id = 1, name = "Maria"))
    `when`(useCase(includeInactive = false)).thenReturn(patients)

    viewModel.loadPatients()

    val state = viewModel.patientListState.value
    assertTrue(state is PatientViewState.ListState.Success)
    assertEquals(1, (state as PatientViewState.ListState.Success).patients.size)
}
```

### Compose UI test

```kotlin
@Test
fun testPatientListDisplaysItems() {
    composeTestRule.setContent {
        PatientListScreen(viewModel = viewModel, onPatientClick = {}, onAddClick = {})
    }

    composeTestRule.onNodeWithText("Maria Silva").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Adicionar paciente").assertExists()
}
```

## Mocking Guidelines

- Use `@Mock` annotations with `MockitoJUnitRunner`
- Mock repositories and use cases, never mock domain models
- Use `runBlocking` for suspend function tests
- Use `runTest` from `kotlinx-coroutines-test` for coroutine-heavy tests
- Use `mockStatic` (Mockito 5+) sparingly — prefer constructor injection

## What NOT to Test

- Android framework classes (Activity lifecycle, system services)
- Generated Room DAO code (tested via integration tests)
- Simple data classes with no logic
- Third-party library internals (SQLCipher, Tink)
