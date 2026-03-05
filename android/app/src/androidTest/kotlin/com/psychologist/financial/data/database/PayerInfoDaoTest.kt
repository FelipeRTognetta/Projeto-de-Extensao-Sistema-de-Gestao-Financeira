package com.psychologist.financial.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.psychologist.financial.data.entities.PatientEntity
import com.psychologist.financial.data.entities.PayerInfoEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Instrumented integration tests for PayerInfoDao.
 *
 * TDD Red Phase — written before full DAO/repository chain is exercised via tests.
 * These tests verify correct DAO behavior using an in-memory Room database.
 *
 * Coverage:
 * - Insert and retrieve by patientId
 * - Update and verify changes
 * - Delete by patientId
 * - Unique constraint on patientId (second insert must throw)
 *
 * Note: A patient row must exist before inserting a payer_info row
 * (FK constraint: payer_info.patient_id → patient.id).
 */
@RunWith(AndroidJUnit4::class)
class PayerInfoDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var payerInfoDao: PayerInfoDao
    private lateinit var patientDao: PatientDao
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        payerInfoDao = database.payerInfoDao()
        patientDao = database.patientDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun insertTestPatient(
        id: Long = 0,
        name: String = "Paciente Teste",
        phone: String? = "(11) 11111-1111"
    ): Long = runBlocking {
        patientDao.insert(
            PatientEntity(
                id = id,
                name = name,
                phone = phone,
                email = null,
                status = "ACTIVE",
                initialConsultDate = LocalDate.now(),
                registrationDate = LocalDate.now(),
                lastAppointmentDate = null,
                createdDate = LocalDateTime.now()
            )
        )
    }

    private fun makePayerInfo(patientId: Long, nome: String = "Responsável Teste"): PayerInfoEntity =
        PayerInfoEntity(
            patientId = patientId,
            nome = nome,
            cpf = null,
            endereco = null,
            email = null,
            telefone = null
        )

    // ========================================
    // Insert + Retrieve
    // ========================================

    @Test
    fun insert_andGetByPatientId_returnsInsertedRecord() = runBlocking {
        val patientId = insertTestPatient()
        val payer = makePayerInfo(patientId, nome = "Maria Silva")
        payerInfoDao.insert(payer)

        val result = payerInfoDao.getByPatientId(patientId)
        assertNotNull(result, "Deve encontrar o responsável inserido")
        assertEquals("Maria Silva", result.nome)
        assertEquals(patientId, result.patientId)
    }

    @Test
    fun insert_withAllFields_persistsCorrectly() = runBlocking {
        val patientId = insertTestPatient()
        val payer = PayerInfoEntity(
            patientId = patientId,
            nome = "João Responsável",
            cpf = "52998224725",
            endereco = "Rua das Flores, 123",
            email = "joao@exemplo.com",
            telefone = "(11) 99999-9999"
        )
        payerInfoDao.insert(payer)

        val result = payerInfoDao.getByPatientId(patientId)
        assertNotNull(result)
        assertEquals("João Responsável", result.nome)
        assertEquals("52998224725", result.cpf)
        assertEquals("Rua das Flores, 123", result.endereco)
        assertEquals("joao@exemplo.com", result.email)
        assertEquals("(11) 99999-9999", result.telefone)
    }

    @Test
    fun getByPatientId_whenNoRecord_returnsNull() = runBlocking {
        val result = payerInfoDao.getByPatientId(999L)
        assertNull(result, "Deve retornar null quando não existe registro")
    }

    // ========================================
    // Update
    // ========================================

    @Test
    fun update_changesStoredValues() = runBlocking {
        val patientId = insertTestPatient()
        val payer = makePayerInfo(patientId, nome = "Nome Antigo")
        val insertedId = payerInfoDao.insert(payer)

        val updated = payer.copy(id = insertedId, nome = "Nome Novo", email = "novo@exemplo.com")
        payerInfoDao.update(updated)

        val result = payerInfoDao.getByPatientId(patientId)
        assertNotNull(result)
        assertEquals("Nome Novo", result.nome)
        assertEquals("novo@exemplo.com", result.email)
    }

    // ========================================
    // Delete
    // ========================================

    @Test
    fun deleteByPatientId_removesRecord() = runBlocking {
        val patientId = insertTestPatient()
        payerInfoDao.insert(makePayerInfo(patientId))

        payerInfoDao.deleteByPatientId(patientId)

        val result = payerInfoDao.getByPatientId(patientId)
        assertNull(result, "Deve retornar null após exclusão")
    }

    @Test
    fun deleteByPatientId_whenNoRecord_isNoOp() = runBlocking {
        // Should not throw when record doesn't exist
        payerInfoDao.deleteByPatientId(999L)
        val result = payerInfoDao.getByPatientId(999L)
        assertNull(result)
    }

    // ========================================
    // Unique constraint on patientId
    // ========================================

    @Test(expected = android.database.sqlite.SQLiteConstraintException::class)
    fun insert_secondPayerForSamePatient_throwsConstraintException() = runBlocking {
        val patientId = insertTestPatient()
        payerInfoDao.insert(makePayerInfo(patientId, nome = "Primeiro Responsável"))
        // Second insert for the same patientId must throw (UNIQUE constraint)
        payerInfoDao.insert(makePayerInfo(patientId, nome = "Segundo Responsável"))
    }

    // ========================================
    // Multiple patients
    // ========================================

    @Test
    fun insert_differentPatients_retrieveCorrectly() = runBlocking {
        val patientId1 = insertTestPatient(name = "Paciente 1", phone = "(11) 11111-1111")
        val patientId2 = insertTestPatient(name = "Paciente 2", phone = "(22) 22222-2222")

        payerInfoDao.insert(makePayerInfo(patientId1, nome = "Responsável 1"))
        payerInfoDao.insert(makePayerInfo(patientId2, nome = "Responsável 2"))

        val result1 = payerInfoDao.getByPatientId(patientId1)
        val result2 = payerInfoDao.getByPatientId(patientId2)

        assertNotNull(result1)
        assertNotNull(result2)
        assertEquals("Responsável 1", result1.nome)
        assertEquals("Responsável 2", result2.nome)
    }
}
