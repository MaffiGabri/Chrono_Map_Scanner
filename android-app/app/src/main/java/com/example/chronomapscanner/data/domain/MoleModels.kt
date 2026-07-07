package com.example.chronomapscanner.data.domain

import com.example.chronomapscanner.data.local.room.HistoryEntryEntity
import com.example.chronomapscanner.data.local.room.MoleEntity
import com.example.chronomapscanner.data.local.room.MoleWithHistory

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.time.LocalDate

enum class BodySide {
    @SerialName("front") FRONT, 
    @SerialName("back") BACK;
    
    companion object {
        fun fromString(value: String): BodySide {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: FRONT
        }
    }
}

/**
 * Domain model for a History Entry.
 * Decoupled from the Room entity to allow flexibility in the UI layer.
 */
@Immutable
@Serializable
data class HistoryEntry(
    val id: String,
    val moleId: String,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val imagePath: String?,
    val notes: String?
)

/**
 * Domain model for a Mole.
 * Includes its full history of entries.
 */
@Immutable
@Serializable
data class Mole(
    val id: String,
    val profileName: String,
    val x: Float,
    val y: Float,
    val side: String,
    val color: String,
    val history: List<HistoryEntry> = emptyList()
)

/**
 * Domain model for flattened Map Item.
 * Represents a single history snapshot for a mole.
 */
@Immutable
@Serializable
data class MoleMapItem(
    val id: String,
    val x: Float,
    val y: Float,
    val side: String,
    val color: String,
    @Serializable(with = LocalDateSerializer::class) val historyDate: LocalDate?,
    val imagePath: String?
)



/**
 * Mappers to convert Room Entities/POJOs to Domain Models.
 */

fun HistoryEntryEntity.toDomain(): HistoryEntry = HistoryEntry(
    id = id,
    moleId = moleId,
    date = date,
    imagePath = imagePath,
    notes = notes
)

fun MoleEntity.toDomain(history: List<HistoryEntryEntity> = emptyList()): Mole = Mole(
    id = id,
    profileName = profileName,
    x = x,
    y = y,
    side = variantId,
    color = color,
    history = history.map { it.toDomain() }
)

fun MoleWithHistory.toDomain(): Mole = mole.toDomain(history)

fun com.example.chronomapscanner.data.local.room.MoleMapDto.toDomain(): MoleMapItem = MoleMapItem(
    id = id,
    x = x,
    y = y,
    side = variantId,
    color = color,
    historyDate = historyDate,
    imagePath = imagePath
)



/**
 * Mappers to convert Domain Models to Room Entities.
 */

fun HistoryEntry.toEntity(): HistoryEntryEntity = HistoryEntryEntity(
    id = id,
    moleId = moleId,
    date = date,
    imagePath = imagePath,
    notes = notes
)

fun Mole.toEntity(): MoleEntity = MoleEntity(
    id = id,
    profileName = profileName,
    x = x,
    y = y,
    variantId = side,
    color = color
)
