package com.psychologist.financial.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PayerInfoValidator.
 *
 * TDD Red Phase — these tests are written BEFORE the implementation.
 * They should FAIL until PayerInfoValidator is created (T022).
 *
 * Coverage:
 * - Nome: required, 2–200 chars
 * - CPF: optional, delegated to PatientValidator.validateCpf when provided
 * - Email: optional, regex check when provided
 * - Only nome is required — all other fields can be absent with no error
 */
class PayerInfoValidatorTest {

    private lateinit var validator: PayerInfoValidator

    @Before
    fun setUp() {
        validator = PayerInfoValidator()
    }

    // ========================================
    // Nome validation
    // ========================================

    @Test
    fun validatePayerInfo_nomeVazio_retornaErro() {
        val errors = validator.validate(nome = "", cpf = null, email = null)
        assertTrue("Nome vazio deve retornar erro", errors.isNotEmpty())
        val nomeError = errors.find { it.field == "payerNome" }
        assertTrue("Deve existir erro no campo 'payerNome'", nomeError != null)
    }

    @Test
    fun validatePayerInfo_nomeComUmChar_retornaErro() {
        val errors = validator.validate(nome = "A", cpf = null, email = null)
        assertTrue("Nome com 1 char deve retornar erro", errors.isNotEmpty())
        val nomeError = errors.find { it.field == "payerNome" }
        assertTrue("Deve existir erro no campo 'payerNome'", nomeError != null)
    }

    @Test
    fun validatePayerInfo_nomeValido_semErros() {
        val errors = validator.validate(nome = "Maria Silva", cpf = null, email = null)
        assertEquals("Nome válido não deve retornar erros", 0, errors.size)
    }

    @Test
    fun validatePayerInfo_nomeMinimo_semErros() {
        val errors = validator.validate(nome = "Jo", cpf = null, email = null)
        assertEquals("Nome com 2 chars é válido", 0, errors.size)
    }

    @Test
    fun validatePayerInfo_nomeMaximoExcedido_retornaErro() {
        val nomeLongo = "A".repeat(201)
        val errors = validator.validate(nome = nomeLongo, cpf = null, email = null)
        assertTrue("Nome com 201 chars deve retornar erro", errors.isNotEmpty())
        val nomeError = errors.find { it.field == "payerNome" }
        assertTrue("Deve existir erro no campo 'payerNome'", nomeError != null)
    }

    // ========================================
    // CPF validation (optional, uses PatientValidator)
    // ========================================

    @Test
    fun validatePayerInfo_cpfNulo_semErros() {
        val errors = validator.validate(nome = "Maria Silva", cpf = null, email = null)
        assertEquals("CPF nulo é válido (opcional)", 0, errors.size)
    }

    @Test
    fun validatePayerInfo_cpfValido_semErros() {
        val errors = validator.validate(nome = "Maria Silva", cpf = "52998224725", email = null)
        assertEquals("CPF válido não deve retornar erros", 0, errors.size)
    }

    @Test
    fun validatePayerInfo_cpfInvalido_retornaErro() {
        val errors = validator.validate(nome = "Maria Silva", cpf = "11111111111", email = null)
        assertTrue("CPF inválido deve retornar erro", errors.isNotEmpty())
        val cpfError = errors.find { it.field == "payerCpf" }
        assertTrue("Deve existir erro no campo 'payerCpf'", cpfError != null)
    }

    @Test
    fun validatePayerInfo_cpfFormatadoValido_semErros() {
        val errors = validator.validate(nome = "Maria Silva", cpf = "529.982.247-25", email = null)
        assertEquals("CPF formatado válido não deve retornar erros", 0, errors.size)
    }

    // ========================================
    // Email validation (optional, regex check)
    // ========================================

    @Test
    fun validatePayerInfo_emailNulo_semErros() {
        val errors = validator.validate(nome = "Maria Silva", cpf = null, email = null)
        assertEquals("Email nulo é válido (opcional)", 0, errors.size)
    }

    @Test
    fun validatePayerInfo_emailValido_semErros() {
        val errors = validator.validate(nome = "Maria Silva", cpf = null, email = "maria@exemplo.com")
        assertEquals("Email válido não deve retornar erros", 0, errors.size)
    }

    @Test
    fun validatePayerInfo_emailInvalido_retornaErro() {
        val errors = validator.validate(nome = "Maria Silva", cpf = null, email = "email-invalido")
        assertTrue("Email inválido deve retornar erro", errors.isNotEmpty())
        val emailError = errors.find { it.field == "payerEmail" }
        assertTrue("Deve existir erro no campo 'payerEmail'", emailError != null)
    }

    // ========================================
    // Combined validation
    // ========================================

    @Test
    fun validatePayerInfo_apenasNomeInformado_semErros() {
        val errors = validator.validate(nome = "João Responsável", cpf = null, email = null)
        assertEquals("Apenas nome (obrigatório) é suficiente", 0, errors.size)
    }

    @Test
    fun validatePayerInfo_todosOsCamposValidos_semErros() {
        val errors = validator.validate(
            nome = "Maria Responsável",
            cpf = "52998224725",
            email = "maria@exemplo.com"
        )
        assertEquals("Todos os campos válidos não deve retornar erros", 0, errors.size)
    }
}
