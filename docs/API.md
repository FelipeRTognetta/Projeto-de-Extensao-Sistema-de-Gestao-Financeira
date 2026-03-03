# API Reference — Financial Management System

Public APIs exposed by Repositories, Use Cases, and Services.

---

## Repositories

### PatientRepository

| Method | Signature | Returns | Description |
|--------|----------|---------|-------------|
| `createPatient` | `suspend (Patient) → Long` | Generated ID | Insert new patient; validates phone/email uniqueness |
| `getPatient` | `suspend (id: Long) → Patient?` | Patient or null | Fetch single patient by ID |
| `getAllPatients` | `suspend (includeInactive: Boolean) → List<Patient>` | Patient list | All patients; default active-only |
| `getAllPatientsFlow` | `(includeInactive: Boolean) → Flow<List<Patient>>` | Reactive stream | Observe patient list changes |
| `updatePatient` | `suspend (Patient) → Unit` | — | Update existing patient fields |
| `markInactive` | `suspend (id: Long) → Unit` | — | Set status to INACTIVE |
| `reactivate` | `suspend (id: Long) → Unit` | — | Set status to ACTIVE |
| `deletePatient` | `suspend (id: Long) → Unit` | — | Permanent deletion (use cautiously) |

### AppointmentRepository

| Method | Signature | Returns | Description |
|--------|----------|---------|-------------|
| `insertAppointment` | `suspend (patientId, date, time, duration, notes?) → Long` | Generated ID | Create new appointment |
| `getPatientAppointments` | `suspend (patientId: Long) → List<Appointment>` | Appointment list | All appointments for patient |
| `getAppointmentById` | `suspend (id: Long) → Appointment?` | Appointment or null | Fetch single appointment |
| `getUpcomingAppointments` | `suspend (patientId: Long) → List<Appointment>` | Future appointments | Date >= today |
| `getPastAppointments` | `suspend (patientId: Long) → List<Appointment>` | Past appointments | Date < today |
| `getAppointmentsByDateRange` | `suspend (patientId, start, end) → List<Appointment>` | Filtered list | Date range query |

### PaymentRepository

| Method | Signature | Returns | Description |
|--------|----------|---------|-------------|
| `createPayment` | `suspend (patientId, appointmentId?, amount, status, method, date) → Long` | Generated ID | Record new payment |
| `getPatientPayments` | `suspend (patientId: Long) → List<Payment>` | Payment list | All payments for patient |
| `getPaidPayments` | `suspend (patientId: Long) → List<Payment>` | Paid only | Status = PAID |
| `getPendingPayments` | `suspend (patientId: Long) → List<Payment>` | Pending only | Status = PENDING |
| `getOverduePayments` | `suspend (patientId: Long) → List<Payment>` | Overdue pending | PENDING with past date |
| `getPaymentsByDateRange` | `suspend (patientId, start, end) → List<Payment>` | Filtered list | Date range query |
| `markAsPaid` | `suspend (paymentId: Long) → Unit` | — | Change status to PAID |
| `markAsPending` | `suspend (paymentId: Long) → Unit` | — | Change status to PENDING |
| `deletePayment` | `suspend (paymentId: Long) → Unit` | — | Remove payment record |

### DashboardRepository

| Method | Signature | Returns | Description |
|--------|----------|---------|-------------|
| `getMetricsByMonth` | `(yearMonth: YearMonth) → DashboardMetrics` | Aggregated metrics | Revenue, appointment count, patient count |
| `getMonthlyTrend` | `(months: Int) → List<MonthlyMetrics>` | Trend data | Last N months comparison |

### ExportRepository

| Method | Signature | Returns | Description |
|--------|----------|---------|-------------|
| `getAllPatientsForExport` | `suspend () → List<PatientEntity>` | Raw entities | Full patient dump |
| `getAllAppointmentsForExport` | `suspend () → List<AppointmentEntity>` | Raw entities | Full appointment dump |
| `getAllPaymentsForExport` | `suspend () → List<PaymentEntity>` | Raw entities | Full payment dump |
| `getExportStatistics` | `suspend () → Map<String, Int>` | Record counts | Patient/appointment/payment counts |

---

## Use Cases

### Patient Use Cases

| Use Case | Input | Output | Business Rules |
|----------|-------|--------|----------------|
| `GetAllPatientsUseCase` | `includeInactive: Boolean` | `List<Patient>` | Default: active only |
| `CreatePatientUseCase` | `name, phone, email?, date?` | `Long` (ID) | Validates name length, phone format, email format, uniqueness |
| `MarkPatientInactiveUseCase` | `patientId: Long` | `Unit` | Patient must exist and be ACTIVE |
| `ReactivatePatientUseCase` | `patientId: Long` | `Unit` | Patient must exist and be INACTIVE |

### Appointment Use Cases

| Use Case | Input | Output | Business Rules |
|----------|-------|--------|----------------|
| `GetPatientAppointmentsUseCase` | `patientId: Long` | `List<Appointment>` | Sorted by date descending |
| `CreateAppointmentUseCase` | `patientId, date, time, duration, notes?` | `Long` (ID) | Duration 5–480 min, date validation |

### Payment Use Cases

| Use Case | Input | Output | Business Rules |
|----------|-------|--------|----------------|
| `GetPatientPaymentsUseCase` | `patientId: Long` | `List<Payment>` | Sorted by date descending |
| `CreatePaymentUseCase` | `patientId, amount, method, status, date` | `Long` (ID) | Patient must be ACTIVE, amount > 0, BigDecimal precision |

### Dashboard Use Cases

| Use Case | Input | Output | Business Rules |
|----------|-------|--------|----------------|
| `GetDashboardMetricsUseCase` | `yearMonth: YearMonth?` | `DashboardMetrics` | Default: current month |

### Export Use Cases

| Use Case | Input | Output | Business Rules |
|----------|-------|--------|----------------|
| `ExportDataUseCase` | `exportType, dateRange?` | `ExportResult` | Requires per-operation biometric auth, min 50 MB storage |

---

## Services

### EncryptionService

| Method | Signature | Description |
|--------|----------|-------------|
| `generateMasterKey` | `(alias, requiresUserAuth?) → EncryptionKey` | Create AES-256 key in Android Keystore |
| `generateDatabaseKey` | `(alias) → EncryptionKey` | Create random 32-byte database key |
| `encrypt` | `(plaintext: ByteArray, keyAlias: String) → ByteArray` | AES-256-GCM encrypt (IV + ciphertext + auth tag) |
| `decrypt` | `(encryptedData: ByteArray, keyAlias: String) → ByteArray` | AES-256-GCM decrypt with GCM verification |
| `keyExists` | `(alias: String) → Boolean` | Check if key exists in Keystore |
| `deleteKey` | `(alias: String) → Boolean` | Remove key from Keystore |

### SecureKeyStore

| Method | Signature | Description |
|--------|----------|-------------|
| `storeDatabaseKey` | `suspend (EncryptionKey) → Boolean` | Persist encrypted key in DataStore |
| `getDatabaseKey` | `suspend () → EncryptionKey?` | Retrieve and decrypt database key |
| `hasDatabaseKey` | `suspend () → Boolean` | Check key existence |
| `deleteDatabaseKey` | `suspend () → Boolean` | Remove database key |
| `getKeyInventory` | `suspend () → Map<KeyPurpose, EncryptionKey?>` | All managed keys |

### DatabaseEncryptionManager

| Method | Signature | Description |
|--------|----------|-------------|
| `getDatabasePassphrase` | `suspend () → String` | Get SQLCipher hex passphrase (`x'...'`) |
| `initializeDatabaseKey` | `suspend () → EncryptionKey` | Create or retrieve database encryption key |
| `verifyDatabaseEncryption` | `suspend () → Boolean` | Verify encryption is active |
| `getEncryptionStatus` | `suspend () → Map<String, Any>` | Full encryption status report |

### BiometricAuthManager

| Method | Signature | Description |
|--------|----------|-------------|
| `isBiometricAvailable` | `() → Boolean` | Device supports biometrics |
| `isBiometricEnrolled` | `() → Boolean` | User has enrolled biometrics |
| `authenticate` | `(callback) → Unit` | Show BiometricPrompt (Class 2) |
| `isSessionValid` | `() → Boolean` | Within 15-min session window |
| `getBiometricStatus` | `() → Map<String, Any>` | Detailed capability report |

### SessionManager

| Method | Signature | Description |
|--------|----------|-------------|
| `startSession` | `() → Unit` | Begin 15-min authenticated session |
| `extendSession` | `() → Boolean` | Reset session timeout |
| `expireSession` | `(reason: String) → Unit` | Force session expiration |
| `clearSession` | `() → Unit` | Logout (clear session) |
| `isSessionValid` | `() → Boolean` | Check session hasn't expired |
| `requireBiometricForOperation` | `(OperationType, reason) → Unit` | Trigger per-operation auth |

### KeyRotationService

| Method | Signature | Description |
|--------|----------|-------------|
| `isRotationDue` | `suspend () → Boolean` | Check if key rotation is needed |
| `performRotation` | `suspend () → Boolean` | Execute key rotation (6-step process) |
| `getRotationStatus` | `suspend () → Map<String, Any>` | Current rotation state |
| `getPolicy` | `() → KeyRotationPolicy` | Active rotation policy |

---

## Domain Models

| Model | Key Properties |
|-------|---------------|
| `Patient` | id, name, phone, email, status, initialConsultDate, registrationDate |
| `Appointment` | id, patientId, date, time, durationMinutes, notes, createdDate |
| `Payment` | id, patientId, appointmentId?, amount (BigDecimal), paymentDate, method, status |
| `DashboardMetrics` | totalRevenue, appointmentCount, activePatientCount, pendingPayments |
| `PatientBalance` | totalDue, totalPaid, pendingAmount, overdueAmount |
| `BillableHoursSummary` | totalHours, totalSessions, averageDuration |
| `EncryptionKey` | alias, keyMaterial, algorithm, keySize, createdAt, expiresAt, purpose |
| `KeyRotationPolicy` | rotationIntervalDays, warningThresholdDays, gracePeriodDays |
| `SessionState` | Authenticated / Expired / BiometricRequired / Unauthenticated |
| `ExportResult` | filePath, recordCount, exportDate, format |
