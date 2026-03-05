package com.psychologist.financial.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room database entity for Responsável Financeiro (Payer Information).
 *
 * Represents the person responsible for paying for a patient's sessions
 * when the patient is marked as non-paying (naoPagante = true).
 *
 * Database Table: "payer_info"
 * Primary Key: id (auto-increment)
 * Foreign Key: patient_id → patient.id (UNIQUE, CASCADE DELETE)
 *
 * Relationship: 1 Patient ↔ 0..1 PayerInfo
 * - One patient can have at most one Responsável Financeiro.
 * - The UNIQUE constraint on patient_id enforces the 1:0..1 relationship.
 * - ON DELETE CASCADE removes payer info when the associated patient is deleted.
 *
 * Constraints:
 * - patient_id: NOT NULL, UNIQUE, FK to patient.id
 * - nome: NOT NULL (required field)
 * - cpf, endereco, email, telefone: all nullable (optional fields)
 * - cpf uniqueness: NOT enforced globally (same person can be responsible for multiple patients)
 *
 * @property id Unique identifier (auto-increment primary key)
 * @property patientId FK to patient.id — identifies the non-paying patient
 * @property nome Full name of the responsible person (required)
 * @property cpf Brazilian tax ID of the responsible person (optional, 11 raw digits)
 * @property endereco Address of the responsible person (optional, free text)
 * @property email Email of the responsible person (optional)
 * @property telefone Phone number of the responsible person (optional)
 */
@Entity(
    tableName = "payer_info",
    indices = [
        Index(name = "idx_payer_info_patient_id", value = ["patient_id"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patient_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PayerInfoEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** FK to patient.id — must be unique (one payer per patient) */
    @ColumnInfo(name = "patient_id")
    val patientId: Long,

    /** Full name of the responsible person. Required. */
    @ColumnInfo(name = "nome")
    val nome: String,

    /** Brazilian tax ID (11 raw digits). Optional. */
    @ColumnInfo(name = "cpf")
    val cpf: String? = null,

    /** Free-text address. Optional. */
    @ColumnInfo(name = "endereco")
    val endereco: String? = null,

    /** Email address. Optional. */
    @ColumnInfo(name = "email")
    val email: String? = null,

    /** Phone number. Optional. */
    @ColumnInfo(name = "telefone")
    val telefone: String? = null
)
