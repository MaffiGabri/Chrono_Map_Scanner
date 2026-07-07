package com.example.chronomapscanner.utils

import android.content.Context
import com.example.chronomapscanner.data.local.room.AppDatabaseRoom
import com.example.chronomapscanner.data.local.room.MoleEntity
import com.example.chronomapscanner.data.local.room.HistoryEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

object Seeder {
    suspend fun seedFakeData(context: Context, database: AppDatabaseRoom, profileName: String, forceReset: Boolean = false) = withContext(Dispatchers.IO) {
        val dao = database.moleDao()
        val bgDao = database.backgroundDao()
        
        if (forceReset) {
            dao.deleteMolesByProfile(profileName)
        } else {
            val count = dao.getMolesCountForProfile(profileName).firstOrNull() ?: 0
            if (count > 0) return@withContext // Già popolato
        }

        val variants = bgDao.getVariantsForProfileSync(profileName)
        if (variants.isEmpty()) {
            return@withContext // Cannot seed if there are no variants to place moles on
        }
        val variantIds = variants.map { it.id }

        val baseImages = (1..10).map { i ->
            val file = File(context.filesDir, "mole_base_$i.png")
            if (!file.exists()) {
                try {
                    context.assets.open("mole_$i.png").use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    // Fallback se per qualche motivo l'asset manca
                }
            }
            file
        }.filter { it.exists() }

        val colors = listOf("#ef4444", "#f97316", "#eab308", "#22c55e", "#3b82f6", "#a855f7")
        val notesList = listOf("Prurito", "Irregolare", "Normale", "Scuro", "Piccolo", "Nuovo", "Prurito lieve", "Da controllare", "Macchia", "Piatto")

        val generatedMoles = mutableMapOf<String, MutableList<Pair<Float, Float>>>()

        for (i in 1..500) {
            val moleId = UUID.randomUUID().toString()
            val variant = variantIds.random()
            var x = 0f
            var y = 0f
            var attempts = 0
            val existing = generatedMoles.getOrPut(variant) { mutableListOf() }
            
            do {
                x = (5..95).random().toFloat()
                y = (5..95).random().toFloat()
                attempts++
                var tooClose = false
                for (p in existing) {
                    val dx = p.first - x
                    val dy = p.second - y
                    if (dx*dx + dy*dy < 12f) { // Circa 3.4% di distanza minima
                        tooClose = true
                        break
                    }
                }
            } while (tooClose && attempts < 50)
            existing.add(Pair(x, y))

            val mole = MoleEntity(
                id = moleId,
                profileName = profileName,
                x = x,
                y = y,
                variantId = variant,
                color = colors.random()
            )
            dao.insertMole(mole)

            if (i <= 200) {
                // 200 nei: Solo testo, senza immagine (così non risultano vuoti)
                val entryId = UUID.randomUUID().toString()
                val date = LocalDate.now().minusDays((0..300).random().toLong())
                
                val historyEntry = HistoryEntryEntity(
                    id = entryId,
                    moleId = moleId,
                    date = date,
                    imagePath = null,
                    notes = notesList.random()
                )
                dao.insertHistoryEntry(historyEntry)
            } else {
                // 300 nei: Con immagini e testo
                val hasMultiple = i % 10 == 0
                val entriesCount = if (hasMultiple) 3 else 1
                
                for (j in 0 until entriesCount) {
                    val entryId = UUID.randomUUID().toString()
                    val date = LocalDate.now().minusDays((0..300).random().toLong())
                    var imagePath: String? = null
                    
                    if (baseImages.isNotEmpty()) {
                        val fileName = "img_$entryId.png"
                        val destFile = File(context.filesDir, fileName)
                        val randomBaseImage = baseImages.random()
                        randomBaseImage.copyTo(destFile, overwrite = true)
                        
                        val thumbFile = File(context.filesDir, "thumb_$fileName")
                        randomBaseImage.copyTo(thumbFile, overwrite = true) // Thumb fittizia
                        
                        imagePath = destFile.absolutePath
                    }
                    
                    val historyEntry = HistoryEntryEntity(
                        id = entryId,
                        moleId = moleId,
                        date = date,
                        imagePath = imagePath,
                        notes = notesList.random()
                    )
                    dao.insertHistoryEntry(historyEntry)
                }
            }
        }
    }
}
