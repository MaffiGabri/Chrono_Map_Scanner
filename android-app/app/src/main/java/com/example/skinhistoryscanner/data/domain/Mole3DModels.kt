package com.example.skinhistoryscanner.data.domain

import com.example.skinhistoryscanner.data.local.room.HistoryEntry3DEntity
import com.example.skinhistoryscanner.data.local.room.Mole3DEntity
import com.example.skinhistoryscanner.data.local.room.Mole3DMapDto

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Immutable
@Serializable
data class Mole3D(
    val id: String,
    val profileName: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val modelId: String,
    val color: String
)

@Immutable
@Serializable
data class Mole3DMapItem(
    val id: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val modelId: String,
    val color: String,
    @Serializable(with = LocalDateSerializer::class) val historyDate: LocalDate?,
    val imagePath: String?
)

fun Mole3DEntity.toDomain(): Mole3D = Mole3D(
    id = id,
    profileName = profileName,
    x = x,
    y = y,
    z = z,
    modelId = modelId,
    color = color
)

fun Mole3D.toEntity(): Mole3DEntity = Mole3DEntity(
    id = id,
    profileName = profileName,
    x = x,
    y = y,
    z = z,
    modelId = modelId,
    color = color
)

fun Mole3DMapDto.toDomain(): Mole3DMapItem = Mole3DMapItem(
    id = id,
    x = x,
    y = y,
    z = z,
    modelId = modelId,
    color = color,
    historyDate = historyDate,
    imagePath = imagePath
)
