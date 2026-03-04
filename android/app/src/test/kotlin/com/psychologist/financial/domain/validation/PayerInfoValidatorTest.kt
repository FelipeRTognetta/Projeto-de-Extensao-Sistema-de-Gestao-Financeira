package com.psychologist.financial.domain.validation

import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        assertTrue(errors.isNotEmpty(), "Nome vazio deve retornar erro")
        val nomeError = errors.find { it.field == "payerNome" }
        assertTrue(nomeError != null, "Deve existir erro no campo 'payerNome'")
    }

    @Test
    fun validatePayerInfo_nomeComUmChar_retornaErro() {
        val errors = validator.validate(nome = "A", cpf = null, email = null)
        assertTrue(errors.isNotEmpty(), "Nome com 1 char deve retornar erro")
        val nomeError = errors.find { it.field == "payerNome" }
        assertTrue(nomeError != null, "Deve existir erro no campo 'payerNome'")
    }

    @Test
    fun validatePayerInfo_nomeValido_semErros() {
        val errors = validator.validate(nome = "Maria Silva", cpf = null, email = null)
        assertEquals(0, errors.size, "Nome válido não deve retornar erros")
    }

    @Test
    fun validatePayerInfo_nomeMinimo_semErros() {
        val errors = validator.validate(nome = "Jo", cpf = null, email = null)
        assertEquals(0, errors.size, "Nome com 2 chars é válido")
    }

    @Test
    fun validatePayerInfo_nomeMaximoExcedido_retornaErro() {
        val nomeLongo = "A".repeat(201)
        val errors = validator.validate(nome = nomeLongo, cpf = null, email = null)
        assertTrue(errors.isNotEmpty(), "Nome com 201 chars deve retornar erro")
        val nomeError = errors.find { it.field == "payerNome" }
        assertTrue(nomeError != null, "Deve existir erro no campo 'payerNome'")
    }

    // ========================================
    // CPF validation (optional, uses PatientValidator)
    // ========================================

    @Test
    fun validatePayerInfo_cpfNulo_semErros() {
        val errors = validator.validate(nome = "Maria Silva", cpf = null, email = null)
        assertEquals(0, errors.size, "CPF nulo é válido (opcional)")
    }

    @Test
    fun validatePayerInfo_cpfValido_semErros() {
        val errors = validator.validate(nome = "Maria Silva", cpf = "52998224725", email = null)
        assertEquals(0, errors.size, "CPF válido não deve retornar erros")
    }

    @Test
    fun validatePayerInfo_cpfInvalido_retornaErro() {
        val errors = validator.validate(nome = "Maria Silva", cpf = "11111111111", email = null)
        assertTrue(errors.isNotEmpty(), "CPF inválido deve retornar erro")
        val cpfError = errors.find { it.field == "payerCpf" }
        assertTrue(cpfError != null, "Deve existir erro no campo 'payerCpf'")
    }

    @Test
    fun validatePayerInfo_cpfFormatadoValido_semErros() {
        val errors = validator.validate(nome = "Maria Silva", cpf = "529.982.247-25", email = null)
        assertEquals(0, errors.size, "CPF formatado válido não deve retornar erros")
    }

    // ========================================
    // Email validation (optional, regex check)
    // ========================================

    @Test
    fun validatePayerInfo_emailNulo_semErros() {
        val errors = validator.validate(nome = "Maria Silva", cpf = null, email = null)
        assertEquals(0, errors.size, "Email nulo é válido (opcional)")
    }

    @Test
    fun validatePayerInfo_emailValido_semErros() {
        val errors = validator.validate(nome = "Maria Silva", cpf = null, email = "maria@exemplo.com")
        assertEquals(0, errors.size, "Email válido não deve retornar erros")
    }

    @Test
    fun validatePayerInfo_emailInvalido_retornaErro() {
        val errors = validator.validate(nome = "Maria Silva", cpf = null, email = "email-invalido")
        assertTrue(errors.isNotEmpty(), "Email inválido deve retornar erro")
        val emailError = errors.find { it.field == "payerEmail" }
        assertTrue(emailError != null, "Deve existir erro no campo 'payerEmail'")
    }

    // ========================================
    // Combined validation
    // ========================================

    @Test
    fun validatePayerInfo_apenasNomeInformado_semErros() {
        val errors = validator.validate(nome = "João Responsável", cpf = null, email = null)
        assertEquals(0, errors.size, "Apenas nome (obrigatório) é suficiente")
    }

    @Test
    fun validatePayerInfo_todosOsCamposValidos_semErros() {
        val errors = validator.validate(
            nome = "Maria Responsável",
            cpf = "52998224725",
            email = "maria@exemplo.com"
        )
        assertEquals(0, errors.size, "Todos os campos válidos não deve retornar erros")
    }
}
