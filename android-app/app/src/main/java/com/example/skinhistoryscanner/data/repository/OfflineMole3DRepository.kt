package com.example.skinhistoryscanner.data.repository

import com.example.skinhistoryscanner.data.domain.Mole3D
import com.example.skinhistoryscanner.data.domain.Mole3DMapItem
import com.example.skinhistoryscanner.data.domain.toDomain
import com.example.skinhistoryscanner.data.domain.toEntity
import com.example.skinhistoryscanner.data.local.room.Mole3DDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineMole3DRepository @Inject constructor(
    private val mole3DDao: Mole3DDao
) : Mole3DRepository {

    override fun getMolesAtDate(profile: String, date: LocalDate): Flow<List<Mole3DMapItem>> {
        return mole3DDao.getMolesAtDate(profile, date).map { dtos ->
            dtos.map { it.toDomain() }
        }
    }

    override suspend fun upsertMole(mole: Mole3D) {
        mole3DDao.insertMole(mole.toEntity())
    }

    override suspend fun updateMolePosition(moleId: String, newX: Float, newY: Float, newZ: Float) {
        mole3DDao.updateMolePosition(moleId, newX, newY, newZ)
    }

    override suspend fun deleteMole(moleId: String) {
        mole3DDao.deleteMole(moleId)
    }
}
