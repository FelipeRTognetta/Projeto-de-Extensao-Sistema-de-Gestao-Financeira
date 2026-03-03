# Architecture — Financial Management System

## Overview

Android application for psychologists to manage patient records, appointments,
payments, and financial dashboards. Built with Kotlin, Jetpack Compose, and
Room + SQLCipher for encrypted local storage.

## MVVM Pattern

The application follows **Model-View-ViewModel** with an additional Use Case
layer between ViewModels and Repositories:

```
┌────────────────────────────────────┐
│           UI Layer                 │
│  Compose Screens + Components     │
│  (declarative, state-driven)      │
└──────────────┬─────────────────────┘
               │ StateFlow / events
┌──────────────▼─────────────────────┐
│         ViewModel Layer            │
│  PatientVM, AppointmentVM,         │
│  PaymentVM, DashboardVM, ExportVM, │
│  AuthenticationVM                  │
│  (extends BaseViewModel)           │
└──────────────┬─────────────────────┘
               │ suspend calls
┌──────────────▼─────────────────────┐
│         Use Case Layer             │
│  GetAllPatientsUseCase,            │
│  CreatePatientUseCase,             │
│  CreatePaymentUseCase, ...         │
│  (single-responsibility, testable) │
└──────────────┬─────────────────────┘
               │ suspend calls
┌──────────────▼─────────────────────┐
│        Repository Layer            │
│  PatientRepository,                │
│  AppointmentRepository,            │
│  PaymentRepository,                │
│  DashboardRepository,              │
│  ExportRepository                  │
│  (extends BaseRepository)          │
└──────────────┬─────────────────────┘
               │ DAO calls (IO thread)
┌──────────────▼─────────────────────┐
│         Data Layer                 │
│  Room DAOs + SQLCipher             │
│  (AES-256-GCM encrypted)           │
└────────────────────────────────────┘
```

## Data Flow

### Read path (e.g., loading patient list)

```
1. Screen calls viewModel.loadPatients()
2. ViewModel calls getAllPatientsUseCase(includeInactive)
3. UseCase calls patientRepository.getAllPatients(includeInactive)
4. Repository calls patientDao.getAll() via withRead { } (Dispatchers.IO)
5. DAO returns List<PatientEntity>
6. Repository maps to List<Patient> (domain model)
7. ViewModel updates _patientListState: MutableStateFlow
8. Screen recomposes via collectAsState()
```

### Write path (e.g., creating a payment)

```
1. Screen calls viewModel.submitCreatePaymentForm(patientId)
2. ViewModel calls createPaymentUseCase(patientId, amount, method, status, date)
3. UseCase validates via PaymentValidator
4. UseCase calls patientRepository.getPatient(patientId) → verifies ACTIVE
5. UseCase calls paymentRepository.createPayment(...)
6. Repository calls paymentDao.insert() via withTransaction { }
7. Returns generated payment ID
8. ViewModel updates _createFormState → Success
9. Screen navigates back
```

## Dependency Graph

```
FinancialApp
  └── AppModule.initialize(context)

MainActivity
  └── AppModule.initDatabase()          [suspend]
      ├── EncryptionService             [lazy singleton]
      ├── SecureKeyStore                [lazy singleton]
      ├── DatabaseEncryptionManager     [lazy singleton]
      └── AppDatabase                   [singleton, SQLCipher]
          ├── PatientDao
          ├── AppointmentDao
          └── PaymentDao

  └── AppModule.provide*ViewModel()
      ├── PatientViewModel
      │   ├── GetAllPatientsUseCase → PatientRepository → PatientDao
      │   ├── CreatePatientUseCase → PatientRepository + PatientValidator
      │   ├── MarkPatientInactiveUseCase → PatientRepository
      │   └── ReactivatePatientUseCase → PatientRepository
      ├── AppointmentViewModel
      │   ├── AppointmentRepository → AppointmentDao
      │   ├── GetPatientAppointmentsUseCase → AppointmentRepository
      │   └── CreateAppointmentUseCase → AppointmentRepository
      ├── PaymentViewModel
      │   ├── PaymentRepository → PaymentDao
      │   ├── GetPatientPaymentsUseCase → PaymentRepository
      │   └── CreatePaymentUseCase → PaymentRepository + PatientRepository + PaymentValidator
      ├── DashboardViewModel
      │   ├── DashboardRepository → PaymentDao + PatientDao
      │   └── GetDashboardMetricsUseCase → DashboardRepository
      ├── ExportViewModel
      │   └── ExportDataUseCase → ExportRepository + CsvExportService + FileStorageManager
      └── AuthenticationViewModel
          └── BiometricAuthManager → FragmentActivity
```

## Navigation Architecture

Single `NavHostController` with `AppNavGraph` and `AppBottomNavigation`:

```
Authentication (start destination)
  ├── BiometricEnrollment
  └── PINFallback

Bottom Navigation (5 tabs):
  ├── Pacientes  → PatientList → PatientDetail → PatientForm
  ├── Consultas  → AppointmentList(patientId) → AppointmentForm
  ├── Pagamentos → PaymentList(patientId) → PaymentForm
  ├── Dashboard  → DashboardScreen
  └── Exportar   → ExportScreen
```

The bottom navigation bar is hidden on auth screens, form screens, and
detail screens — shown only on root tab destinations.

## Encryption Architecture

Three-layer encryption hierarchy:

```
Layer 1: Android Keystore (hardware-backed)
  └── Master Key (AES-256, non-exportable, stored in TEE/StrongBox)

Layer 2: SecureKeyStore (DataStore + Tink)
  └── Database Key (AES-256, encrypted by Master Key, persisted in DataStore)

Layer 3: SQLCipher
  └── Database file encrypted with Database Key passphrase
```

- **Key rotation**: 90-day interval, 30-day grace period, Monday 2 AM window
- **Passphrase format**: `x'<64 hex chars>'` (256-bit key as hex string)
- **Auth tag**: 128-bit GCM authentication tag on every encryption operation
- **IV**: 12-byte random IV per encryption (never reused)

## Authentication Architecture

Two-tier biometric authentication:

| Tier | Purpose | Biometric Class | PIN Fallback | Timeout |
|------|---------|----------------|-------------|---------|
| Tier 1 | App access | Class 2 (weak) | Yes | 15 min session |
| Tier 2 | Per-operation | Class 3 (strong) | No | Per-operation |

Tier 2 is required for:
- Recording payments
- Exporting data

## Service Locator (AppModule)

Manual dependency injection via `AppModule` object:

- **Phase 1** (sync): `AppModule.initialize(context)` — called in `FinancialApp.onCreate()`
- **Phase 2** (suspend): `AppModule.initDatabase()` — called in `MainActivity.onCreate()` coroutine
- **Lazy singletons**: all repositories, use cases, validators
- **ViewModel factories**: `providePatientViewModel()`, `provideAppointmentViewModel()`, etc.

Migration path to Hilt: replace `AppModule` with `@Module @InstallIn(SingletonComponent::class)` and
ViewModels with `@HiltViewModel @Inject constructor`.

## Error Handling

- `ErrorHandler.getMessageForException()` maps exceptions to Portuguese user-facing messages
- `ErrorHandler.AppResult<T>` sealed class for type-safe success/error returns
- `ErrorHandler.safeCall()` wraps operations with automatic logging and error mapping
- `AppLogger` wraps `android.util.Log` with debug-only verbose logging and release-safe error logging
