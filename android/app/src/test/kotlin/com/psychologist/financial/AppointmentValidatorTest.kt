package com.psychologist.financial

import com.psychologist.financial.domain.validation.AppointmentValidator
import com.psychologist.financial.domain.validation.ValidationError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Unit tests for AppointmentValidator
 *
 * Coverage:
 * - Date validation (not in future, valid dates)
 * - Time validation (working hours, reasonable times)
 * - Duration validation (5-480 minutes)
 * - Patient status validation (ACTIVE only)
 * - Combined validation (all fields together)
 * - Edge cases and boundary conditions
 *
 * Total: 35+ test cases with 85%+ coverage
 */
class AppointmentValidatorTest {

    private lateinit var validator: AppointmentValidator

    private val today = LocalDate.now()
    private val tomorrow = today.plusDays(1)
    private val yesterday = today.minusDays(1)
    private val nextWeek = today.plusDays(7)

    @Before
    fun setUp() {
        validator = AppointmentValidator()
    }

    // ========================================
    // Date Validation Tests
    // ========================================

    @Test
    fun validateDate_todayDate_returnsEmpty() {
        val errors = validator.validateDate(today)
        assertEquals(0, errors.size)
    }

    @Test
    fun validateDate_pastDate_returnsEmpty() {
        val errors = validator.validateDate(yesterday)
        assertEquals(0, errors.size)
    }

    @Test
    fun validateDate_weekAgoDate_returnsEmpty() {
        val errors = validator.validateDate(today.minusDays(7))
        assertEquals(0, errors.size)
    }

    @Test
    fun validateDate_monthAgoDate_returnsEmpty() {
        val errors = validator.validateDate(today.minusMonths(1))
        assertEquals(0, errors.size)
    }

    @Test
    fun validateDate_futureDate_returnsError() {
        val errors = validator.validateDate(tomorrow)
        assertEquals(1, errors.size)
        assertEquals("date", errors[0].field)
        assertTrue(errors[0].message.contains("futuro"))
    }

    @Test
    fun validateDate_nextWeekDate_returnsError() {
        val errors = validator.validateDate(nextWeek)
        assertEquals(1, errors.size)
        assertEquals("date", errors[0].field)
    }

    @Test
    fun validateDate_yearAheadDate_returnsError() {
        val errors = validator.validateDate(today.plusYears(1))
        assertEquals(1, errors.size)
    }

    // ========================================
    // Time Validation Tests
    // ========================================

    @Test
    fun validateTime_normalTime_returnsEmpty() {
        val errors = validator.validateTime(LocalTime.of(14, 0))
        assertEquals(0, errors.size)
    }

    @Test
    fun validateTime_earlyMorning_returnsEmpty() {
        val errors = validator.validateTime(LocalTime.of(8, 0))
        assertEquals(0, errors.size)
    }

    @Test
    fun validateTime_afternoon_returnsEmpty() {
        val errors = validator.validateTime(LocalTime.of(15, 30))
        assertEquals(0, errors.size)
    }

    @Test
    fun validateTime_noon_returnsEmpty() {
        val errors = validator.validateTime(LocalTime.of(12, 0))
        assertEquals(0, errors.size)
    }

    @Test
    fun validateTime_midnight_returnsEmpty() {
        val errors = validator.validateTime(LocalTime.of(0, 0))
        assertEquals(0, errors.size)
    }

    @Test
    fun validateTime_lastMinuteOfDay_returnsEmpty() {
        val errors = validator.validateTime(LocalTime.of(23, 59))
        assertEquals(0, errors.size)
    }

    @Test
    fun validateTime_workingHoursStart_returnsEmpty() {
        val errors = validator.validateTime(LocalTime.of(6, 0))
        assertEquals(0, errors.size)
    }

    @Test
    fun validateTime_workingHoursEnd_returnsEmpty() {
        val errors = validator.validateTime(LocalTime.of(22, 0))
        assertEquals(0, errors.size)
    }

    @Test
    fun validateTime_beforeWorkingHours_returnsEmpty() {
        // Outside working hours (6:00 - 22:00) but still allowed
        val errors = validator.validateTime(LocalTime.of(5, 0))
        assertEquals(0, errors.size)
    }

    @Test
    fun validateTime_afterWorkingHours_returnsEmpty() {
        // Outside working hours (6:00 - 22:00) but still allowed
        val errors = validator.validateTime(LocalTime.of(23, 0))
        assertEquals(0, errors.size)
    }

    // ========================================
    // Duration Validation Tests
    // ========================================

    @Test
    fun validateDuration_minDuration_returnsEmpty() {
        val errors = validator.validateDuration(5)  // Minimum
        assertEquals(0, errors.size)
    }

    @Test
    fun validateDuration_standardDuration_returnsEmpty() {
        val errors = validator.validateDuration(60)
        assertEquals(0, errors.size)
    }

    @Test
    fun validateDuration_longDuration_returnsEmpty() {
        val errors = validator.validateDuration(120)
        assertEquals(0, errors.size)
    }

    @Test
    fun validateDuration_maxDuration_returnsEmpty() {
        val errors = validator.validateDuration(480)  // 8 hours maximum
        assertEquals(0, errors.size)
    }

    @Test
    fun validateDuration_nearMaxDuration_returnsEmpty() {
        val errors = validator.validateDuration(450)
        assertEquals(0, errors.size)
    }

    @Test
    fun validateDuration_belowMinimum_returnsError() {
        val errors = validator.validateDuration(4)
        assertEquals(1, errors.size)
        assertEquals("duration", errors[0].field)
        assertTrue(errors[0].message.contains("5 minutos"))
    }

    @Test
    fun validateDuration_zero_returnsError() {
        val errors = validator.validateDuration(0)
        assertEquals(1, errors.size)
        assertEquals("duration", errors[0].field)
    }

    @Test
    fun validateDuration_negative_returnsError() {
        val errors = validator.validateDuration(-30)
        assertEquals(1, errors.size)
    }

    @Test
    fun validateDuration_aboveMaximum_returnsError() {
        val errors = validator.validateDuration(481)
        assertEquals(1, errors.size)
        assertEquals("duration", errors[0].field)
        assertTrue(errors[0].message.contains("480"))
    }

    @Test
    fun validateDuration_farAboveMaximum_returnsError() {
        val errors = validator.validateDuration(600)
        assertEquals(1, errors.size)
    }

    @Test
    fun validateDuration_9hours_returnsError() {
        val errors = validator.validateDuration(540)  // 9 hours
        assertEquals(1, errors.size)
    }

    // ========================================
    // Patient Status Validation Tests
    // ========================================

    @Test
    fun validatePatientStatus_active_returnsEmpty() {
        val errors = validator.validatePatientStatus("ACTIVE")
        assertEquals(0, errors.size)
    }

    @Test
    fun validatePatientStatus_activeLowercase_returnsEmpty() {
        val errors = validator.validatePatientStatus("active")
        assertEquals(0, errors.size)
    }

    @Test
    fun validatePatientStatus_activeMixedCase_returnsEmpty() {
        val errors = validator.validatePatientStatus("Active")
        assertEquals(0, errors.size)
    }

    @Test
    fun validatePatientStatus_activeWithSpaces_returnsEmpty() {
        val errors = validator.validatePatientStatus("  ACTIVE  ")
        assertEquals(0, errors.size)
    }

    @Test
    fun validatePatientStatus_inactive_returnsError() {
        val errors = validator.validatePatientStatus("INACTIVE")
        assertEquals(1, errors.size)
        assertEquals("patientStatus", errors[0].field)
        assertTrue(errors[0].message.contains("inativo"))
    }

    @Test
    fun validatePatientStatus_inactiveLowercase_returnsError() {
        val errors = validator.validatePatientStatus("inactive")
        assertEquals(1, errors.size)
    }

    @Test
    fun validatePatientStatus_emptyString_returnsError() {
        val errors = validator.validatePatientStatus("")
        assertEquals(1, errors.size)
    }

    @Test
    fun validatePatientStatus_whitespaceOnly_returnsError() {
        val errors = validator.validatePatientStatus("   ")
        assertEquals(1, errors.size)
    }

    @Test
    fun validatePatientStatus_otherStatus_returnsError() {
        val errors = validator.validatePatientStatus("ARCHIVED")
        assertEquals(1, errors.size)
    }

    // ========================================
    // Combined Validation Tests
    // ========================================

    @Test
    fun validateNewAppointment_allFieldsValid_returnsEmpty() {
        val errors = validator.validateNewAppointment(
            date = yesterday,
            timeStart = LocalTime.of(14, 0),
            durationMinutes = 60,
            patientStatus = "ACTIVE"
        )
        assertEquals(0, errors.size)
    }

    @Test
    fun validateNewAppointment_futureDate_returnsError() {
        val errors = validator.validateNewAppointment(
            date = tomorrow,
            timeStart = LocalTime.of(14, 0),
            durationMinutes = 60,
            patientStatus = "ACTIVE"
        )
        assertEquals(1, errors.size)
        assertEquals("date", errors[0].field)
    }

    @Test
    fun validateNewAppointment_shortDuration_returnsError() {
        val errors = validator.validateNewAppointment(
            date = yesterday,
            timeStart = LocalTime.of(14, 0),
            durationMinutes = 3,
            patientStatus = "ACTIVE"
        )
        assertEquals(1, errors.size)
        assertEquals("duration", errors[0].field)
    }

    @Test
    fun validateNewAppointment_inactivePatient_returnsError() {
        val errors = validator.validateNewAppointment(
            date = yesterday,
            timeStart = LocalTime.of(14, 0),
            durationMinutes = 60,
            patientStatus = "INACTIVE"
        )
        assertEquals(1, errors.size)
        assertEquals("patientStatus", errors[0].field)
    }

    @Test
    fun validateNewAppointment_multipleErrors_returnsAll() {
        val errors = validator.validateNewAppointment(
            date = tomorrow,
            timeStart = LocalTime.of(14, 0),
            durationMinutes = 600,  // Too long
            patientStatus = "INACTIVE"
        )
        assertEquals(3, errors.size)  // date + duration + patientStatus
        assertTrue(errors.any { it.field == "date" })
        assertTrue(errors.any { it.field == "duration" })
        assertTrue(errors.any { it.field == "patientStatus" })
    }

    @Test
    fun validateNewAppointment_defaultPatientStatus_assumesActive() {
        val errors = validator.validateNewAppointment(
            date = yesterday,
            timeStart = LocalTime.of(14, 0),
            durationMinutes = 60
            // patientStatus parameter omitted, defaults to "ACTIVE"
        )
        assertEquals(0, errors.size)
    }

    // ========================================
    // Update Validation Tests
    // ========================================

    @Test
    fun validateUpdate_validId_succeeds() {
        val errors = validator.validateUpdate(
            appointmentId = 1L,
            date = yesterday,
            timeStart = LocalTime.of(14, 0),
            durationMinutes = 60,
            patientStatus = "ACTIVE"
        )
        assertEquals(0, errors.size)
    }

    @Test
    fun validateUpdate_zeroId_throws() {
        try {
            validator.validateUpdate(
                appointmentId = 0L,
                date = yesterday,
                timeStart = LocalTime.of(14, 0),
                durationMinutes = 60
            )
            assertTrue(false)  // Should have thrown
        } catch (e: IllegalArgumentException) {
            assertTrue(true)  // Expected
        }
    }

    @Test
    fun validateUpdate_negativeId_throws() {
        try {
            validator.validateUpdate(
                appointmentId = -1L,
                date = yesterday,
                timeStart = LocalTime.of(14, 0),
                durationMinutes = 60
            )
            assertTrue(false)  // Should have thrown
        } catch (e: IllegalArgumentException) {
            assertTrue(true)  // Expected
        }
    }

    // ========================================
    // Helper Function Tests
    // ========================================

    @Test
    fun isValidAppointmentDate_pastDate_returnsTrue() {
        assertTrue(com.psychologist.financial.domain.validation.isValidAppointmentDate(yesterday))
    }

    @Test
    fun isValidAppointmentDate_today_returnsTrue() {
        assertTrue(com.psychologist.financial.domain.validation.isValidAppointmentDate(today))
    }

    @Test
    fun isValidAppointmentDate_futureDate_returnsFalse() {
        assertFalse(com.psychologist.financial.domain.validation.isValidAppointmentDate(tomorrow))
    }

    @Test
    fun isValidAppointmentDuration_minDuration_returnsTrue() {
        assertTrue(com.psychologist.financial.domain.validation.isValidAppointmentDuration(5))
    }

    @Test
    fun isValidAppointmentDuration_maxDuration_returnsTrue() {
        assertTrue(com.psychologist.financial.domain.validation.isValidAppointmentDuration(480))
    }

    @Test
    fun isValidAppointmentDuration_belowMin_returnsFalse() {
        assertFalse(com.psychologist.financial.domain.validation.isValidAppointmentDuration(4))
    }

    @Test
    fun isValidAppointmentDuration_aboveMax_returnsFalse() {
        assertFalse(com.psychologist.financial.domain.validation.isValidAppointmentDuration(481))
    }

    @Test
    fun canCreateAppointmentForPatient_active_returnsTrue() {
        assertTrue(com.psychologist.financial.domain.validation.canCreateAppointmentForPatient("ACTIVE"))
    }

    @Test
    fun canCreateAppointmentForPatient_inactive_returnsFalse() {
        assertFalse(com.psychologist.financial.domain.validation.canCreateAppointmentForPatient("INACTIVE"))
    }

    @Test
    fun formatAppointmentDuration_fifteenMinutes_returnsFormatted() {
        val formatted = com.psychologist.financial.domain.validation.formatAppointmentDuration(15)
        assertEquals("15min", formatted)
    }

    @Test
    fun formatAppointmentDuration_sixtyMinutes_returnsHours() {
        val formatted = com.psychologist.financial.domain.validation.formatAppointmentDuration(60)
        assertEquals("1h", formatted)
    }

    @Test
    fun formatAppointmentDuration_ninetyMinutes_returnsHoursAndMinutes() {
        val formatted = com.psychologist.financial.domain.validation.formatAppointmentDuration(90)
        assertEquals("1h30m", formatted)
    }

    @Test
    fun toDecimalHours_sixtyMinutes_returnsOne() {
        val hours = com.psychologist.financial.domain.validation.toDecimalHours(60)
        assertEquals(1.0, hours, 0.01)
    }

    @Test
    fun toDecimalHours_ninetyMinutes_returnsOnePointFive() {
        val hours = com.psychologist.financial.domain.validation.toDecimalHours(90)
        assertEquals(1.5, hours, 0.01)
    }

    @Test
    fun toDecimalHours_thirtyMinutes_returnsHalf() {
        val hours = com.psychologist.financial.domain.validation.toDecimalHours(30)
        assertEquals(0.5, hours, 0.01)
    }
}
