package com.psychologist.financial.utils

import java.text.Normalizer

/**
 * Normalizes a string for accent-insensitive search.
 *
 * Decomposes combined characters (NFD), strips all combining diacritical marks
 * (accents, cedillas, tildes, etc.), and lowercases the result.
 *
 * Examples:
 *   "José"    → "jose"
 *   "Ângela"  → "angela"
 *   "Conceição" → "conceicao"
 *   "joão"    → "joao"
 */
fun String.normalizeForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
        .lowercase()
