package com.psychologist.financial

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.entities.AppointmentEntity
import com.psychologist.financial.data.entities.PatientEntity
import com.psychologist.financial.data.entities.PayerInfoEntity
import com.psychologist.financial.data.entities.PaymentAppointmentCrossRef
import com.psychologist.financial.data.entities.PaymentEntity
import com.psychologist.financial.data.repositories.ExportRepository
import com.psychologist.financial.domain.models.BackupResult
import com.psychologist.financial.services.BackupExportService
import com.psychologist.financial.services.BackupImportService
import com.psychologist.financial.services.FileStorageManager
import com.psychologist.financial.domain.usecases.ExportBackupUseCase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Integration test: full backup export → import round-trip.
 *
 * Scenario:
 * 1. Insert known data into an in-memory Room database (source DB)
 * 2. Export a .pgfbackup using ExportBackupUseCase + BackupExportService
 * 3. Import the backup into a separate in-memory Room database (target DB)
 * 4. Verify all entity counts match the original source database
 *
 * This validates the complete end-to-end chain:
 *   Room → ExportRepository → BackupExportService (serialize + encrypt)
 *         → BackupImportService (decrypt + parse + importAtomic)
 *         → Room
 */
@RunWith(AndroidJUnit4::class)
class ExportBackupImportIntegrationTest {

    private lateinit var sourceDb: AppDatabase
    private lateinit var targetDb: AppDatabase
    private lateinit var exportRepository: ExportRepository
    private lateinit var backupExportService: BackupExportService
    private lateinit var fileStorageManager: FileStorageManager
    private lateinit var exportDir: File

    private val testPassword = "senhaSegura123"

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        sourceDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        targetDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        exportRepository = ExportRepository(sourceDb)
        backupExportService = BackupExportService()
        fileStorageManager = FileStorageManager(context)

        exportDir = File(context.cacheDir, "backup_test_${System.currentTimeMillis()}")
        exportDir.mkdirs()
    }

    @After
    fun tearDown() {
        sourceDb.close()
        targetDb.close()
        exportDir.deleteRecursively()
    }

    // ========================================
    // Full Round-trip Test
    // ========================================

    @Test
    fun backup_export_and_import_restores_all_entity_counts() = runTest {
        // --- 1. Insert known data into source DB ---
        val patientDao = sourceDb.patientDao()
        val appointmentDao = sourceDb.appointmentDao()
        val paymentDao = sourceDb.paymentDao()
        val payerInfoDao = sourceDb.payerInfoDao()

        // 3 patients
        val pid1 = patientDao.insert(
            PatientEntity(name = "Ana Costa", status = "ACTIVE",
                initialConsultDate = LocalDate.of(2026, 1, 1),
                registrationDate = LocalDate.of(2026, 1, 1))
        )
        val pid2 = patientDao.insert(
            PatientEntity(name = "Bruno Lima", status = "ACTIVE",
                initialConsultDate = LocalDate.of(2026, 2, 1),
                registrationDate = LocalDate.of(2026, 2, 1))
        )
        val pid3 = patientDao.insert(
            PatientEntity(name = "Carla Santos", status = "INACTIVE",
                naoPagante = true,
                initialConsultDate = LocalDate.of(2026, 3, 1),
                registrationDate = LocalDate.of(2026, 3, 1))
        )

        // 2 appointments
        val aid1 = appointmentDao.insert(
            AppointmentEntity(patientId = pid1, date = LocalDate.of(2026, 3, 5),
                timeStart = LocalTime.of(9, 0), durationMinutes = 50)
        )
        appointmentDao.insert(
            AppointmentEntity(patientId = pid2, date = LocalDate.of(2026, 3, 6),
                timeStart = LocalTime.of(10, 0), durationMinutes = 50)
        )

        // 1 payment
        val pymId = paymentDao.insert(
            PaymentEntity(patientId = pid1, amount = BigDecimal("150.00"),
                paymentDate = LocalDate.of(2026, 3, 5))
        )
        paymentDao.insertAppointmentLink(PaymentAppointmentCrossRef(pymId, aid1))

        // 1 payer info (for non-paying patient pid3)
        payerInfoDao.insert(
            PayerInfoEntity(patientId = pid3, nome = "Maria Responsavel",
                cpf = "98765432100")
        )

        // --- 2. Export backup ---
        val useCase = ExportBackupUseCase(exportRepository, backupExportService, fileStorageManager)
        val exportResult = useCase.execute(testPassword, testPassword)

        assertTrue("Export should succeed", exportResult is BackupResult.ExportSuccess)
        val success = exportResult as BackupResult.ExportSuccess
        assertEquals("Should export 3 patients", 3, success.patientCount)
        assertEquals("Should export 2 appointments", 2, success.appointmentCount)
        assertEquals("Should export 1 payment", 1, success.paymentCount)
        assertEquals("Should export 1 payer info", 1, success.payerInfoCount)
        assertNotNull("Backup file should exist", success.file)
        assertTrue("Backup file should not be empty", success.file.length() > 0)

        // --- 3. Import into target DB ---
        val importService = BackupImportService(targetDb)
        val fileBytes = success.file.readBytes()
        val jsonBytes = importService.decrypt(fileBytes, testPassword)
        val backupData = importService.parse(jsonBytes)
        importService.importAtomic(backupData)

        // --- 4. Verify entity counts in target DB ---
        val targetPatientDao = targetDb.patientDao()
        val targetAppointmentDao = targetDb.appointmentDao()
        val targetPaymentDao = targetDb.paymentDao()
        val targetPayerInfoDao = targetDb.payerInfoDao()

        val importedPatients = targetPatientDao.getAllPatients()
        val importedAppointments = targetAppointmentDao.getAll()
        val importedPayments = targetPaymentDao.getAll()
        val importedPayerInfos = targetPayerInfoDao.getAll()

        assertEquals("Imported patient count must match source", 3, importedPatients.size)
        assertEquals("Imported appointment count must match source", 2, importedAppointments.size)
        assertEquals("Imported payment count must match source", 1, importedPayments.size)
        assertEquals("Imported payer info count must match source", 1, importedPayerInfos.size)
    }

    @Test
    fun backup_import_replaces_existing_data_in_target() = runTest {
        // Pre-populate target DB with different data
        val targetPatientDao = targetDb.patientDao()
        targetPatientDao.insert(
            PatientEntity(name = "Old Patient", status = "ACTIVE",
                initialConsultDate = LocalDate.of(2025, 1, 1),
                registrationDate = LocalDate.of(2025, 1, 1))
        )
        assertEquals("Target should have 1 old patient", 1, targetPatientDao.getAllPatients().size)

        // Insert fresh data in source
        sourceDb.patientDao().insert(
            PatientEntity(name = "New Patient A", status = "ACTIVE",
                initialConsultDate = LocalDate.of(2026, 1, 1),
                registrationDate = LocalDate.of(2026, 1, 1))
        )
        sourceDb.patientDao().insert(
            PatientEntity(name = "New Patient B", status = "ACTIVE",
                initialConsultDate = LocalDate.of(2026, 1, 2),
                registrationDate = LocalDate.of(2026, 1, 2))
        )

        // Export from source
        val useCase = ExportBackupUseCase(exportRepository, backupExportService, fileStorageManager)
        val exportResult = useCase.execute(testPassword, testPassword) as BackupResult.ExportSuccess

        // Import into target (overwrites old patient)
        val importService = BackupImportService(targetDb)
        val jsonBytes = importService.decrypt(exportResult.file.readBytes(), testPassword)
        importService.importAtomic(importService.parse(jsonBytes))

        // Old patient should be gone; only 2 new patients
        val afterImport = targetPatientDao.getAllPatients()
        assertEquals("Old data should be replaced — only 2 new patients", 2, afterImport.size)
        assertTrue("Should have New Patient A", afterImport.any { it.name == "New Patient A" })
        assertTrue("Should have New Patient B", afterImport.any { it.name == "New Patient B" })
    }

    @Test
    fun wrong_password_does_not_modify_existing_target_data() = runTest {
        // Pre-populate target with 1 patient
        val targetPatientDao = targetDb.patientDao()
        targetPatientDao.insert(
            PatientEntity(name = "Existing Patient", status = "ACTIVE",
                initialConsultDate = LocalDate.of(2025, 6, 1),
                registrationDate = LocalDate.of(2025, 6, 1))
        )

        // Export from source (empty)
        val useCase = ExportBackupUseCase(exportRepository, backupExportService, fileStorageManager)
        val exportResult = useCase.execute(testPassword, testPassword) as BackupResult.ExportSuccess

        // Attempt import with wrong password — should throw before touching DB
        val importService = BackupImportService(targetDb)
        var threw = false
        try {
            val jsonBytes = importService.decrypt(exportResult.file.readBytes(), "wrongPassword")
            importService.importAtomic(importService.parse(jsonBytes))
        } catch (e: Exception) {
            threw = true
        }

        assertTrue("decrypt() with wrong password should throw", threw)

        // Target DB should be unchanged
        assertEquals(
            "Target DB should still have 1 patient after failed import",
            1,
            targetPatientDao.getAllPatients().size
        )
    }
}
