package com.psychologist.financial.services

import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.database.AppointmentDao
import com.psychologist.financial.data.database.PatientDao
import com.psychologist.financial.data.database.PayerInfoDao
import com.psychologist.financial.data.database.PaymentDao
import com.psychologist.financial.data.entities.AppointmentEntity
import com.psychologist.financial.data.entities.PatientEntity
import com.psychologist.financial.data.entities.PayerInfoEntity
import com.psychologist.financial.data.entities.PaymentEntity
import com.psychologist.financial.data.entities.PaymentAppointmentCrossRef
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for BackupImportService
 *
 * Coverage:
 * - decrypt() delegates to BackupExportService — correct password works, wrong password throws
 * - parse() deserializes JSON back into BackupData correctly
 * - importAtomic() deletes all existing data before inserting
 * - importAtomic() inserts all records from backup data
 * - importAtomic() propagates exception when insert fails
 */
@RunWith(MockitoJUnitRunner::class)
class BackupImportServiceTest {

    @Mock private lateinit var mockDatabase: AppDatabase
    @Mock private lateinit var mockPatientDao: PatientDao
    @Mock private lateinit var mockAppointmentDao: AppointmentDao
    @Mock private lateinit var mockPaymentDao: PaymentDao
    @Mock private lateinit var mockPayerInfoDao: PayerInfoDao

    private lateinit var service: BackupImportService
    private val exportService = BackupExportService()

    private val sampleBackupData = BackupData(
        version = 3,
        appVersion = "1.0",
        exportedAt = "2026-03-01T12:00:00",
        patients = listOf(
            PatientBackup(
                id = 1L, name = "Ana Costa", phone = null, email = null, status = "ACTIVE",
                initialConsultDate = "2026-01-01", registrationDate = "2026-01-01",
                lastAppointmentDate = null, cpf = null, endereco = null, naoPagante = false,
                createdDate = "2026-01-01T10:00:00"
            )
        ),
        appointments = listOf(
            AppointmentBackup(
                id = 1L, patientId = 1L, date = "2026-03-01", timeStart = "09:00",
                durationMinutes = 50, notes = null, createdDate = "2026-03-01T08:00:00"
            )
        ),
        payments = listOf(
            PaymentBackup(
                id = 1L, patientId = 1L, amount = "150.00",
                paymentDate = "2026-03-01", createdDate = "2026-03-01T09:00:00"
            )
        ),
        paymentAppointments = listOf(PaymentAppointmentBackup(paymentId = 1L, appointmentId = 1L)),
        payerInfos = emptyList()
    )

    @Before
    fun setUp() {
        whenever(mockDatabase.patientDao()).thenReturn(mockPatientDao)
        whenever(mockDatabase.appointmentDao()).thenReturn(mockAppointmentDao)
        whenever(mockDatabase.paymentDao()).thenReturn(mockPaymentDao)
        whenever(mockDatabase.payerInfoDao()).thenReturn(mockPayerInfoDao)

        // Make runInTransaction execute the body synchronously
        doAnswer { invocation ->
            (invocation.arguments[0] as Runnable).run()
            null
        }.whenever(mockDatabase).runInTransaction(any())

        service = BackupImportService(mockDatabase)
    }

    // ========================================
    // decrypt() Tests
    // ========================================

    @Test
    fun `decrypt with correct password returns original bytes`() {
        val plaintext = "backup content".toByteArray(Charsets.UTF_8)
        val encrypted = exportService.encrypt(plaintext, "senha123")
        val decrypted = service.decrypt(encrypted, "senha123")
        assertArrayEquals("Decrypted content must match original", plaintext, decrypted)
    }

    @Test(expected = Exception::class)
    fun `decrypt with wrong password throws exception before any DB write`() {
        val encrypted = exportService.encrypt("data".toByteArray(), "correct")
        service.decrypt(encrypted, "wrong") // must throw — no DB write occurs
    }

    // ========================================
    // parse() Tests
    // ========================================

    @Test
    fun `parse returns BackupData with correct patient count`() {
        val jsonBytes = exportService.serialize(
            patients = sampleBackupData.patients.map {
                com.psychologist.financial.domain.models.Patient.createForTesting(
                    id = it.id, name = it.name
                )
            },
            appointments = emptyList(),
            payments = emptyList(),
            paymentAppointments = emptyList(),
            payerInfos = emptyList()
        )
        val result = service.parse(jsonBytes)
        assert(result.patients.isNotEmpty()) { "Parsed BackupData should contain patients" }
        assert(result.patients[0].name == "Ana Costa") { "Patient name should be preserved" }
    }

    @Test
    fun `parse preserves backup version`() {
        val jsonBytes = exportService.serialize(
            patients = emptyList(), appointments = emptyList(), payments = emptyList(),
            paymentAppointments = emptyList(), payerInfos = emptyList()
        )
        val result = service.parse(jsonBytes)
        assert(result.version == 3) { "Backup version should be 3" }
    }

    // ========================================
    // importAtomic() Tests
    // ========================================

    @Test
    fun `importAtomic deletes all existing data before importing`() = runTest {
        service.importAtomic(sampleBackupData)

        verify(mockPaymentDao).deleteAllCrossRefs()
        verify(mockPaymentDao).deleteAllPayments()
        verify(mockAppointmentDao).deleteAll()
        verify(mockPayerInfoDao).deleteAll()
        verify(mockPatientDao).deleteAll()
    }

    @Test
    fun `importAtomic inserts all patients from backup`() = runTest {
        service.importAtomic(sampleBackupData)

        verify(mockPatientDao).insertAll(
            org.mockito.kotlin.check { entities: List<PatientEntity> ->
                assert(entities.size == 1) { "Should insert 1 patient" }
                assert(entities[0].name == "Ana Costa") { "Patient name should match" }
            }
        )
    }

    @Test
    fun `importAtomic inserts all appointments from backup`() = runTest {
        service.importAtomic(sampleBackupData)

        verify(mockAppointmentDao).insertAll(
            org.mockito.kotlin.check { entities: List<AppointmentEntity> ->
                assert(entities.size == 1) { "Should insert 1 appointment" }
                assert(entities[0].durationMinutes == 50) { "Duration should match" }
            }
        )
    }

    @Test
    fun `importAtomic inserts all payments and cross-refs from backup`() = runTest {
        service.importAtomic(sampleBackupData)

        verify(mockPaymentDao).insertAll(
            org.mockito.kotlin.check { entities: List<PaymentEntity> ->
                assert(entities.size == 1) { "Should insert 1 payment" }
            }
        )
        verify(mockPaymentDao).insertAllCrossRefs(
            org.mockito.kotlin.check { links: List<PaymentAppointmentCrossRef> ->
                assert(links.size == 1) { "Should insert 1 cross-ref" }
                assert(links[0].paymentId == 1L) { "Cross-ref paymentId should match" }
            }
        )
    }

    @Test
    fun `importAtomic deletes before inserting for patients`() = runTest {
        val order = inOrder(mockPatientDao)
        service.importAtomic(sampleBackupData)

        order.verify(mockPatientDao).deleteAll()
        order.verify(mockPatientDao).insertAll(any())
    }

    @Test(expected = Exception::class)
    fun `importAtomic propagates exception when patient insert fails`() = runTest {
        whenever(mockPatientDao.insertAll(any())).thenThrow(RuntimeException("DB full"))
        service.importAtomic(sampleBackupData)
    }
}
