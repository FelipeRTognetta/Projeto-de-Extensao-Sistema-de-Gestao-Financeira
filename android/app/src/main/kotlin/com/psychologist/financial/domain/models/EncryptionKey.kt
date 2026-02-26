package com.psychologist.financial.domain.models

import java.time.LocalDateTime

/**
 * Encryption Key Model
 *
 * Represents an encryption key with metadata for key management, rotation, and audit.
 * Tracks key lifecycle: creation, usage, rotation, expiration.
 *
 * Key Hierarchy:
 * - Master Key: Stored in Android Keystore (hardware-backed)
 * - Database Key: 256-bit AES-GCM key, encrypted with Master Key
 * - Backup Key: Separate key for backup/restore operations
 *
 * @property alias Unique identifier for the key in Android Keystore
 * @property keyMaterial Raw key bytes (256-bit = 32 bytes for AES-256)
 * @property algorithm Encryption algorithm (e.g., "AES/GCM/NoPadding")
 * @property keySize Size of key in bits (256 for AES-256)
 * @property createdAt When key was generated
 * @property lastRotatedAt When key was last rotated (if never, equals createdAt)
 * @property expiresAt When key expires and rotation is required
 * @property rotationIntervalDays Days between rotations (default 90)
 * @property isActive Whether key is currently in use
 * @property isMasterKey Whether this is the root Master Key or derived Database Key
 * @property requiresUserAuthentication Whether key access requires biometric/PIN
 * @property userAuthenticationValiditySeconds Seconds user auth remains valid after unlock
 */
data class EncryptionKey(
    val alias: String,
    val keyMaterial: ByteArray,
    val algorithm: String = "AES/GCM/NoPadding",
    val keySize: Int = 256,
    val createdAt: LocalDateTime,
    val lastRotatedAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val rotationIntervalDays: Int = 90,
    val isActive: Boolean = true,
    val isMasterKey: Boolean = false,
    val requiresUserAuthentication: Boolean = true,
    val userAuthenticationValiditySeconds: Int = 300, // 5 minutes
    val purpose: KeyPurpose = KeyPurpose.DATABASE
) {
    /**
     * Check if key is expired and rotation is required
     *
     * @return true if current time >= expiresAt
     */
    fun isExpired(): Boolean = LocalDateTime.now() >= expiresAt

    /**
     * Check if key is about to expire (within 7 days)
     *
     * @return true if expires within 7 days
     */
    fun isAboutToExpire(): Boolean {
        val warningThreshold = LocalDateTime.now().plusDays(7)
        return expiresAt <= warningThreshold && !isExpired()
    }

    /**
     * Get days remaining until expiration
     *
     * @return Number of days until key expires, or negative if already expired
     */
    fun getDaysUntilExpiration(): Long {
        val now = LocalDateTime.now()
        if (isExpired()) return -1
        val daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(now, expiresAt)
        return maxOf(0, daysRemaining)
    }

    /**
     * Get human-readable key status
     *
     * @return Status message (Active, Expired, About to Expire, Inactive)
     */
    fun getStatus(): String = when {
        !isActive -> "Inativo"
        isExpired() -> "Expirado (Rotação Necessária)"
        isAboutToExpire() -> "Expirando em ${getDaysUntilExpiration()} dias"
        else -> "Ativo"
    }

    /**
     * Get key metadata for logging/audit
     *
     * @return Map with key information (alias, created, expires, days remaining)
     */
    fun getMetadata(): Map<String, Any> = mapOf(
        "alias" to alias,
        "algorithm" to algorithm,
        "keySize" to keySize,
        "createdAt" to createdAt.toString(),
        "lastRotatedAt" to lastRotatedAt.toString(),
        "expiresAt" to expiresAt.toString(),
        "daysUntilExpiration" to getDaysUntilExpiration(),
        "isActive" to isActive,
        "isMasterKey" to isMasterKey,
        "purpose" to purpose.name,
        "status" to getStatus()
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptionKey) return false

        if (alias != other.alias) return false
        if (!keyMaterial.contentEquals(other.keyMaterial)) return false
        if (algorithm != other.algorithm) return false
        if (keySize != other.keySize) return false
        if (createdAt != other.createdAt) return false
        if (purpose != other.purpose) return false

        return true
    }

    override fun hashCode(): Int {
        var result = alias.hashCode()
        result = 31 * result + keyMaterial.contentHashCode()
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + keySize
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + purpose.hashCode()
        return result
    }

    companion object {
        /**
         * Generate a new encryption key with default 90-day rotation interval
         *
         * @param alias Unique key identifier
         * @param keyMaterial Raw key bytes (should be 32 bytes for AES-256)
         * @param isMasterKey Whether this is Master Key or Database Key
         * @param purpose Key purpose (Database, Backup, etc.)
         * @return New EncryptionKey with metadata
         */
        fun create(
            alias: String,
            keyMaterial: ByteArray,
            isMasterKey: Boolean = false,
            purpose: KeyPurpose = KeyPurpose.DATABASE
        ): EncryptionKey {
            val now = LocalDateTime.now()
            val expiresAt = now.plusDays(90)

            return EncryptionKey(
                alias = alias,
                keyMaterial = keyMaterial,
                createdAt = now,
                lastRotatedAt = now,
                expiresAt = expiresAt,
                isMasterKey = isMasterKey,
                purpose = purpose
            )
        }
    }
}

/**
 * Key Purpose Enum
 *
 * Identifies the purpose of the encryption key.
 */
enum class KeyPurpose {
    /**
     * Main key for SQLCipher database encryption
     */
    DATABASE,

    /**
     * Key for backup/restore operations
     */
    BACKUP,

    /**
     * Master key for encrypting other keys
     */
    MASTER,

    /**
     * Temporary key for specific session or operation
     */
    TEMPORARY
}
