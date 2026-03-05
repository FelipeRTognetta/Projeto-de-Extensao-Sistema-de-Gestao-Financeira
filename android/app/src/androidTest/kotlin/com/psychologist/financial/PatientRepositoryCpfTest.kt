package com.psychologist.financial

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportFactory
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Instrumented integration tests for PatientRepository CPF support.
 *
 * TDD: Written BEFORE the implementation (T015 - PatientRepository CPF update).
 * Tests FAIL until createPatient/updatePatient are updated to call isCpfInUse.
 *
 * Coverage:
 * - createPatient with valid CPF → persists and CPF is stored
 * - createPatient with duplicate CPF → throws IllegalArgumentException
 * - createPatient without CPF → persists normally (CPF is optional)
 * - updatePatient with CPF in use by another patient → throws IllegalArgumentException
 * - updatePatient with own CPF → succeeds (no false positive on self-check)
 *
 * Run via: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class PatientRepositoryCpfTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: PatientRepository
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today: LocalDate = LocalDate.now()

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        val passphrase = "test-passphrase".toByteArray()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .openHelperFactory(SupportFactory(passphrase))
            .allowMainThreadQueries()
            .build()
        repository = PatientRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun makePatient(
        name: String = "Ana Silva",
        phone: String = "(11) 99999-9999",
        cpf: String? = null,
        endereco: String? = null
    ) = Patient(
        id = 0,
        name = name,
        phone = phone,
        email = null,
        status = PatientStatus.ACTIVE,
        initialConsultDate = today,
        registrationDate = today,
        cpf = cpf,
        endereco = endereco
    )

    // ========================================
    // createPatient CPF Tests
    // ========================================

    @Test
    fun createPatient_withValidCpf_persistsCpf() = runBlocking {
        val patient = makePatient(cpf = "12345678909")

        val id = repository.createPatient(patient)

        val saved = repository.getPatient(id)
        assertNotNull(saved)
        assertEquals("12345678909", saved.cpf)
    }

    @Test
    fun createPatient_withDuplicateCpf_throwsIllegalArgumentException() = runBlocking {
        // First patient with this CPF
        repository.createPatient(makePatient(phone = "(11) 11111-1111", cpf = "12345678909"))

        // Second patient with the same CPF → should throw
        assertFailsWith<IllegalArgumentException> {
            repository.createPatient(makePatient(phone = "(22) 22222-2222", cpf = "12345678909"))
        }
        Unit
    }

    @Test
    fun createPatient_withoutCpf_persistsNormally() = runBlocking {
        val patient = makePatient(cpf = null)

        val id = repository.createPatient(patient)

        val saved = repository.getPatient(id)
        assertNotNull(saved)
        assertEquals(null, saved.cpf)
    }

    @Test
    fun createPatient_withEndereco_persistsEndereco() = runBlocking {
        val patient = makePatient(endereco = "Rua das Flores, 123")

        val id = repository.createPatient(patient)

        val saved = repository.getPatient(id)
        assertNotNull(saved)
        assertEquals("Rua das Flores, 123", saved.endereco)
    }

    // ========================================
    // updatePatient CPF Tests
    // ========================================

    @Test
    fun updatePatient_withCpfInUseByAnother_throwsIllegalArgumentException() = runBlocking {
        // Patient 1 claims CPF
        repository.createPatient(makePatient(phone = "(11) 11111-1111", cpf = "12345678909"))
        // Patient 2 without CPF initially
        val id2 = repository.createPatient(makePatient(phone = "(22) 22222-2222", cpf = null))

        val patient2 = repository.getPatient(id2)!!
        val updated = patient2.copy(cpf = "12345678909")

        // Trying to assign patient 1's CPF to patient 2 → should throw
        assertFailsWith<IllegalArgumentException> {
            repository.updatePatient(updated)
        }
        Unit
    }

    @Test
    fun updatePatient_withOwnCpf_succeeds() = runBlocking {
        // Patient has CPF → updating with same CPF (editing other fields) should work
        val id = repository.createPatient(makePatient(cpf = "12345678909"))
        val patient = repository.getPatient(id)!!

        val updated = patient.copy(endereco = "Rua Nova, 456")
        repository.updatePatient(updated)

        val saved = repository.getPatient(id)!!
        assertEquals("12345678909", saved.cpf)
        assertEquals("Rua Nova, 456", saved.endereco)
    }
}
