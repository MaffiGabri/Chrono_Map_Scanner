package com.example.skinhistoryscanner.ui.viewmodels

import com.example.skinhistoryscanner.data.domain.HistoryEntry
import com.example.skinhistoryscanner.data.domain.Mole
import com.example.skinhistoryscanner.data.domain.MoleMapItem
import com.example.skinhistoryscanner.data.repository.MoleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class FakeMoleRepository : MoleRepository {
    private val moles = MutableStateFlow<List<Mole>>(emptyList())
    private val history = MutableStateFlow<List<HistoryEntry>>(emptyList())

    override suspend fun getMolesWithHistory(profileName: String): List<Mole> {
        return moles.value.filter { it.profileName == profileName }.map { mole ->
            val moleHistory = history.value.filter { it.moleId == mole.id }
            mole.copy(history = moleHistory)
        }
    }

    override fun getMolesAtDate(profileName: String, targetDate: LocalDate): Flow<List<MoleMapItem>> {
        return moles.map { allMoles ->
            allMoles.filter { it.profileName == profileName }.map { mole ->
                val lastEntry = history.value.filter { it.moleId == mole.id && it.date <= targetDate }.maxByOrNull { it.date }
                MoleMapItem(
                    id = mole.id,
                    x = mole.x,
                    y = mole.y,
                    color = mole.color,
                    side = mole.side,
                    historyDate = lastEntry?.date,
                    imagePath = lastEntry?.imagePath
                )
            }
        }
    }

    override fun getAvailableDates(profileName: String): Flow<List<LocalDate>> {
        return history.map { allHistory ->
            val profileMoleIds = moles.value.filter { it.profileName == profileName }.map { it.id }.toSet()
            allHistory.filter { it.moleId in profileMoleIds }.map { it.date }.distinct().sortedDescending()
        }
    }

    override fun getAvailableDatesForVariant(profileName: String, variantId: String): Flow<List<LocalDate>> {
        return history.map { allHistory ->
            val profileMoleIds = moles.value.filter { it.profileName == profileName && it.side == variantId }.map { it.id }.toSet()
            allHistory.filter { it.moleId in profileMoleIds }.map { it.date }.distinct().sortedDescending()
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

    override fun getTotalMolesCount(): Flow<Int> {
        return moles.map { it.size }
    }

    override fun getMolesCountForProfile(profileName: String): Flow<Int> {
        return moles.map { allMoles -> allMoles.count { it.profileName == profileName } }
    }

    override suspend fun upsertMole(mole: Mole) {
        val current = moles.value.toMutableList()
        current.removeIf { it.id == mole.id }
        current.add(mole)
        moles.value = current
    }

    override suspend fun updateMolePosition(id: String, x: Float, y: Float, side: String) {
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

    override suspend fun deleteMolesByVariant(variantId: String) {
        val idsToDelete = moles.value.filter { it.side == variantId }.map { it.id }
        moles.value = moles.value.filter { it.side != variantId }
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

    override suspend fun migrateMoles(oldVariantIds: List<String>, newVariantId: String) {
        moles.value = moles.value.map {
            if (it.side in oldVariantIds) it.copy(side = newVariantId) else it
        }
    }
}
