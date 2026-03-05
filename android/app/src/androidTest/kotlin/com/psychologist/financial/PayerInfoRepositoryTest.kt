package com.psychologist.financial

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.entities.PatientEntity
import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.data.repositories.PayerInfoRepository
import com.psychologist.financial.domain.models.PayerInfo
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SupportFactory
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
 * Instrumented integration tests for PayerInfoRepository.
 *
 * TDD Red Phase — written BEFORE PayerInfoRepository is implemented (T023).
 * These tests should FAIL until T023 creates PayerInfoRepository.
 *
 * Uses in-memory Room database with SQLCipher (required for withTransaction).
 *
 * Coverage:
 * - savePayerInfo with nome → persists
 * - savePayerInfo for same patientId → upsert (no duplicate)
 * - removePayerInfo → record deleted
 * - getPayerInfoByPatientId with unknown ID → null
 */
@RunWith(AndroidJUnit4::class)
class PayerInfoRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: PayerInfoRepository
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        val passphrase = "test-passphrase".toByteArray()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .openHelperFactory(SupportFactory(passphrase))
            .allowMainThreadQueries()
            .build()
        repository = PayerInfoRepository(database)
        insertTestPatient(patientId = 1L)
        insertTestPatient(patientId = 2L, phone = "(22) 22222-2222")
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun insertTestPatient(patientId: Long = 1L, phone: String = "(11) 11111-1111") =
        runBlocking {
            database.patientDao().insert(
                PatientEntity(
                    id = patientId,
                    name = "Paciente $patientId",
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

    private fun makePayerInfo(
        patientId: Long = 1L,
        nome: String = "Responsável Teste",
        cpf: String? = null,
        email: String? = null,
        telefone: String? = null,
        endereco: String? = null
    ) = PayerInfo(
        patientId = patientId,
        nome = nome,
        cpf = cpf,
        email = email,
        telefone = telefone,
        endereco = endereco
    )

    // ========================================
    // savePayerInfo
    // ========================================

    @Test
    fun savePayerInfo_comNomeValido_persiste() = runBlocking {
        val payer = makePayerInfo(patientId = 1L, nome = "Maria Responsável")
        repository.savePayerInfo(1L, payer)

        val result = repository.getPayerInfoByPatientId(1L)
        assertNotNull(result, "Responsável deve ser encontrado após salvar")
        assertEquals("Maria Responsável", result.nome)
        assertEquals(1L, result.patientId)
    }

    @Test
    fun savePayerInfo_comTodosOsCampos_persisteCorretamente() = runBlocking {
        val payer = makePayerInfo(
            patientId = 1L,
            nome = "João Responsável",
            cpf = "52998224725",
            email = "joao@exemplo.com",
            telefone = "(11) 99999-9999",
            endereco = "Rua das Flores, 123"
        )
        repository.savePayerInfo(1L, payer)

        val result = repository.getPayerInfoByPatientId(1L)
        assertNotNull(result)
        assertEquals("João Responsável", result.nome)
        assertEquals("52998224725", result.cpf)
        assertEquals("joao@exemplo.com", result.email)
        assertEquals("(11) 99999-9999", result.telefone)
        assertEquals("Rua das Flores, 123", result.endereco)
    }

    @Test
    fun savePayerInfo_paraMemmoPatientId_fazUpsert() = runBlocking {
        val payerOriginal = makePayerInfo(patientId = 1L, nome = "Nome Original")
        repository.savePayerInfo(1L, payerOriginal)

        val payerAtualizado = makePayerInfo(patientId = 1L, nome = "Nome Atualizado")
        repository.savePayerInfo(1L, payerAtualizado)

        val result = repository.getPayerInfoByPatientId(1L)
        assertNotNull(result, "Responsável deve existir após upsert")
        assertEquals("Nome Atualizado", result.nome, "Deve conter o valor atualizado")

        // Verify no duplicates — can only have one record per patientId
        val rawCount = database.payerInfoDao().getByPatientId(1L)
        assertNotNull(rawCount, "Deve existir exatamente um registro")
    }

    @Test
    fun savePayerInfo_diferentesPatients_persistemIndependentemente() = runBlocking {
        repository.savePayerInfo(1L, makePayerInfo(patientId = 1L, nome = "Responsável do Paciente 1"))
        repository.savePayerInfo(2L, makePayerInfo(patientId = 2L, nome = "Responsável do Paciente 2"))

        val result1 = repository.getPayerInfoByPatientId(1L)
        val result2 = repository.getPayerInfoByPatientId(2L)

        assertNotNull(result1)
        assertNotNull(result2)
        assertEquals("Responsável do Paciente 1", result1.nome)
        assertEquals("Responsável do Paciente 2", result2.nome)
    }

    // ========================================
    // removePayerInfo
    // ========================================

    @Test
    fun removePayerInfo_removendoRegistroExistente_retornaNull() = runBlocking {
        repository.savePayerInfo(1L, makePayerInfo(patientId = 1L))
        repository.removePayerInfo(1L)

        val result = repository.getPayerInfoByPatientId(1L)
        assertNull(result, "Deve retornar null após remoção")
    }

    @Test
    fun removePayerInfo_semRegistroExistente_naoLancaExcecao() = runBlocking {
        // Should be a no-op without throwing
        repository.removePayerInfo(999L)
        val result = repository.getPayerInfoByPatientId(999L)
        assertNull(result)
    }

    // ========================================
    // getPayerInfoByPatientId
    // ========================================

    @Test
    fun getPayerInfoByPatientId_semRegistro_retornaNull() = runBlocking {
        val result = repository.getPayerInfoByPatientId(999L)
        assertNull(result, "Deve retornar null para ID inexistente")
    }

    @Test
    fun getPayerInfoByPatientId_aposRemocao_retornaNull() = runBlocking {
        repository.savePayerInfo(1L, makePayerInfo(patientId = 1L))
        repository.removePayerInfo(1L)

        val result = repository.getPayerInfoByPatientId(1L)
        assertNull(result)
    }
}
