package com.example.skinhistoryscanner.data.repository

import com.example.skinhistoryscanner.data.domain.Mole3D
import com.example.skinhistoryscanner.data.domain.Mole3DMapItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface Mole3DRepository {
    fun getMolesAtDate(profile: String, date: LocalDate): Flow<List<Mole3DMapItem>>
    suspend fun upsertMole(mole: Mole3D)
    suspend fun updateMolePosition(moleId: String, newX: Float, newY: Float, newZ: Float)
    suspend fun deleteMole(moleId: String)
}
