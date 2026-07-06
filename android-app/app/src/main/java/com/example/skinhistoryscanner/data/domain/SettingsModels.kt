package com.example.skinhistoryscanner.data.domain
import kotlinx.serialization.Serializable

enum class Gender { MALE, FEMALE }

enum class BodyType { SLIM, OVERWEIGHT }

enum class PdfQuality { LOW, MEDIUM, HIGH }

@Serializable
data class UserSettings(
    val gender: Gender = Gender.MALE,
    val bodyType: BodyType = BodyType.SLIM,
    val pdfQuality: PdfQuality = PdfQuality.MEDIUM,
    val openPdfAutomatically: Boolean = false,
    val showExportDialog: Boolean = true
)

enum class ReminderUnit { DAYS, MONTHS }
@Serializable
data class ReminderSettings(
    val enabled: Boolean = false,
    val intervalValue: Int = 1,
    val intervalUnit: ReminderUnit = ReminderUnit.MONTHS,
    val lastReminderDate: String? = null
)
@Serializable
data class ColorSetting(
    val hex: String,
    val label: String,
    val visible: Boolean = true
)
