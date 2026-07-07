package com.example.chronomapscanner.ui.viewmodels

import com.example.chronomapscanner.data.domain.HistoryEntry
import com.example.chronomapscanner.data.domain.Mole
import com.example.chronomapscanner.data.repository.MoleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeMoleRepository : MoleRepository {
    val moles = MutableStateFlow<List<Mole>>(emptyList())
    val history = MutableStateFlow<List<HistoryEntry>>(emptyList())

    override suspend fun getMolesWithHistory(profileName: String): List<Mole> {
        return moles.value.filter { it.profileName == profileName }.map { mole ->
            val moleHistory = history.value.filter { it.moleId == mole.id }
            mole.copy(history = moleHistory)
        }
    }

    override fun getAllProfileNames(): Flow<List<String>> {
        return moles.map { allMoles -> allMoles.map { it.profileName }.distinct() }
    }

    override fun getMolesCountForProfile(profileName: String): Flow<Int> {
        return moles.map { allMoles -> allMoles.count { it.profileName == profileName } }
    }

    override fun getMolesAtDate(profileName: String, targetDate: java.time.LocalDate): Flow<List<com.example.chronomapscanner.data.domain.MoleMapItem>> {
        return MutableStateFlow(emptyList())
    }

    override fun getAvailableDates(profileName: String): Flow<List<java.time.LocalDate>> {
        return MutableStateFlow(emptyList())
    }

    override fun getAvailableDatesForVariant(profileName: String, variantId: String): Flow<List<java.time.LocalDate>> {
        return MutableStateFlow(emptyList())
    }

    override fun getMoleByIdWithHistory(moleId: String): Flow<Mole?> {
        return MutableStateFlow(null)
    }

    override fun getTotalMolesCount(): Flow<Int> {
        return MutableStateFlow(0)
    }

    override suspend fun upsertMole(mole: Mole) {
        val current = moles.value.toMutableList()
        current.removeIf { it.id == mole.id }
        current.add(mole)
        moles.value = current
    }

    override suspend fun updateMoleColor(id: String, color: String) {
        moles.value = moles.value.map {
            if (it.id == id) it.copy(color = color) else it
        }
    }

    override suspend fun updateMolePosition(id: String, x: Float, y: Float, side: String) {
        moles.value = moles.value.map {
            if (it.id == id) it.copy(x = x, y = y, side = side) else it
        }
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

    override suspend fun deleteMolesByVariant(variantId: String) {}

    override suspend fun migrateMoles(oldVariantIds: List<String>, newVariantId: String) {}
}
