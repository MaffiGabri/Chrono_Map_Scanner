package com.example.skinhistoryscanner.fakes

import com.example.skinhistoryscanner.data.domain.HistoryEntry
import com.example.skinhistoryscanner.data.domain.Mole
import com.example.skinhistoryscanner.data.domain.MoleMapItem
import com.example.skinhistoryscanner.data.repository.MoleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class FakeMoleRepository : MoleRepository {
    private val molesFlow = MutableStateFlow<List<Mole>>(emptyList())
    private val historyFlow = MutableStateFlow<List<HistoryEntry>>(emptyList())

    // Test utilities
    fun emitMoles(moles: List<Mole>) {
        molesFlow.value = moles
    }

    fun emitHistory(history: List<HistoryEntry>) {
        historyFlow.value = history
    }

    override suspend fun getMolesWithHistory(profileName: String): List<Mole> {
        val currentMoles = molesFlow.value.filter { it.profileName == profileName }
        val currentHistory = historyFlow.value

        return currentMoles.map { mole ->
            mole.copy(history = currentHistory.filter { it.moleId == mole.id })
        }
    }

    override fun getMolesAtDate(
        profileName: String,
        targetDate: LocalDate
    ): Flow<List<MoleMapItem>> {
        return molesFlow.map { moles ->
            moles.filter { it.profileName == profileName }.mapNotNull { mole ->
                val moleHistory = historyFlow.value.filter { it.moleId == mole.id }
                // Find latest history entry before or on targetDate
                val entry = moleHistory
                    .filter { !it.date.isAfter(targetDate) }
                    .maxByOrNull { it.date }

                if (entry != null) {
                    MoleMapItem(
                        id = mole.id,

                        x = mole.x,
                        y = mole.y,
                        side = mole.side,
                        color = mole.color,
                        historyDate = entry.date,
                        imagePath = entry.imagePath
                    )
                } else {
                    null // Mole didn't exist at this date
                }
            }
        }
    }

    override fun getAvailableDates(profileName: String): Flow<List<LocalDate>> {
        return historyFlow.map { history ->
            val moleIdsForProfile = molesFlow.value.filter { it.profileName == profileName }.map { it.id }.toSet()
            history.filter { it.moleId in moleIdsForProfile }.map { it.date }.distinct().sorted()
        }
    }

    override fun getAvailableDatesForVariant(
        profileName: String,
        variantId: String
    ): Flow<List<LocalDate>> {
        return historyFlow.map { history ->
            val moleIdsForProfileAndVariant = molesFlow.value
                .filter { it.profileName == profileName && it.side == variantId }
                .map { it.id }
                .toSet()
            history.filter { it.moleId in moleIdsForProfileAndVariant }.map { it.date }.distinct().sorted()
        }
    }

    override fun getMoleByIdWithHistory(moleId: String): Flow<Mole?> {
        return molesFlow.map { moles ->
            val mole = moles.find { it.id == moleId }
            if (mole != null) {
                mole.copy(history = historyFlow.value.filter { it.moleId == mole.id })
            } else {
                null
            }
        }
    }

    override fun getAllProfileNames(): Flow<List<String>> {
        return molesFlow.map { moles ->
            moles.map { it.profileName }.distinct()
        }
    }

    override fun getTotalMolesCount(): Flow<Int> {
        return molesFlow.map { it.size }
    }

    override fun getMolesCountForProfile(profileName: String): Flow<Int> {
        return molesFlow.map { moles ->
            moles.count { it.profileName == profileName }
        }
    }

    override suspend fun upsertMole(mole: Mole) {
        val current = molesFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == mole.id }
        if (index >= 0) {
            current[index] = mole
        } else {
            current.add(mole)
        }
        molesFlow.value = current
    }

    override suspend fun updateMolePosition(id: String, x: Float, y: Float, side: String) {
        val current = molesFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            current[index] = current[index].copy(x = x, y = y, side = side)
            molesFlow.value = current
        }
    }

    override suspend fun updateMoleColor(id: String, color: String) {
        val current = molesFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            current[index] = current[index].copy(color = color)
            molesFlow.value = current
        }
    }

    override suspend fun insertMoleWithHistory(mole: Mole, historyEntry: HistoryEntry) {
        upsertMole(mole)
        upsertHistoryEntry(historyEntry)
    }

    override suspend fun deleteMole(moleId: String) {
        molesFlow.value = molesFlow.value.filter { it.id != moleId }
        historyFlow.value = historyFlow.value.filter { it.moleId != moleId }
    }

    override suspend fun deleteMolesByProfile(profileName: String) {
        val molesToDelete = molesFlow.value.filter { it.profileName == profileName }.map { it.id }.toSet()
        molesFlow.value = molesFlow.value.filter { it.profileName != profileName }
        historyFlow.value = historyFlow.value.filter { it.moleId !in molesToDelete }
    }

    override suspend fun renameProfile(oldName: String, newName: String) {
        val current = molesFlow.value.toMutableList()
        for (i in current.indices) {
            if (current[i].profileName == oldName) {
                current[i] = current[i].copy(profileName = newName)
            }
        }
        molesFlow.value = current
    }

    override suspend fun upsertHistoryEntry(entry: HistoryEntry) {
        val current = historyFlow.value.toMutableList()
        val index = current.indexOfFirst { it.id == entry.id }
        if (index >= 0) {
            current[index] = entry
        } else {
            current.add(entry)
        }
        historyFlow.value = current
    }

    override suspend fun deleteHistoryEntry(entryId: String) {
        historyFlow.value = historyFlow.value.filter { it.id != entryId }
    }

    override suspend fun deleteMolesByVariant(variantId: String) {
        val molesToDelete = molesFlow.value.filter { it.side == variantId }.map { it.id }.toSet()
        molesFlow.value = molesFlow.value.filter { it.side != variantId }
        historyFlow.value = historyFlow.value.filter { it.moleId !in molesToDelete }
    }

    override suspend fun migrateMoles(oldVariantIds: List<String>, newVariantId: String) {
        val current = molesFlow.value.toMutableList()
        for (i in current.indices) {
            if (current[i].side in oldVariantIds) {
                current[i] = current[i].copy(side = newVariantId)
            }
        }
        molesFlow.value = current
    }
}
