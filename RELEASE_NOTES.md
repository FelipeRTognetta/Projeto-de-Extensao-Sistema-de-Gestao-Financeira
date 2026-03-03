# Release Notes — Financial Management System for Psychologists

**Version**: 1.0.0
**Release Date**: 2026-02-27
**Package**: `com.psychologist.financial`
**Minimum Android**: 11 (API 30)
**Target Android**: 15 (API 35)

---

## App Store Description

### Short Description (80 characters)

Gestão financeira segura para psicólogos — pacientes, consultas e pagamentos.

### Full Description

**Sistema de Gestão Financeira para Psicólogos** é um aplicativo Android projetado exclusivamente para psicólogos autônomos que precisam gerenciar pacientes, consultas e pagamentos de forma segura e eficiente, diretamente no celular — sem internet, sem nuvem, sem compartilhamento de dados.

#### O que você pode fazer:

**Pacientes**
Cadastre e gerencie sua carteira de pacientes. Visualize o histórico completo de consultas e pagamentos de cada paciente. Marque pacientes como Inativos quando necessário — os dados históricos são preservados no modo somente leitura.

**Consultas**
Registre consultas com data, horário, duração e anotações. Acompanhe o total de horas faturáveis por paciente ao longo do tempo.

**Pagamentos**
Controle entradas com precisão. Registre pagamentos por método (dinheiro, Pix, cartão etc.) e acompanhe o status (Pago / Pendente). Visualize o saldo devedor imediato e o total em aberto de cada paciente.

**Dashboard Financeiro**
Acesse métricas mensais consolidadas: receita total, número de pacientes ativos, honorário médio e saldo devedor total. Selecione qualquer mês para ver o desempenho histórico com detalhamento semanal.

**Exportação de Dados**
Exporte todos os dados (pacientes, consultas e pagamentos) em formato CSV para backup ou análise em planilhas (Excel, LibreOffice Calc).

#### Segurança de ponta a ponta:

- **Autenticação biométrica em dois níveis**: desbloqueio por biometria/PIN na abertura (sessão de 15 min) + confirmação biométrica para pagamentos e exportações
- **Banco de dados totalmente criptografado**: SQLCipher com AES-256-GCM
- **Chaves no hardware do dispositivo**: Android Keystore (TEE/StrongBox)
- **Rotação automática de chaves**: a cada 90 dias, com período de carência de 30 dias
- **Sem acesso à internet**: seus dados nunca saem do dispositivo

---

## Feature Summary (English)

### v1.0.0 Features

| Feature | Description |
|---------|-------------|
| Patient Management | Register, view, edit, and filter patients (Active/Inactive) |
| Appointment Logging | Create appointments with date, time, duration, notes; track billable hours |
| Payment Tracking | Record payments (PAID/PENDING), link to appointments, view balances |
| Financial Dashboard | Monthly metrics: revenue, patient count, average fee, outstanding balance |
| Status Management | Mark patients Inactive (read-only), reactivate, filter by status |
| CSV Data Export | Export patients, appointments, payments to three CSV files |
| Biometric Auth | Two-tier auth: app-level session (15 min) + per-operation for payments |
| Encrypted Storage | SQLCipher + Android Keystore + Tink — AES-256-GCM at rest |

---

## Compatibility

| Attribute | Value |
|-----------|-------|
| Minimum Android version | Android 11 (API 30) |
| Target Android version | Android 15 (API 35) |
| Architecture | arm64-v8a, x86_64 |
| Screen sizes | Phone (4.5"+), Tablet compatible |
| Biometrics | Class 2 (app unlock) + Class 3 (per-operation) |
| Offline | Fully offline — no internet permission in release |

---

## QA Test Matrix

### Tested Android Versions

| API | Version | Result |
|-----|---------|--------|
| 30 | Android 11 | Pending device testing |
| 33 | Android 13 | Pending device testing |
| 35 | Android 15 | Pending device testing |

### Critical Test Scenarios

| # | Scenario | Steps | Expected |
|---|----------|-------|----------|
| 1 | App launch + biometric auth | Open app → authenticate | Dashboard visible after auth |
| 2 | Register patient | Patients → Add → fill form → Save | Patient appears in list |
| 3 | Invalid patient form | Submit empty name | Validation error shown in Portuguese |
| 4 | Create appointment | Patient → Appointments → Add → fill form → Save | Appointment in chronological list |
| 5 | Record payment (PAID) | Patient → Payments → Add → amount + PAID → Save | Balance updated immediately |
| 6 | Record payment (PENDING) | Patient → Payments → Add → amount + PENDING → Save | Outstanding balance updated |
| 7 | Mark patient Inactive | Patient detail → Mark Inactive → Confirm | Patient moves to Inactive list; Add buttons disabled |
| 8 | Inactive patient restrictions | Inactive patient → try Add Appointment | Error: patient is inactive |
| 9 | Dashboard month filter | Dashboard → select previous month | Metrics update to selected month |
| 10 | CSV Export | Export screen → Export → confirm biometric | Three CSV files created; success message shown |
| 11 | Session timeout | Authenticate → wait 15 min → navigate | Re-authentication prompt appears |
| 12 | Data persistence | Register patient → force-stop app → reopen | Patient data still present |
| 13 | Per-operation auth | Record payment → biometric prompt | Payment only saved after successful auth |
| 14 | App restart after key rotation | Simulate 90+ day key rotation → reopen | Database decrypts successfully; no data loss |
| 15 | Large dataset | 500 patients + 5000 appointments → scroll list | List responsive; no ANR |

---

## Build Information

| Attribute | Value |
|-----------|-------|
| Build tool | Android Gradle Plugin 8.x |
| Kotlin version | 1.9.x (JVM 17) |
| Compose BOM | 2024.02.00 |
| Room version | 2.6.1 |
| SQLCipher version | 4.5.4 |
| Tink version | 1.10.0 |
| minification | ProGuard (release builds) |
| Coverage tool | JaCoCo 0.8.10 |

### Build Commands

```bash
# Debug build (no signing required)
cd android
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Release build (requires signing config — see CHANGELOG.md)
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk

# Run unit tests
./gradlew testDebugUnitTest

# Generate coverage report
./gradlew testCoverage
# Report: app/build/reports/jacoco/test/html/index.html
```

---

## Known Issues in v1.0.0

1. **No cloud backup**: Data export to CSV is the only backup mechanism. Users should export regularly and store files in a safe location.
2. **No reminders**: The app does not send appointment reminders or payment notifications.
3. **Single device**: Data is not synchronized across devices.
4. **Biometric requirement**: Per-operation auth (payments, export) requires Class 3 biometric (fingerprint sensor). Devices with only face unlock may fall back to PIN, which is not available for per-operation auth (by design — see `PerOperationAuthManager`).
5. **First-run setup**: Users without biometric enrollment are redirected to system settings to enroll before using the app.

---

## Privacy & Data Handling

- No data is collected, transmitted, or shared with third parties
- All data remains on the device in an encrypted SQLite database
- No analytics, crash reporting, or telemetry is included
- No internet permission is declared in the release manifest
- Users control their data entirely through the in-app CSV export feature
