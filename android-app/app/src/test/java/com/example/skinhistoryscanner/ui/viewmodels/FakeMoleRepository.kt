package com.example.skinhistoryscanner.ui.viewmodels

import com.example.skinhistoryscanner.data.domain.HistoryEntry
import com.example.skinhistoryscanner.data.domain.Mole
import com.example.skinhistoryscanner.data.repository.MoleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeMoleRepository : MoleRepository {
    private val moles = MutableStateFlow<List<Mole>>(emptyList())
    private val history = MutableStateFlow<List<HistoryEntry>>(emptyList())

    override fun getMolesWithHistory(profileName: String): Flow<List<Mole>> {
        return moles.map { allMoles ->
            allMoles.filter { it.profileName == profileName }.map { mole ->
                val moleHistory = history.value.filter { it.moleId == mole.id }
                mole.copy(history = moleHistory)
            }
        }
    }

    override fun getMolesForMap(profileName: String, side: com.example.skinhistoryscanner.data.domain.BodySide, maxDate: String, colors: List<String>): Flow<List<com.example.skinhistoryscanner.data.domain.MoleMapSummary>> {
        return moles.map { allMoles ->
            allMoles.filter { it.profileName == profileName && it.side == side && it.color in colors }
                .map { mole ->
                    com.example.skinhistoryscanner.data.domain.MoleMapSummary(
                        id = mole.id,
                        profileName = mole.profileName,
                        x = mole.x,
                        y = mole.y,
                        side = mole.side,
                        color = mole.color,
                        latestPhotoPath = history.value.filter { it.moleId == mole.id && it.date <= maxDate }.maxByOrNull { it.date }?.imagePath
                    )
                }
        }
    }

    override fun getMoleByIdWithHistory(moleId: String): Flow<Mole?> {
        return moles.map { allMoles ->
            val mole = allMoles.find { it.id == moleId }
            if (mole != null) {
                mole.copy(history = history.value.filter { it.moleId == mole.id })
            } else {
                null
            }
        }
    }

    override fun getAllProfileNames(): Flow<List<String>> {
        return moles.map { allMoles -> allMoles.map { it.profileName }.distinct() }
    }

    override fun getMolesCountForProfile(profileName: String): Flow<Int> {
        return moles.map { allMoles -> allMoles.count { it.profileName == profileName } }
    }

    override fun getTotalMolesCount(): Flow<Int> {
        return moles.map { it.size }
    }

    override suspend fun upsertMole(mole: Mole) {
        val current = moles.value.toMutableList()
        current.removeIf { it.id == mole.id }
        current.add(mole)
        moles.value = current
    }

    override suspend fun updateMolePosition(id: String, x: Float, y: Float, side: com.example.skinhistoryscanner.data.domain.BodySide) {
        val current = moles.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            current[index] = current[index].copy(x = x, y = y, side = side)
        }
        moles.value = current
    }

    override suspend fun updateMoleColor(id: String, color: String) {
        val current = moles.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            current[index] = current[index].copy(color = color)
        }
        moles.value = current
    }

    override suspend fun insertMoleWithHistory(mole: Mole, historyEntry: HistoryEntry) {
        upsertMole(mole)
        upsertHistoryEntry(historyEntry)
    }

    override suspend fun deleteMole(moleId: String) {
        moles.value = moles.value.filter { it.id != moleId }
        history.value = history.value.filter { it.moleId != moleId }
    }

    override suspend fun deleteMolesByProfile(profileName: String) {
        val idsToDelete = moles.value.filter { it.profileName == profileName }.map { it.id }
        moles.value = moles.value.filter { it.profileName != profileName }
        history.value = history.value.filter { it.moleId !in idsToDelete }
    }

    override suspend fun renameProfile(oldName: String, newName: String) {
        moles.value = moles.value.map { 
            if (it.profileName == oldName) it.copy(profileName = newName) else it 
        }
    }

    override suspend fun upsertHistoryEntry(entry: HistoryEntry) {
        val current = history.value.toMutableList()
        current.removeIf { it.id == entry.id }
        current.add(entry)
        history.value = current
    }

    override suspend fun deleteHistoryEntry(entryId: String) {
        history.value = history.value.filter { it.id != entryId }
    }
}
