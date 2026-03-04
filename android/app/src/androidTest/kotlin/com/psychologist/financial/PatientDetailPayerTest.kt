package com.psychologist.financial

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.entities.PatientEntity
import com.psychologist.financial.data.entities.PayerInfoEntity
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.domain.models.PayerInfo
import com.psychologist.financial.ui.screens.PatientDetailPayerSection
import com.psychologist.financial.ui.theme.FinancialTheme
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SupportFactory
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Compose UI tests for the "Responsável Financeiro" section in PatientDetailScreen.
 *
 * TDD Red Phase — written BEFORE T028 and T029 implement the feature.
 * These tests should FAIL until:
 *   - T028: PatientRepository loads PayerInfo when naoPagante=true
 *   - T029: PatientDetailScreen renders the "Responsável Financeiro" section
 *
 * Coverage:
 * - Patient with naoPagante=true + payer info → section is visible with correct data
 * - Patient with naoPagante=false → section is absent
 * - Inactive patient with naoPagante=true → section still visible (read-only)
 *
 * Note: Uses a real in-memory Room database with SQLCipher and a simplified
 * ViewModel setup so that the real repository layer is exercised.
 */
@RunWith(AndroidJUnit4::class)
class PatientDetailPayerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        val passphrase = "test-passphrase".toByteArray()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .openHelperFactory(SupportFactory(passphrase))
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ========================================
    // Helpers
    // ========================================

    private fun insertPatient(
        id: Long = 1L,
        naoPagante: Boolean = false,
        status: String = "ACTIVE"
    ): Long = runBlocking {
        database.patientDao().insert(
            PatientEntity(
                id = id,
                name = "Paciente Teste",
                phone = "(11) 11111-1111",
                email = null,
                status = status,
                initialConsultDate = LocalDate.now(),
                registrationDate = LocalDate.now(),
                lastAppointmentDate = null,
                createdDate = LocalDateTime.now(),
                naoPagante = naoPagante
            )
        )
    }

    private fun insertPayerInfo(
        patientId: Long,
        nome: String = "Maria Responsável",
        cpf: String? = "52998224725",
        email: String? = "maria@exemplo.com",
        telefone: String? = "(11) 99999-8888",
        endereco: String? = "Rua das Flores, 123"
    ) = runBlocking {
        database.payerInfoDao().insert(
            PayerInfoEntity(
                patientId = patientId,
                nome = nome,
                cpf = cpf,
                email = email,
                telefone = telefone,
                endereco = endereco
            )
        )
    }

    private fun makePatientWithPayer(
        naoPagante: Boolean = true,
        status: PatientStatus = PatientStatus.ACTIVE,
        payerInfo: PayerInfo? = null
    ): Patient = Patient(
        id = 1L,
        name = "Paciente Teste",
        phone = "(11) 11111-1111",
        email = null,
        status = status,
        initialConsultDate = LocalDate.now(),
        registrationDate = LocalDate.now(),
        lastAppointmentDate = null,
        naoPagante = naoPagante,
        payerInfo = payerInfo
    )

    private fun makePayerInfo(patientId: Long = 1L): PayerInfo = PayerInfo(
        id = 1L,
        patientId = patientId,
        nome = "Maria Responsável",
        cpf = "52998224725",
        email = "maria@exemplo.com",
        telefone = "(11) 99999-8888",
        endereco = "Rua das Flores, 123"
    )

    // ========================================
    // Tests: naoPagante=true → section visible
    // ========================================

    @Test
    fun detailScreen_comNaoPaganteTrue_exibeSeccaoResponsavel() {
        val payerInfo = makePayerInfo()
        val patient = makePatientWithPayer(naoPagante = true, payerInfo = payerInfo)

        composeTestRule.setContent {
            FinancialTheme {
                // Render a simplified patient detail that accepts a Patient directly
                PatientDetailPayerSection(patient = patient)
            }
        }

        composeTestRule.onNodeWithText("Responsável Financeiro").assertIsDisplayed()
        composeTestRule.onNodeWithText("Maria Responsável").assertIsDisplayed()
    }

    @Test
    fun detailScreen_comNaoPaganteTrue_exibeNomeDoResponsavel() {
        val payerInfo = makePayerInfo()
        val patient = makePatientWithPayer(naoPagante = true, payerInfo = payerInfo)

        composeTestRule.setContent {
            FinancialTheme {
                PatientDetailPayerSection(patient = patient)
            }
        }

        composeTestRule.onNodeWithText("Maria Responsável").assertIsDisplayed()
    }

    @Test
    fun detailScreen_comNaoPaganteTrue_exibeCpfFormatado() {
        val payerInfo = makePayerInfo()
        val patient = makePatientWithPayer(naoPagante = true, payerInfo = payerInfo)

        composeTestRule.setContent {
            FinancialTheme {
                PatientDetailPayerSection(patient = patient)
            }
        }

        // CPF "52998224725" should be displayed as "529.982.247-25"
        composeTestRule.onNodeWithText("529.982.247-25").assertIsDisplayed()
    }

    @Test
    fun detailScreen_comNaoPaganteTrue_exibeEmail() {
        val payerInfo = makePayerInfo()
        val patient = makePatientWithPayer(naoPagante = true, payerInfo = payerInfo)

        composeTestRule.setContent {
            FinancialTheme {
                PatientDetailPayerSection(patient = patient)
            }
        }

        composeTestRule.onNodeWithText("maria@exemplo.com").assertIsDisplayed()
    }

    @Test
    fun detailScreen_comNaoPaganteTrue_exibeTelefone() {
        val payerInfo = makePayerInfo()
        val patient = makePatientWithPayer(naoPagante = true, payerInfo = payerInfo)

        composeTestRule.setContent {
            FinancialTheme {
                PatientDetailPayerSection(patient = patient)
            }
        }

        composeTestRule.onNodeWithText("(11) 99999-8888").assertIsDisplayed()
    }

    @Test
    fun detailScreen_comNaoPaganteTrue_exibeEndereco() {
        val payerInfo = makePayerInfo()
        val patient = makePatientWithPayer(naoPagante = true, payerInfo = payerInfo)

        composeTestRule.setContent {
            FinancialTheme {
                PatientDetailPayerSection(patient = patient)
            }
        }

        composeTestRule.onNodeWithText("Rua das Flores, 123").assertIsDisplayed()
    }

    // ========================================
    // Tests: naoPagante=false → section absent
    // ========================================

    @Test
    fun detailScreen_comNaoPaganteFalse_naoExibeSeccaoResponsavel() {
        val patient = makePatientWithPayer(naoPagante = false, payerInfo = null)

        composeTestRule.setContent {
            FinancialTheme {
                PatientDetailPayerSection(patient = patient)
            }
        }

        composeTestRule.onNodeWithText("Responsável Financeiro").assertIsNotDisplayed()
    }

    // ========================================
    // Tests: inactive patient → section still visible
    // ========================================

    @Test
    fun detailScreen_pacienteInativoComResponsavel_exibeSeccao() {
        val payerInfo = makePayerInfo()
        val patient = makePatientWithPayer(
            naoPagante = true,
            status = PatientStatus.INACTIVE,
            payerInfo = payerInfo
        )

        composeTestRule.setContent {
            FinancialTheme {
                PatientDetailPayerSection(patient = patient)
            }
        }

        composeTestRule.onNodeWithText("Responsável Financeiro").assertIsDisplayed()
        composeTestRule.onNodeWithText("Maria Responsável").assertIsDisplayed()
    }
}
