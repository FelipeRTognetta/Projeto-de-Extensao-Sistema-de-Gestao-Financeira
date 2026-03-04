package com.psychologist.financial.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Jetpack Compose VisualTransformation for Brazilian CPF format (XXX.XXX.XXX-XX).
 *
 * The underlying state holds only raw digits (up to 11 characters).
 * This transformation displays the digits with the standard CPF mask applied:
 * - First 3 digits, then a dot '.'
 * - Next 3 digits, then a dot '.'
 * - Next 3 digits, then a dash '-'
 * - Last 2 digits
 *
 * Example: raw "12345678909" → displayed "123.456.789-09"
 *
 * Usage:
 * ```kotlin
 * OutlinedTextField(
 *     value = rawCpfDigits,   // max 11 digit chars
 *     onValueChange = { new -> rawCpfDigits = new.filter { it.isDigit() }.take(11) },
 *     visualTransformation = CpfVisualTransformation(),
 *     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
 * )
 * ```
 *
 * OffsetMapping contract:
 * Separators appear BEFORE digit groups starting at original positions 3, 6, and 9:
 *   - original[0..2]  → transformed[0..2]   (no separator yet)
 *   - original[3..5]  → transformed[4..6]   (+1 for the first dot at transformed[3])
 *   - original[6..8]  → transformed[8..10]  (+2 for two dots)
 *   - original[9..10] → transformed[12..13] (+3 for two dots + one dash)
 */
class CpfVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text.take(11)

        val out = buildString {
            raw.forEachIndexed { i, c ->
                if (i == 3 || i == 6) append('.')
                if (i == 9) append('-')
                append(c)
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, raw.length)
                return when {
                    clamped <= 2 -> clamped
                    clamped <= 5 -> clamped + 1
                    clamped <= 8 -> clamped + 2
                    else -> clamped + 3
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, out.length)
                return when {
                    clamped <= 2 -> clamped
                    clamped == 3 -> 2   // first dot → map to digit before it
                    clamped <= 6 -> clamped - 1
                    clamped == 7 -> 5   // second dot → map to digit before it
                    clamped <= 10 -> clamped - 2
                    clamped == 11 -> 8  // dash → map to digit before it
                    clamped <= 13 -> clamped - 3
                    else -> 10          // beyond end of 11 digits
                }
            }
        }

        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}
