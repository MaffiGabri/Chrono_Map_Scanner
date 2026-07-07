package com.example.chronomapscanner.data.repository

import com.example.chronomapscanner.data.domain.HistoryEntry
import com.example.chronomapscanner.data.domain.Mole
import kotlinx.coroutines.flow.Flow

/**
 * Interface defining the operations for managing Moles and their history.
 */
interface MoleRepository {
    /**
     * Returns a list of moles with their full history for a given profile.
     * Used for one-off operations like backups.
     */
    suspend fun getMolesWithHistory(profileName: String): List<Mole>

    /**
     * Returns a flat stream of moles and their state at a specific target date.
     */
    fun getMolesAtDate(profileName: String, targetDate: java.time.LocalDate): Flow<List<com.example.chronomapscanner.data.domain.MoleMapItem>>

    /**
     * Returns all unique history dates available for the given profile.
     */
    fun getAvailableDates(profileName: String): Flow<List<java.time.LocalDate>>

    /**
     * Returns all unique history dates available for the given profile and variant.
     */
    fun getAvailableDatesForVariant(profileName: String, variantId: String): Flow<List<java.time.LocalDate>>

    /**
     * Returns a stream of a single mole with its history.
     */
    fun getMoleByIdWithHistory(moleId: String): Flow<Mole?>

    /**
     * Returns a distinct list of all profile names in the database.
     */
    fun getAllProfileNames(): Flow<List<String>>

    /**
     * Returns the total count of moles in the database efficiently.
     */
    fun getTotalMolesCount(): Flow<Int>

    /**
     * Returns the count of moles for a specific profile.
     */
    fun getMolesCountForProfile(profileName: String): Flow<Int>



    /**
     * Inserts or updates a mole.
     */
    suspend fun upsertMole(mole: Mole)

    /**
     * Updates only the position of a mole.
     */
    suspend fun updateMolePosition(id: String, x: Float, y: Float, side: String)

    /**
     * Updates only the color of a mole.
     */
    suspend fun updateMoleColor(id: String, color: String)

    /**
     * Inserts a new mole along with its first history entry atomically.
     */
    suspend fun insertMoleWithHistory(mole: Mole, historyEntry: HistoryEntry)

    /**
     * Deletes a mole and its associated history.
     */
    suspend fun deleteMole(moleId: String)

    /**
     * Deletes all moles associated with a specific profile.
     */
    suspend fun deleteMolesByProfile(profileName: String)

    /**
     * Renames a profile across all associated moles.
     */
    suspend fun renameProfile(oldName: String, newName: String)

    /**
     * Inserts or updates a history entry for a mole.
     */
    suspend fun upsertHistoryEntry(entry: HistoryEntry)

    /**
     * Deletes a specific history entry.
     */
    suspend fun deleteHistoryEntry(entryId: String)

    /**
     * Deletes all moles associated with a variant.
     */
    suspend fun deleteMolesByVariant(variantId: String)

    /**
     * Migrates moles from a list of old variants to a new variant.
     */
    suspend fun migrateMoles(oldVariantIds: List<String>, newVariantId: String)
}
