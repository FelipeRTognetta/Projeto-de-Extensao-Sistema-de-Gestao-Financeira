# Changelog — Financial Management System for Psychologists

All notable changes to this project are documented here.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
Versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] — 2026-02-27

### Added

#### Core Features
- **Patient Registration & Management** (US1)
  - Register new patients with name, phone, email, and initial consultation date
  - View full patient profile with appointment and payment summary
  - Edit patient information
  - Active/Inactive status display and filtering

- **Appointment Logging** (US2)
  - Create appointments with date, time, duration (5–480 min), and optional notes
  - View chronological appointment history per patient
  - Billable hours summary (total sessions + total hours per patient)
  - Appointments are immutable after creation

- **Payment Control & Tracking** (US3)
  - Record payments with amount (BigDecimal), date, payment method, and status (PAID/PENDING)
  - Link payments to appointments (optional)
  - "Amount Due Now" calculation (PAID payments only)
  - "Total Outstanding" calculation (includes PENDING)
  - Filter payment history by date range and status
  - Payments are immutable after creation

- **Financial Dashboard & Reporting** (US4)
  - Monthly revenue summary (total revenue, active patient count, average fee, outstanding balance)
  - Month selector to filter all dashboard metrics
  - Weekly revenue breakdown

- **Patient Status Management** (US5)
  - Mark patients as Inactive with confirmation dialog
  - Reactivate Inactive patients
  - Filter patient list by ACTIVE / INACTIVE status
  - Read-only enforcement: Inactive patients cannot have new appointments or payments
  - Visual "Inactive" badge on patient cards

- **Data Export & Backup** (US6)
  - CSV export for patients, appointments, and payments (three files)
  - Streaming export supports 500+ records without memory issues
  - Exported files compatible with spreadsheet applications (Excel, LibreOffice Calc)

#### Security
- Two-tier biometric authentication:
  - App-level: BiometricPrompt with 15-minute session timeout (PIN fallback)
  - Per-operation: Class 3 biometric required for payments and CSV export
- Three-layer encryption at rest:
  - Android Keystore (hardware-backed TEE/StrongBox) → key generation and storage
  - SecureKeyStore (Tink + DataStore) → encrypted key persistence
  - SQLCipher (AES-256-GCM) → database encryption
- 90-day automatic key rotation with 30-day grace period
- No sensitive data in production logs
- Input validation at every system boundary

#### Architecture & Infrastructure
- MVVM architecture with Use Case layer
- Jetpack Compose + Material 3 UI
- Room ORM + SQLCipher encrypted database
- Compose Navigation with Bottom Navigation Bar (Patients, Appointments, Payments, Dashboard, Export)
- Manual service locator (AppModule) — Hilt-ready structure
- Standardized error handling (ErrorHandler) with Portuguese user-facing messages
- Structured logging (AppLogger) with security event channel

#### Testing
- 46 test files (19 unit + 27 instrumented)
- 485+ test cases
- Critical modules (auth, encryption, calculations) exceed 85% estimated coverage
- JaCoCo coverage task: `./gradlew testCoverage`

#### Documentation
- `docs/ARCHITECTURE.md` — MVVM pattern, data flow, dependency graph
- `docs/API.md` — public API reference (Repositories, UseCases, Services)
- `docs/TESTING.md` — test strategy, coverage goals, commands
- `docs/CODE_REVIEW.md` — review guidelines and checklist
- `docs/CONSTITUTION_COMPLIANCE.md` — compliance verification for 4 core principles
- `docs/COVERAGE.md` — coverage report and gap analysis

### Known Limitations

- **No cloud synchronization**: Data is stored locally only; users must manually back up CSV exports
- **Single user**: Application is designed for one psychologist on one device
- **No notifications or reminders**: Appointment reminders are not implemented in v1.0
- **No recurring appointments**: Each appointment must be created individually
- **No multi-currency**: All monetary values are in a single implicit currency (BRL assumed)
- **No automatic backup**: Manual CSV export is the only backup mechanism
- **Android only**: iOS and web versions are not available
- **Biometric enrollment required**: App requires biometric enrollment; devices without biometrics cannot use per-operation auth
- **Instrumented tests require device/emulator**: Integration and UI tests cannot run in CI without Android environment
- **Release APK requires signing configuration**: `assembleRelease` requires a keystore; see release setup below

### Technical Debt / Future Work

- Migrate from manual service locator (AppModule) to Hilt for full DI support
- Add cloud backup option (Google Drive or encrypted remote storage)
- Implement appointment reminders via WorkManager + NotificationManager
- Add recurring appointment templates
- Improve JaCoCo coverage to authoritative 75%+ (currently estimated at ~60–65% without full build environment)

---

## Release Setup Notes

### Signing the Release APK

To build a signed release APK, configure `android/app/build.gradle.kts`:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
```

Then build:

```bash
cd android
KEYSTORE_PATH=/path/to/keystore.jks \
KEYSTORE_PASSWORD=<password> \
KEY_ALIAS=<alias> \
KEY_PASSWORD=<key-password> \
./gradlew assembleRelease
```

Output: `android/app/build/outputs/apk/release/app-release.apk`

---

[1.0.0]: https://github.com/psychologist/financial-management/releases/tag/v1.0.0
