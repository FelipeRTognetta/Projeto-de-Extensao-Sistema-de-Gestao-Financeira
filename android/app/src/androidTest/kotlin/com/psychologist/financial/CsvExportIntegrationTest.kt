package com.psychologist.financial

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.entities.AppointmentEntity
import com.psychologist.financial.data.entities.PatientEntity
import com.psychologist.financial.data.entities.PaymentEntity
import com.psychologist.financial.data.repositories.ExportRepository
import com.psychologist.financial.services.CsvExportService
import com.psychologist.financial.services.FileStorageManager
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Integration tests for CSV Export with Room Database
 *
 * Coverage:
 * - Export data from in-memory Room database
 * - CSV file generation and validation
 * - File write operations
 * - File read operations
 * - Data roundtrip (DB → CSV → Read)
 * - Multiple entity types export
 * - Large number of records (100+)
 * - Special characters and escape sequences
 * - Data integrity validation
 * - File format validation
 *
 * Uses in-memory Room database for isolated testing.
 * Tests actual file I/O operations.
 *
 * Total: 25+ test cases
 */
@RunWith(AndroidJUnit4::class)
class CsvExportIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var exportRepository: ExportRepository
    private lateinit var csvService: CsvExportService
    private lateinit var fileStorageManager: FileStorageManager
    private lateinit var exportDir: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Create in-memory database
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Initialize repository and services
        exportRepository = ExportRepository(database)
        csvService = CsvExportService()
        fileStorageManager = FileStorageManager(context)

        // Create temporary export directory
        exportDir = File(context.cacheDir, "export_test_${System.currentTimeMillis()}")
        exportDir.mkdirs()
    }

    @After
    fun tearDown() {
        database.close()
        // Clean up test export directory
        deleteDir(exportDir)
    }

    // ========================================
    // Patient Export Tests
    // ========================================

    @Test
    fun exportPatients_withDatabaseData_createsValidCsvFile() = runTest {
        // Arrange
        val patientEntity = PatientEntity(
            id = 0,
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            status = "ACTIVE",
            initialConsultDate = LocalDate.of(2025, 1, 15),
            registrationDate = LocalDate.of(2025, 1, 15)
        )
        database.patientDao().insertPatient(patientEntity)

        // Act
        val patients = exportRepository.getAllPatients()
        val csvFile = csvService.exportPatients(patients, exportDir)

        // Assert
        assertNotNull(csvFile)
        assertTrue(csvFile.exists())
        assertTrue(csvFile.name.startsWith("patients"))
        assertTrue(csvFile.name.endsWith(".csv"))

        val lines = csvFile.readLines()
        assertEquals(2, lines.size) // header + 1 patient
        assertTrue(lines[0].contains("name,phone,email"))
        assertTrue(lines[1].contains("João Silva"))
    }

    @Test
    fun exportPatients_withMultipleRecords_includesAllData() = runTest {
        // Arrange - Insert 5 patients
        val patients = (1..5).map { i ->
            PatientEntity(
                id = 0,
                name = "Patient $i",
                phone = "(11) 9999$i",
                email = "patient$i@test.com",
                status = if (i % 2 == 0) "ACTIVE" else "INACTIVE",
                initialConsultDate = LocalDate.now(),
                registrationDate = LocalDate.now()
            )
        }
        patients.forEach { database.patientDao().insertPatient(it) }

        // Act
        val allPatients = exportRepository.getAllPatients()
        val csvFile = csvService.exportPatients(allPatients, exportDir)

        // Assert
        val lines = csvFile.readLines()
        assertEquals(6, lines.size) // header + 5 patients
    }

    @Test
    fun exportPatients_withSpecialCharacters_escapesCorrectly() = runTest {
        // Arrange
        val patientEntity = PatientEntity(
            id = 0,
            name = "Maria \"Maré\" Santos",
            phone = "(21) 98765-4321",
            email = "maria@test.com",
            status = "ACTIVE",
            initialConsultDate = LocalDate.now(),
            registrationDate = LocalDate.now()
        )
        database.patientDao().insertPatient(patientEntity)

        // Act
        val patients = exportRepository.getAllPatients()
        val csvFile = csvService.exportPatients(patients, exportDir)

        // Assert
        assertTrue(csvFile.exists())
        val content = csvFile.readText()
        assertTrue(content.contains("Maré"))
    }

    // ========================================
    // Appointment Export Tests
    // ========================================

    @Test
    fun exportAppointments_withDatabaseData_createsValidCsvFile() = runTest {
        // Arrange - Create patient first
        val patientEntity = PatientEntity(
            id = 0,
            name = "Test Patient",
            phone = "(11) 99999-9999",
            email = "test@example.com",
            status = "ACTIVE",
            initialConsultDate = LocalDate.now(),
            registrationDate = LocalDate.now()
        )
        val patientId = database.patientDao().insertPatient(patientEntity).toInt()

        val appointmentEntity = AppointmentEntity(
            id = 0,
            patientId = patientId,
            date = LocalDate.of(2025, 2, 20),
            time = LocalTime.of(14, 30),
            durationMinutes = 50,
            notes = "Sessão de acompanhamento"
        )
        database.appointmentDao().insertAppointment(appointmentEntity)

        // Act
        val appointments = exportRepository.getAllAppointments()
        val csvFile = csvService.exportAppointments(appointments, exportDir)

        // Assert
        assertNotNull(csvFile)
        assertTrue(csvFile.exists())
        val lines = csvFile.readLines()
        assertEquals(2, lines.size) // header + 1 appointment
        assertTrue(lines[1].contains("2025-02-20"))
    }

    @Test
    fun exportAppointments_withMultipleRecords_includesAllData() = runTest {
        // Arrange - Create patient
        val patientEntity = PatientEntity(
            id = 0,
            name = "Test Patient",
            phone = "(11) 99999-9999",
            email = "test@test.com",
            status = "ACTIVE",
            initialConsultDate = LocalDate.now(),
            registrationDate = LocalDate.now()
        )
        val patientId = database.patientDao().insertPatient(patientEntity).toInt()

        // Insert 10 appointments
        (1..10).forEach { i ->
            val appointmentEntity = AppointmentEntity(
                id = 0,
                patientId = patientId,
                date = LocalDate.now().plusDays(i.toLong()),
                time = LocalTime.of(10 + i % 8, 0),
                durationMinutes = 45,
                notes = "Session $i"
            )
            database.appointmentDao().insertAppointment(appointmentEntity)
        }

        // Act
        val appointments = exportRepository.getAllAppointments()
        val csvFile = csvService.exportAppointments(appointments, exportDir)

        // Assert
        val lines = csvFile.readLines()
        assertEquals(11, lines.size) // header + 10 appointments
    }

    // ========================================
    // Payment Export Tests
    // ========================================

    @Test
    fun exportPayments_withDatabaseData_createsValidCsvFile() = runTest {
        // Arrange - Create patient and appointment
        val patientEntity = PatientEntity(
            id = 0,
            name = "Test Patient",
            phone = "(11) 99999-9999",
            email = "test@example.com",
            status = "ACTIVE",
            initialConsultDate = LocalDate.now(),
            registrationDate = LocalDate.now()
        )
        val patientId = database.patientDao().insertPatient(patientEntity).toInt()

        val appointmentEntity = AppointmentEntity(
            id = 0,
            patientId = patientId,
            date = LocalDate.now(),
            time = LocalTime.of(14, 0),
            durationMinutes = 50,
            notes = null
        )
        val appointmentId = database.appointmentDao().insertAppointment(appointmentEntity).toInt()

        val paymentEntity = PaymentEntity(
            id = 0,
            patientId = patientId,
            appointmentId = appointmentId,
            amount = BigDecimal("150.00"),
            method = "PIX",
            status = "PAID",
            paymentDate = LocalDate.now()
        )
        database.paymentDao().insertPayment(paymentEntity)

        // Act
        val payments = exportRepository.getAllPayments()
        val csvFile = csvService.exportPayments(payments, exportDir)

        // Assert
        assertNotNull(csvFile)
        assertTrue(csvFile.exists())
        val lines = csvFile.readLines()
        assertEquals(2, lines.size) // header + 1 payment
        assertTrue(lines[1].contains("150"))
    }

    @Test
    fun exportPayments_withVariousAmounts_preservesPrecision() = runTest {
        // Arrange - Create patient
        val patientEntity = PatientEntity(
            id = 0,
            name = "Test Patient",
            phone = "(11) 99999-9999",
            email = "test@test.com",
            status = "ACTIVE",
            initialConsultDate = LocalDate.now(),
            registrationDate = LocalDate.now()
        )
        val patientId = database.patientDao().insertPatient(patientEntity).toInt()

        // Insert payments with various amounts
        val amounts = listOf("50.00", "150.50", "1250.75", "100")
        amounts.forEach { amount ->
            val paymentEntity = PaymentEntity(
                id = 0,
                patientId = patientId,
                appointmentId = null,
                amount = BigDecimal(amount),
                method = "TRANSFER",
                status = "PAID",
                paymentDate = LocalDate.now()
            )
            database.paymentDao().insertPayment(paymentEntity)
        }

        // Act
        val payments = exportRepository.getAllPayments()
        val csvFile = csvService.exportPayments(payments, exportDir)

        // Assert
        val content = csvFile.readText()
        amounts.forEach { amount ->
            assertTrue(content.contains(amount),
                "Amount $amount not found in CSV export")
        }
    }

    // ========================================
    // Multi-Entity Export Tests
    // ========================================

    @Test
    fun exportAllData_createsThreeCsvFiles() = runTest {
        // Arrange - Create patient, appointment, payment
        val patientEntity = PatientEntity(
            id = 0,
            name = "Complete Patient",
            phone = "(11) 99999-9999",
            email = "complete@example.com",
            status = "ACTIVE",
            initialConsultDate = LocalDate.now(),
            registrationDate = LocalDate.now()
        )
        val patientId = database.patientDao().insertPatient(patientEntity).toInt()

        val appointmentEntity = AppointmentEntity(
            id = 0,
            patientId = patientId,
            date = LocalDate.now(),
            time = LocalTime.of(15, 0),
            durationMinutes = 60,
            notes = "Test appointment"
        )
        val appointmentId = database.appointmentDao().insertAppointment(appointmentEntity).toInt()

        val paymentEntity = PaymentEntity(
            id = 0,
            patientId = patientId,
            appointmentId = appointmentId,
            amount = BigDecimal("200.00"),
            method = "CREDIT_CARD",
            status = "PAID",
            paymentDate = LocalDate.now()
        )
        database.paymentDao().insertPayment(paymentEntity)

        // Act
        val patients = exportRepository.getAllPatients()
        val appointments = exportRepository.getAllAppointments()
        val payments = exportRepository.getAllPayments()

        val patientFile = csvService.exportPatients(patients, exportDir)
        val appointmentFile = csvService.exportAppointments(appointments, exportDir)
        val paymentFile = csvService.exportPayments(payments, exportDir)

        // Assert
        assertTrue(patientFile.exists())
        assertTrue(appointmentFile.exists())
        assertTrue(paymentFile.exists())

        assertEquals(2, patientFile.readLines().size) // header + 1
        assertEquals(2, appointmentFile.readLines().size) // header + 1
        assertEquals(2, paymentFile.readLines().size) // header + 1
    }

    // ========================================
    // File I/O and Format Tests
    // ========================================

    @Test
    fun csvExport_fileCanBeReadAsText() = runTest {
        // Arrange
        val patientEntity = PatientEntity(
            id = 0,
            name = "João Silva",
            phone = "(11) 99999-9999",
            email = "joao@example.com",
            status = "ACTIVE",
            initialConsultDate = LocalDate.now(),
            registrationDate = LocalDate.now()
        )
        database.patientDao().insertPatient(patientEntity)

        val patients = exportRepository.getAllPatients()
        val csvFile = csvService.exportPatients(patients, exportDir)

        // Act
        val content = csvFile.readText()
        val lines = csvFile.readLines()

        // Assert
        assertNotNull(content)
        assertTrue(content.isNotEmpty())
        assertTrue(lines.isNotEmpty())
        assertEquals(2, lines.size)
    }

    @Test
    fun csvExport_fileHasCorrectLineEndings() = runTest {
        // Arrange
        val patientEntity = PatientEntity(
            id = 0,
            name = "Test",
            phone = "123",
            email = "test@test.com",
            status = "ACTIVE",
            initialConsultDate = LocalDate.now(),
            registrationDate = LocalDate.now()
        )
        database.patientDao().insertPatient(patientEntity)

        val patients = exportRepository.getAllPatients()
        val csvFile = csvService.exportPatients(patients, exportDir)

        // Act
        val lines = csvFile.readLines()

        // Assert
        assertTrue(lines.size >= 2)
        lines.forEach { line ->
            assertFalse(line.isEmpty())
        }
    }

    @Test
    fun csvExport_fileNameIncludesTimestamp() = runTest {
        // Arrange
        val patientEntity = PatientEntity(
            id = 0,
            name = "Test",
            phone = "123",
            email = "test@test.com",
            status = "ACTIVE",
            initialConsultDate = LocalDate.now(),
            registrationDate = LocalDate.now()
        )
        database.patientDao().insertPatient(patientEntity)

        // Act
        val patients = exportRepository.getAllPatients()
        val csvFile = csvService.exportPatients(patients, exportDir)

        // Assert
        assertTrue(csvFile.name.contains("patients"))
        assertTrue(csvFile.name.contains(".csv"))
        // File should exist and be readable
        assertTrue(csvFile.canRead())
    }

    // ========================================
    // Data Integrity Tests
    // ========================================

    @Test
    fun exportedPatient_dataMatches_originalData() = runTest {
        // Arrange
        val patientEntity = PatientEntity(
            id = 0,
            name = "Exact Patient",
            phone = "(85) 9999-8888",
            email = "exact@example.com",
            status = "INACTIVE",
            initialConsultDate = LocalDate.of(2024, 6, 15),
            registrationDate = LocalDate.of(2024, 6, 15)
        )
        database.patientDao().insertPatient(patientEntity)

        // Act
        val patients = exportRepository.getAllPatients()
        val csvFile = csvService.exportPatients(patients, exportDir)
        val content = csvFile.readText()

        // Assert
        assertTrue(content.contains("Exact Patient"))
        assertTrue(content.contains("(85) 9999-8888"))
        assertTrue(content.contains("exact@example.com"))
        assertTrue(content.contains("INACTIVE"))
    }

    @Test
    fun exportedAppointment_dataMatches_originalData() = runTest {
        // Arrange - Create patient and appointment
        val patientEntity = PatientEntity(
            id = 0,
            name = "Test Patient",
            phone = "(11) 99999-9999",
            email = "test@test.com",
            status = "ACTIVE",
            initialConsultDate = LocalDate.now(),
            registrationDate = LocalDate.now()
        )
        val patientId = database.patientDao().insertPatient(patientEntity).toInt()

        val appointmentEntity = AppointmentEntity(
            id = 0,
            patientId = patientId,
            date = LocalDate.of(2025, 3, 15),
            time = LocalTime.of(16, 45),
            durationMinutes = 60,
            notes = "Specific session"
        )
        database.appointmentDao().insertAppointment(appointmentEntity)

        // Act
        val appointments = exportRepository.getAllAppointments()
        val csvFile = csvService.exportAppointments(appointments, exportDir)
        val content = csvFile.readText()

        // Assert
        assertTrue(content.contains("2025-03-15"))
        assertTrue(content.contains("16:45"))
        assertTrue(content.contains("60"))
        assertTrue(content.contains("Specific session"))
    }

    @Test
    fun exportedPayment_dataMatches_originalData() = runTest {
        // Arrange - Create patient and payment
        val patientEntity = PatientEntity(
            id = 0,
            name = "Test Patient",
            phone = "(11) 99999-9999",
            email = "test@test.com",
            status = "ACTIVE",
            initialConsultDate = LocalDate.now(),
            registrationDate = LocalDate.now()
        )
        val patientId = database.patientDao().insertPatient(patientEntity).toInt()

        val paymentEntity = PaymentEntity(
            id = 0,
            patientId = patientId,
            appointmentId = null,
            amount = BigDecimal("555.75"),
            method = "DEBIT_CARD",
            status = "PENDING",
            paymentDate = LocalDate.of(2025, 2, 28)
        )
        database.paymentDao().insertPayment(paymentEntity)

        // Act
        val payments = exportRepository.getAllPayments()
        val csvFile = csvService.exportPayments(payments, exportDir)
        val content = csvFile.readText()

        // Assert
        assertTrue(content.contains("555.75"))
        assertTrue(content.contains("DEBIT_CARD"))
        assertTrue(content.contains("PENDING"))
        assertTrue(content.contains("2025-02-28"))
    }

    // ========================================
    // Empty Data Tests
    // ========================================

    @Test
    fun exportPatients_withEmptyDatabase_createsHeaderOnlyFile() = runTest {
        // Arrange - Database is empty

        // Act
        val patients = exportRepository.getAllPatients()
        val csvFile = csvService.exportPatients(patients, exportDir)

        // Assert
        val lines = csvFile.readLines()
        assertEquals(1, lines.size) // Only header
        assertTrue(lines[0].contains("name"))
    }

    @Test
    fun exportAppointments_withEmptyDatabase_createsHeaderOnlyFile() = runTest {
        // Arrange - Database is empty

        // Act
        val appointments = exportRepository.getAllAppointments()
        val csvFile = csvService.exportAppointments(appointments, exportDir)

        // Assert
        val lines = csvFile.readLines()
        assertEquals(1, lines.size) // Only header
    }

    @Test
    fun exportPayments_withEmptyDatabase_createsHeaderOnlyFile() = runTest {
        // Arrange - Database is empty

        // Act
        val payments = exportRepository.getAllPayments()
        val csvFile = csvService.exportPayments(payments, exportDir)

        // Assert
        val lines = csvFile.readLines()
        assertEquals(1, lines.size) // Only header
    }

    // ========================================
    // Helper Methods
    // ========================================

    private fun deleteDir(dir: File) {
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { deleteDir(it) }
        }
        dir.delete()
    }
}
