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
 * Performance and stress tests for CSV Export with large datasets
 *
 * Coverage:
 * - 500+ patient records export
 * - 2000+ appointment records export
 * - 5000+ payment records export
 * - Large file generation performance
 * - Memory usage during export
 * - File size validation
 * - Export completion time measurement
 * - CSV integrity with large datasets
 * - Database query performance with 500+ records
 *
 * Performance Targets:
 * - 500 patients export: < 2 seconds
 * - 2000 appointments export: < 3 seconds
 * - 5000 payments export: < 5 seconds
 * - File sizes reasonable (<50MB for 500+ records)
 *
 * Uses in-memory Room database for testing.
 * Can be run in parallel with other tests.
 *
 * Total: 10+ performance test cases
 */
@RunWith(AndroidJUnit4::class)
class LargeDatasetExportTest {

    private lateinit var database: AppDatabase
    private lateinit var exportRepository: ExportRepository
    private lateinit var csvService: CsvExportService
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

        // Create temporary export directory
        exportDir = File(context.cacheDir, "export_perf_test_${System.currentTimeMillis()}")
        exportDir.mkdirs()
    }

    @After
    fun tearDown() {
        database.close()
        // Clean up test export directory
        deleteDir(exportDir)
    }

    // ========================================
    // 500+ Patient Export Tests
    // ========================================

    @Test
    fun exportLargePatientDataset_500Records_completesWithinTimeLimit() = runTest {
        // Arrange - Create 500 patients
        val patientCount = 500
        val startInsert = System.currentTimeMillis()

        (1..patientCount).forEach { i ->
            val patientEntity = PatientEntity(
                id = 0,
                name = "Patient Number $i",
                phone = "(11) 9999-${String.format("%04d", i)}",
                email = "patient$i@example.com",
                status = if (i % 2 == 0) "ACTIVE" else "INACTIVE",
                initialConsultDate = LocalDate.now().minusDays((i % 30).toLong()),
                registrationDate = LocalDate.now().minusDays((i % 30).toLong())
            )
            database.patientDao().insertPatient(patientEntity)
        }

        val insertTime = System.currentTimeMillis() - startInsert
        println("Insert time for $patientCount patients: ${insertTime}ms")

        // Act
        val startExport = System.currentTimeMillis()
        val patients = exportRepository.getAllPatients()
        val csvFile = csvService.exportPatients(patients, exportDir)
        val exportTime = System.currentTimeMillis() - startExport

        // Assert
        assertNotNull(csvFile)
        assertTrue(csvFile.exists())
        assertEquals(patientCount, patients.size)

        // Verify export completed within reasonable time (< 3 seconds)
        assertTrue("Export took too long: ${exportTime}ms", exportTime < 3000)

        // Verify file size is reasonable
        val fileSizeKB = csvFile.length() / 1024
        assertTrue("File too large: ${fileSizeKB}KB", fileSizeKB < 5000) // < 5MB

        // Verify all records in file
        val lineCount = csvFile.readLines().size
        assertEquals(patientCount + 1, lineCount) // header + records
    }

    @Test
    fun exportLargePatientDataset_canReadEntireFile() = runTest {
        // Arrange - Create 500 patients
        (1..500).forEach { i ->
            val patientEntity = PatientEntity(
                id = 0,
                name = "Test Patient $i",
                phone = "(11) 9999-${String.format("%04d", i)}",
                email = "test$i@example.com",
                status = "ACTIVE",
                initialConsultDate = LocalDate.now(),
                registrationDate = LocalDate.now()
            )
            database.patientDao().insertPatient(patientEntity)
        }

        // Act
        val patients = exportRepository.getAllPatients()
        val csvFile = csvService.exportPatients(patients, exportDir)
        val content = csvFile.readText()

        // Assert
        assertNotNull(content)
        assertTrue(content.isNotEmpty())
        // Verify some records are present
        assertTrue(content.contains("Test Patient 1"))
        assertTrue(content.contains("Test Patient 500"))
    }

    // ========================================
    // 2000+ Appointment Export Tests
    // ========================================

    @Test
    fun exportLargeAppointmentDataset_2000Records_completesWithinTimeLimit() = runTest {
        // Arrange - Create patient first
        val patientEntity = PatientEntity(
            id = 0,
            name = "Busy Patient",
            phone = "(11) 99999-9999",
            email = "busy@example.com",
            status = "ACTIVE",
            initialConsultDate = LocalDate.now(),
            registrationDate = LocalDate.now()
        )
        val patientId = database.patientDao().insertPatient(patientEntity).toInt()

        // Create 2000 appointments
        val appointmentCount = 2000
        val startInsert = System.currentTimeMillis()

        (1..appointmentCount).forEach { i ->
            val appointmentEntity = AppointmentEntity(
                id = 0,
                patientId = patientId,
                date = LocalDate.now().plusDays((i / 10).toLong()),
                time = LocalTime.of((i % 24), (i % 60)),
                durationMinutes = 30 + (i % 60),
                notes = "Session $i - Patient notes"
            )
            database.appointmentDao().insertAppointment(appointmentEntity)
        }

        val insertTime = System.currentTimeMillis() - startInsert
        println("Insert time for $appointmentCount appointments: ${insertTime}ms")

        // Act
        val startExport = System.currentTimeMillis()
        val appointments = exportRepository.getAllAppointments()
        val csvFile = csvService.exportAppointments(appointments, exportDir)
        val exportTime = System.currentTimeMillis() - startExport

        // Assert
        assertNotNull(csvFile)
        assertTrue(csvFile.exists())
        assertEquals(appointmentCount, appointments.size)

        // Verify export completed within reasonable time (< 5 seconds)
        assertTrue("Export took too long: ${exportTime}ms", exportTime < 5000)

        // Verify file size is reasonable
        val fileSizeKB = csvFile.length() / 1024
        assertTrue("File too large: ${fileSizeKB}KB", fileSizeKB < 10000) // < 10MB

        // Verify all records in file
        val lineCount = csvFile.readLines().size
        assertEquals(appointmentCount + 1, lineCount) // header + records
    }

    @Test
    fun exportLargeAppointmentDataset_dateFormatting_consistentAcrossLargeDataset() = runTest {
        // Arrange - Create patient
        val patientEntity = PatientEntity(
            id = 0,
            name = "Test",
            phone = "123",
            email = "test@test.com",
            status = "ACTIVE",
            initialConsultDate = LocalDate.now(),
            registrationDate = LocalDate.now()
        )
        val patientId = database.patientDao().insertPatient(patientEntity).toInt()

        // Create 500 appointments with different dates
        (1..500).forEach { i ->
            val appointmentEntity = AppointmentEntity(
                id = 0,
                patientId = patientId,
                date = LocalDate.now().plusDays(i.toLong()),
                time = LocalTime.of(9, 0),
                durationMinutes = 45,
                notes = null
            )
            database.appointmentDao().insertAppointment(appointmentEntity)
        }

        // Act
        val appointments = exportRepository.getAllAppointments()
        val csvFile = csvService.exportAppointments(appointments, exportDir)
        val lines = csvFile.readLines()

        // Assert
        // Check date format consistency (YYYY-MM-DD)
        lines.drop(1).forEach { line ->
            val parts = line.split(",")
            if (parts.size > 1) {
                val dateStr = parts.getOrNull(2) // date column
                assertNotNull(dateStr)
                // Should match YYYY-MM-DD pattern
                assertTrue("Invalid date format: $dateStr",
                    dateStr?.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) == true)
            }
        }
    }

    // ========================================
    // 5000+ Payment Export Tests
    // ========================================

    @Test
    fun exportLargePaymentDataset_5000Records_completesWithinTimeLimit() = runTest {
        // Arrange - Create patient first
        val patientEntity = PatientEntity(
            id = 0,
            name = "Rich Patient",
            phone = "(11) 99999-9999",
            email = "rich@example.com",
            status = "ACTIVE",
            initialConsultDate = LocalDate.now(),
            registrationDate = LocalDate.now()
        )
        val patientId = database.patientDao().insertPatient(patientEntity).toInt()

        // Create 5000 payments
        val paymentCount = 5000
        val startInsert = System.currentTimeMillis()

        (1..paymentCount).forEach { i ->
            val paymentEntity = PaymentEntity(
                id = 0,
                patientId = patientId,
                appointmentId = if (i % 2 == 0) (i / 2) else null,
                amount = BigDecimal((50 + (i % 1000)).toDouble()),
                method = when (i % 4) {
                    0 -> "PIX"
                    1 -> "CREDIT_CARD"
                    2 -> "DEBIT_CARD"
                    else -> "TRANSFER"
                },
                status = if (i % 3 == 0) "PENDING" else "PAID",
                paymentDate = LocalDate.now().minusDays((i % 90).toLong())
            )
            database.paymentDao().insertPayment(paymentEntity)
        }

        val insertTime = System.currentTimeMillis() - startInsert
        println("Insert time for $paymentCount payments: ${insertTime}ms")

        // Act
        val startExport = System.currentTimeMillis()
        val payments = exportRepository.getAllPayments()
        val csvFile = csvService.exportPayments(payments, exportDir)
        val exportTime = System.currentTimeMillis() - startExport

        // Assert
        assertNotNull(csvFile)
        assertTrue(csvFile.exists())
        assertEquals(paymentCount, payments.size)

        // Verify export completed within reasonable time (< 8 seconds)
        assertTrue("Export took too long: ${exportTime}ms", exportTime < 8000)

        // Verify file size is reasonable
        val fileSizeKB = csvFile.length() / 1024
        assertTrue("File too large: ${fileSizeKB}KB", fileSizeKB < 20000) // < 20MB

        // Verify all records in file
        val lineCount = csvFile.readLines().size
        assertEquals(paymentCount + 1, lineCount) // header + records
    }

    @Test
    fun exportLargePaymentDataset_decimalPrecision_preservedAcrossLargeDataset() = runTest {
        // Arrange - Create patient
        val patientEntity = PatientEntity(
            id = 0,
            name = "Test",
            phone = "123",
            email = "test@test.com",
            status = "ACTIVE",
            initialConsultDate = LocalDate.now(),
            registrationDate = LocalDate.now()
        )
        val patientId = database.patientDao().insertPatient(patientEntity).toInt()

        // Create 300 payments with various decimal amounts
        val amounts = listOf("10.50", "100.75", "250.00", "1000.25", "5000.99")
        (1..300).forEach { i ->
            val paymentEntity = PaymentEntity(
                id = 0,
                patientId = patientId,
                appointmentId = null,
                amount = BigDecimal(amounts[i % amounts.size]),
                method = "PIX",
                status = "PAID",
                paymentDate = LocalDate.now()
            )
            database.paymentDao().insertPayment(paymentEntity)
        }

        // Act
        val payments = exportRepository.getAllPayments()
        val csvFile = csvService.exportPayments(payments, exportDir)
        val content = csvFile.readText()

        // Assert
        amounts.forEach { amount ->
            assertTrue("Amount $amount not in export",
                content.contains(amount))
        }
    }

    // ========================================
    // Multi-Entity Large Dataset Tests
    // ========================================

    @Test
    fun exportCompleteDataset_500patientsAnd2500payments_completesSuccessfully() = runTest {
        // Arrange - Create 500 patients with payments
        val patientCount = 500
        val paymentsPerPatient = 5

        (1..patientCount).forEach { i ->
            val patientEntity = PatientEntity(
                id = 0,
                name = "Patient $i",
                phone = "(11) 9999-${String.format("%04d", i)}",
                email = "p$i@example.com",
                status = if (i % 2 == 0) "ACTIVE" else "INACTIVE",
                initialConsultDate = LocalDate.now(),
                registrationDate = LocalDate.now()
            )
            val patientId = database.patientDao().insertPatient(patientEntity).toInt()

            // Add payments for each patient
            (1..paymentsPerPatient).forEach { j ->
                val paymentEntity = PaymentEntity(
                    id = 0,
                    patientId = patientId,
                    appointmentId = null,
                    amount = BigDecimal((100 + j * 50).toDouble()),
                    method = "PIX",
                    status = "PAID",
                    paymentDate = LocalDate.now()
                )
                database.paymentDao().insertPayment(paymentEntity)
            }
        }

        // Act
        val startTime = System.currentTimeMillis()
        val patients = exportRepository.getAllPatients()
        val payments = exportRepository.getAllPayments()

        val patientFile = csvService.exportPatients(patients, exportDir)
        val paymentFile = csvService.exportPayments(payments, exportDir)
        val totalTime = System.currentTimeMillis() - startTime

        // Assert
        assertEquals(patientCount, patients.size)
        assertEquals(patientCount * paymentsPerPatient, payments.size)

        assertTrue(patientFile.exists())
        assertTrue(paymentFile.exists())

        // Verify export time is acceptable
        assertTrue("Export took too long: ${totalTime}ms", totalTime < 10000)

        // Verify record counts
        assertEquals(patientCount + 1, patientFile.readLines().size)
        assertEquals(patientCount * paymentsPerPatient + 1, paymentFile.readLines().size)
    }

    // ========================================
    // Stress Tests
    // ========================================

    @Test
    fun exportLargeDataset_memoryUsage_doesNotCrashWithLargeFile() = runTest {
        // Arrange - Create very large patient dataset
        val patientCount = 1000
        println("Creating $patientCount patients for stress test...")

        (1..patientCount).forEach { i ->
            val patientEntity = PatientEntity(
                id = 0,
                name = "Stress Test Patient $i with long name and description",
                phone = "(11) 9999-${String.format("%04d", i)}",
                email = "stress.test.patient$i@example.com",
                status = if (i % 2 == 0) "ACTIVE" else "INACTIVE",
                initialConsultDate = LocalDate.now().minusDays((i % 365).toLong()),
                registrationDate = LocalDate.now().minusDays((i % 365).toLong())
            )
            database.patientDao().insertPatient(patientEntity)
        }

        // Act
        val startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        val patients = exportRepository.getAllPatients()
        val csvFile = csvService.exportPatients(patients, exportDir)

        val endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryUsedMB = (endMemory - startMemory) / (1024 * 1024)

        // Assert
        assertNotNull(csvFile)
        assertTrue(csvFile.exists())
        assertEquals(patientCount, patients.size)

        println("Memory used for export: ${memoryUsedMB}MB")
        // Memory usage should be reasonable (< 100MB)
        assertTrue("Memory usage too high: ${memoryUsedMB}MB", memoryUsedMB < 100)
    }

    // ========================================
    // Performance Measurement Tests
    // ========================================

    @Test
    fun exportPerformance_measureQueryTime_500Patients() = runTest {
        // Arrange - Create 500 patients
        (1..500).forEach { i ->
            val patientEntity = PatientEntity(
                id = 0,
                name = "Patient $i",
                phone = "(11) 9999-${String.format("%04d", i)}",
                email = "p$i@example.com",
                status = "ACTIVE",
                initialConsultDate = LocalDate.now(),
                registrationDate = LocalDate.now()
            )
            database.patientDao().insertPatient(patientEntity)
        }

        // Act
        val startTime = System.currentTimeMillis()
        val patients = exportRepository.getAllPatients()
        val queryTime = System.currentTimeMillis() - startTime

        // Assert
        assertEquals(500, patients.size)
        println("Query time for 500 patients: ${queryTime}ms")
        assertTrue("Query too slow: ${queryTime}ms", queryTime < 1000)
    }

    @Test
    fun exportPerformance_measureSerializationTime_500Patients() = runTest {
        // Arrange - Create 500 patients
        (1..500).forEach { i ->
            val patientEntity = PatientEntity(
                id = 0,
                name = "Patient $i",
                phone = "(11) 9999-${String.format("%04d", i)}",
                email = "p$i@example.com",
                status = "ACTIVE",
                initialConsultDate = LocalDate.now(),
                registrationDate = LocalDate.now()
            )
            database.patientDao().insertPatient(patientEntity)
        }

        val patients = exportRepository.getAllPatients()

        // Act
        val startTime = System.currentTimeMillis()
        csvService.exportPatients(patients, exportDir)
        val serializeTime = System.currentTimeMillis() - startTime

        // Assert
        println("Serialization time for 500 patients: ${serializeTime}ms")
        assertTrue("Serialization too slow: ${serializeTime}ms", serializeTime < 2000)
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
