package com.psychologist.financial.services

import com.psychologist.financial.data.entities.PaymentAppointmentCrossRef
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.models.PayerInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Unit tests for BackupExportService
 *
 * Coverage:
 * - serialize() → JSON contains all fields of all entities
 * - encrypt(json, password) + decrypt(bytes, password) → round-trip identical
 * - decrypt(bytes, wrongPassword) → throws exception
 * - Salt and IV are unique between distinct calls to encrypt()
 */
class BackupExportServiceTest {

    private lateinit var service: BackupExportService

    private val samplePatient = Patient.createForTesting(
        id = 1L,
        name = "Ana Costa",
        naoPagante = false
    )

    private val sampleAppointment = Appointment(
        id = 1L,
        patientId = 1L,
        date = LocalDate.of(2026, 3, 1),
        timeStart = LocalTime.of(9, 0),
        durationMinutes = 50,
        notes = "Sessão inicial",
        createdDate = LocalDateTime.of(2026, 3, 1, 8, 0)
    )

    private val samplePayment = Payment(
        id = 1L,
        patientId = 1L,
        amount = BigDecimal("150.00"),
        paymentDate = LocalDate.of(2026, 3, 1),
        appointmentIds = listOf(1L)
    )

    private val sampleCrossRef = PaymentAppointmentCrossRef(
        paymentId = 1L,
        appointmentId = 1L
    )

    private val samplePayerInfo = PayerInfo(
        id = 1L,
        patientId = 2L,
        nome = "Maria Responsavel",
        cpf = "98765432100",
        email = "maria@test.com",
        telefone = "(11) 9000-0001",
        endereco = "Rua das Flores, 10"
    )

    @Before
    fun setUp() {
        service = BackupExportService()
    }

    // ========================================
    // serialize() Tests
    // ========================================

    @Test
    fun `serialize returns JSON containing patient name`() {
        val bytes = service.serialize(
            patients = listOf(samplePatient),
            appointments = emptyList(),
            payments = emptyList(),
            paymentAppointments = emptyList(),
            payerInfos = emptyList()
        )
        val json = bytes.toString(Charsets.UTF_8)
        assertTrue("JSON should contain patient name", json.contains("Ana Costa"))
    }

    @Test
    fun `serialize returns JSON containing version and exportedAt`() {
        val bytes = service.serialize(
            patients = emptyList(),
            appointments = emptyList(),
            payments = emptyList(),
            paymentAppointments = emptyList(),
            payerInfos = emptyList()
        )
        val json = bytes.toString(Charsets.UTF_8)
        assertTrue("JSON should contain version field", json.contains("\"version\""))
        assertTrue("JSON should contain exportedAt field", json.contains("exportedAt"))
    }

    @Test
    fun `serialize includes appointment fields`() {
        val bytes = service.serialize(
            patients = listOf(samplePatient),
            appointments = listOf(sampleAppointment),
            payments = emptyList(),
            paymentAppointments = emptyList(),
            payerInfos = emptyList()
        )
        val json = bytes.toString(Charsets.UTF_8)
        assertTrue("JSON should contain appointment notes", json.contains("Sessão inicial"))
        assertTrue("JSON should contain appointment date", json.contains("2026-03-01"))
    }

    @Test
    fun `serialize includes payment amount`() {
        val bytes = service.serialize(
            patients = listOf(samplePatient),
            appointments = emptyList(),
            payments = listOf(samplePayment),
            paymentAppointments = listOf(sampleCrossRef),
            payerInfos = emptyList()
        )
        val json = bytes.toString(Charsets.UTF_8)
        assertTrue("JSON should contain payment amount", json.contains("150.00"))
    }

    @Test
    fun `serialize includes payerInfo data`() {
        val bytes = service.serialize(
            patients = emptyList(),
            appointments = emptyList(),
            payments = emptyList(),
            paymentAppointments = emptyList(),
            payerInfos = listOf(samplePayerInfo)
        )
        val json = bytes.toString(Charsets.UTF_8)
        assertTrue("JSON should contain payer name", json.contains("Maria Responsavel"))
        assertTrue("JSON should contain payer CPF", json.contains("98765432100"))
    }

    @Test
    fun `serialize includes all entity counts in JSON`() {
        val bytes = service.serialize(
            patients = listOf(samplePatient),
            appointments = listOf(sampleAppointment),
            payments = listOf(samplePayment),
            paymentAppointments = listOf(sampleCrossRef),
            payerInfos = listOf(samplePayerInfo)
        )
        val json = bytes.toString(Charsets.UTF_8)
        assertTrue("JSON should include patients array", json.contains("\"patients\""))
        assertTrue("JSON should include appointments array", json.contains("\"appointments\""))
        assertTrue("JSON should include payments array", json.contains("\"payments\""))
        assertTrue("JSON should include payerInfos array", json.contains("\"payerInfos\""))
        assertTrue("JSON should include paymentAppointments array", json.contains("\"paymentAppointments\""))
    }

    // ========================================
    // encrypt() + decrypt() Round-trip Tests
    // ========================================

    @Test
    fun `encrypt and decrypt round-trip produces identical plaintext`() {
        val plaintext = "Hello, backup world!".toByteArray(Charsets.UTF_8)
        val encrypted = service.encrypt(plaintext, "password123")
        val decrypted = service.decrypt(encrypted, "password123")
        assertArrayEquals("Decrypted content must match original", plaintext, decrypted)
    }

    @Test
    fun `encrypt and decrypt round-trip works with JSON payload`() {
        val payload = service.serialize(
            patients = listOf(samplePatient),
            appointments = listOf(sampleAppointment),
            payments = listOf(samplePayment),
            paymentAppointments = listOf(sampleCrossRef),
            payerInfos = listOf(samplePayerInfo)
        )
        val encrypted = service.encrypt(payload, "senhaSegura123")
        val decrypted = service.decrypt(encrypted, "senhaSegura123")
        assertArrayEquals("JSON payload should survive encryption round-trip", payload, decrypted)
    }

    // ========================================
    // decrypt() Wrong Password Tests
    // ========================================

    @Test(expected = Exception::class)
    fun `decrypt with wrong password throws exception`() {
        val plaintext = "sensitive data".toByteArray(Charsets.UTF_8)
        val encrypted = service.encrypt(plaintext, "correctPassword")
        service.decrypt(encrypted, "wrongPassword") // must throw
    }

    @Test
    fun `decrypt with wrong password does not return original plaintext`() {
        val plaintext = "sensitive data".toByteArray(Charsets.UTF_8)
        val encrypted = service.encrypt(plaintext, "correctPassword")
        var threw = false
        try {
            val result = service.decrypt(encrypted, "wrongPassword")
            // Should not reach here, but if it does, content must differ
            assertFalse(
                "Decryption with wrong password must not produce original plaintext",
                plaintext.contentEquals(result)
            )
        } catch (e: Exception) {
            threw = true
        }
        assertTrue("decrypt() with wrong password must throw an exception", threw)
    }

    // ========================================
    // Uniqueness Tests (Salt + IV)
    // ========================================

    @Test
    fun `two calls to encrypt produce different ciphertext due to random salt and IV`() {
        val plaintext = "same data".toByteArray(Charsets.UTF_8)
        val encrypted1 = service.encrypt(plaintext, "samePassword")
        val encrypted2 = service.encrypt(plaintext, "samePassword")
        assertFalse(
            "Each encryption must produce different output (random salt + IV)",
            encrypted1.contentEquals(encrypted2)
        )
    }
}
