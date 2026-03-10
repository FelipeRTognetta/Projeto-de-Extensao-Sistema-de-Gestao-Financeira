package com.psychologist.financial.services

import com.psychologist.financial.data.entities.PaymentAppointmentCrossRef
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.models.PayerInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup Export Service
 *
 * Handles serialization and encryption of full database backup.
 * Produces a portable `.pgfbackup` file that can be restored on any device.
 *
 * Responsibilities:
 * - Serialize all entity data to a JSON byte array (kotlinx.serialization)
 * - Encrypt JSON with PBKDF2WithHmacSHA256 (100k iterations) + AES-256-GCM
 * - Write/read the envelope: [4B saltSize][salt][4B ivSize][iv][ciphertext]
 *
 * Encryption design:
 * - Password-based key derivation (PBKDF2) — no Android Keystore dependency
 * - AES-256-GCM authenticated encryption — detects tampering or wrong password
 * - Salt (32 bytes) and IV (12 bytes) are randomized per export
 * - 100,000 PBKDF2 iterations (OWASP 2024 minimum for SHA-256)
 *
 * Usage:
 * ```kotlin
 * val service = BackupExportService()
 * val json = service.serialize(patients, appointments, payments, crossRefs, payerInfos)
 * val encrypted = service.encrypt(json, password)
 * val decrypted = service.decrypt(encrypted, password) // same password
 * ```
 */
class BackupExportService {

    private companion object {
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val AES_ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val KEY_LENGTH_BITS = 256
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val PBKDF2_ITERATIONS = 100_000
        private const val SALT_SIZE = 32
        private val isoDateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ========================================
    // Serialization
    // ========================================

    /**
     * Serialize all backup data to a UTF-8 JSON byte array.
     *
     * The JSON structure mirrors the backup version 3 schema:
     * - version: 3 (current DATABASE_VERSION)
     * - patients, appointments, payments, paymentAppointments, payerInfos
     *
     * Java time types (LocalDate, LocalDateTime, LocalTime) are serialized as strings.
     * BigDecimal is serialized as plain string to preserve precision.
     *
     * @param patients All Patient domain models
     * @param appointments All Appointment domain models
     * @param payments All Payment domain models
     * @param paymentAppointments All PaymentAppointmentCrossRef entities
     * @param payerInfos All PayerInfo domain models
     * @return UTF-8 JSON byte array
     */
    fun serialize(
        patients: List<Patient>,
        appointments: List<Appointment>,
        payments: List<Payment>,
        paymentAppointments: List<PaymentAppointmentCrossRef>,
        payerInfos: List<PayerInfo>
    ): ByteArray {
        val data = BackupData(
            version = 3,
            appVersion = "1.0",
            exportedAt = LocalDateTime.now().format(isoDateTimeFormatter),
            patients = patients.map { it.toBackup() },
            appointments = appointments.map { it.toBackup() },
            payments = payments.map { it.toBackup() },
            paymentAppointments = paymentAppointments.map {
                PaymentAppointmentBackup(it.paymentId, it.appointmentId)
            },
            payerInfos = payerInfos.map { it.toBackup() }
        )
        return json.encodeToString(data).toByteArray(Charsets.UTF_8)
    }

    // ========================================
    // Encryption
    // ========================================

    /**
     * Encrypt plaintext bytes with a password using PBKDF2+AES-256-GCM.
     *
     * Generates random 32-byte salt and 12-byte IV per call.
     * Writes envelope: [4B saltSize][salt][4B ivSize][iv][ciphertext]
     *
     * @param plaintext Data to encrypt (JSON bytes)
     * @param password User password for key derivation
     * @return Encrypted envelope bytes
     */
    fun encrypt(plaintext: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password.toCharArray(), salt)

        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)

        val buf = ByteArrayOutputStream()
        val dos = DataOutputStream(buf)
        dos.writeInt(salt.size)
        dos.write(salt)
        dos.writeInt(iv.size)
        dos.write(iv)
        dos.write(ciphertext)
        dos.flush()
        return buf.toByteArray()
    }

    /**
     * Decrypt envelope bytes produced by [encrypt].
     *
     * Extracts salt and IV from the envelope, derives the key using PBKDF2,
     * and decrypts with AES-256-GCM. AEADBadTagException is thrown (as a
     * general Exception) if the password is wrong or the data is corrupt.
     *
     * @param fileBytes Encrypted envelope bytes
     * @param password User password for key derivation
     * @return Decrypted plaintext bytes
     * @throws Exception if password is wrong or data is corrupt
     */
    fun decrypt(fileBytes: ByteArray, password: String): ByteArray {
        val dis = DataInputStream(ByteArrayInputStream(fileBytes))
        val saltSize = dis.readInt()
        val salt = ByteArray(saltSize)
        dis.readFully(salt)
        val ivSize = dis.readInt()
        val iv = ByteArray(ivSize)
        dis.readFully(iv)
        val ciphertext = dis.readBytes()

        val key = deriveKey(password.toCharArray(), salt)
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    // ========================================
    // Private Helpers
    // ========================================

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, KEY_ALGORITHM)
    }

    private fun Patient.toBackup() = PatientBackup(
        id = id,
        name = name,
        phone = phone,
        email = email,
        status = status.name,
        initialConsultDate = initialConsultDate.toString(),
        registrationDate = registrationDate.toString(),
        lastAppointmentDate = lastAppointmentDate?.toString(),
        cpf = cpf,
        endereco = endereco,
        naoPagante = naoPagante,
        createdDate = createdDate.format(isoDateTimeFormatter)
    )

    private fun Appointment.toBackup() = AppointmentBackup(
        id = id,
        patientId = patientId,
        date = date.toString(),
        timeStart = timeStart.toString(),
        durationMinutes = durationMinutes,
        notes = notes,
        createdDate = createdDate.format(isoDateTimeFormatter)
    )

    private fun Payment.toBackup() = PaymentBackup(
        id = id,
        patientId = patientId,
        amount = amount.toPlainString(),
        paymentDate = paymentDate.toString(),
        createdDate = createdDate.format(isoDateTimeFormatter)
    )

    private fun PayerInfo.toBackup() = PayerInfoBackup(
        id = id,
        patientId = patientId,
        nome = nome,
        cpf = cpf,
        email = email,
        telefone = telefone,
        endereco = endereco
    )
}

// ========================================
// Serializable Backup Data Classes
// ========================================

@Serializable
data class BackupData(
    val version: Int,
    val appVersion: String,
    val exportedAt: String,
    val patients: List<PatientBackup>,
    val appointments: List<AppointmentBackup>,
    val payments: List<PaymentBackup>,
    val paymentAppointments: List<PaymentAppointmentBackup>,
    val payerInfos: List<PayerInfoBackup>
)

@Serializable
data class PatientBackup(
    val id: Long,
    val name: String,
    val phone: String?,
    val email: String?,
    val status: String,
    val initialConsultDate: String,
    val registrationDate: String,
    val lastAppointmentDate: String?,
    val cpf: String?,
    val endereco: String?,
    val naoPagante: Boolean,
    val createdDate: String
)

@Serializable
data class AppointmentBackup(
    val id: Long,
    val patientId: Long,
    val date: String,
    val timeStart: String,
    val durationMinutes: Int,
    val notes: String?,
    val createdDate: String
)

@Serializable
data class PaymentBackup(
    val id: Long,
    val patientId: Long,
    val amount: String,
    val paymentDate: String,
    val createdDate: String
)

@Serializable
data class PaymentAppointmentBackup(
    val paymentId: Long,
    val appointmentId: Long
)

@Serializable
data class PayerInfoBackup(
    val id: Long,
    val patientId: Long,
    val nome: String,
    val cpf: String?,
    val email: String?,
    val telefone: String?,
    val endereco: String?
)
