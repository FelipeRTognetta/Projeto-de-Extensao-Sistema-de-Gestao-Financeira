# Financial Management System for Psychologists

An Android mobile application for financial management built with Kotlin, designed specifically for psychologists to track patient registrations, appointment sessions, payment tracking, and financial reporting with complete privacy and security.

## Project Overview

**Type**: Android Mobile Application  
**Language**: Kotlin (Android API 30+)  
**Architecture**: MVVM with Clean Architecture layers  
**Database**: Room ORM + SQLCipher (encrypted local storage)  
**UI Framework**: Jetpack Compose + Material 3  
**Status**: In Development (Phase 1.1-1.2 Complete)

### Key Features

- **Patient Registration & Management**: Register patients with contact details, track status (Active/Inactive)
- **Appointment Logging**: Log sessions with date, time, duration, and notes; auto-calculate billable hours
- **Payment Control**: Record payments with status tracking (Paid/Pending), optional appointment linking
- **Financial Dashboard**: Real-time metrics (revenue, patient count, outstanding balance, monthly breakdown)
- **Patient Status Management**: Soft-delete via status flags, read-only enforcement for inactive patients
- **Data Export**: CSV export for backup (3 separate files: patients, appointments, payments)
- **Biometric Authentication**: Fingerprint/Face recognition with PIN fallback (15-minute session timeout)
- **Data Encryption**: SQLCipher database encryption + Android Keystore hardware-backed key storage

## Technical Stack

| Component           | Technology                              | Version              |
| ------------------- | --------------------------------------- | -------------------- |
| Language            | Kotlin                                  | 1.9.22               |
| Min SDK             | Android 11 (API 30)                     |                      |
| Target SDK          | Android 15 (API 35)                     | Latest               |
| UI Framework        | Jetpack Compose                         | 2024.02.00           |
| Database            | Room ORM                                | 2.6.1                |
| Database Encryption | SQLCipher                               | 4.5.4                |
| Security            | BiometricPrompt, Tink, Android Keystore | androidx, 1.10.0     |
| Build System        | Gradle                                  | 8.3.0                |
| Testing             | JUnit, Mockito, Espresso                | 4.13.2, 5.7.0, 3.5.1 |
| Code Quality        | JaCoCo                                  | 0.8.10               |

## Prerequisites

- **Android Studio**: 2024.2+
- **Android SDK**: API 30+ (minimum), API 35+ (recommended)
- **JDK**: OpenJDK 17+
- **Device/Emulator**: Android 11+ (API 30+)

## Project Setup

### 1. Clone Repository

```bash
git clone <repository-url>
cd Projeto-de-Extensao-Sistema-de-Gestao-Financeira
```

### 2. Open in Android Studio

1. Open Android Studio
2. File → Open → Select the `android/` directory
3. Android Studio will auto-detect and configure the project

### 3. Sync Gradle

```bash
./gradlew clean
./gradlew build
```

Or use Android Studio: Sync Now when prompted.

### 4. Run on Device/Emulator

```bash
./gradlew installDebug
```

Or use Android Studio: Run → Run 'app'

## Build Commands

### Development

```bash
# Clean and build debug APK
./gradlew clean build

# Assemble debug APK (for manual testing)
./gradlew assembleDebug

# Run on connected device/emulator
./gradlew installDebug
```

### Testing

```bash
# Run all unit tests
./gradlew test

# Generate code coverage report (JaCoCo)
./gradlew testCoverage

# View report
open android/app/build/reports/jacoco/test/html/index.html
```

### Release

```bash
# Build release APK (requires signing configuration)
./gradlew assembleRelease
```

## Testing Strategy

### Coverage Targets

- **Overall**: 75% (across all modules)
- **Critical Modules**: 85% minimum (auth, encryption, financial calculations)

### Running Tests

```bash
./gradlew test              # Unit tests
./gradlew connectedAndroidTest  # Instrumented tests
./gradlew testCoverage      # Coverage report
```

## Security & Privacy

### Authentication

- **App-Level**: Biometric with 15-minute session timeout + PIN fallback
- **Per-Operation**: Biometric required for payments, exports

### Encryption

- **Database**: SQLCipher with AES-256-GCM encryption
- **Keys**: Android Keystore hardware-backed storage (TEE/StrongBox)
- **Configuration**: Tink + DataStore for sensitive keys

### Privacy

- **Local-Only**: No cloud services, all data stored locally
- **No External APIs**: No third-party integrations
- **Single-User**: App designed for single psychologist use

**Last Updated**: 2026-02-25  
**Phase**: 1.1-1.2 (Project Setup & Android Configuration)  
**Status**: In Development
