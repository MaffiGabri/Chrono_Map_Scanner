package com.example.chronomapscanner.data.local.room

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MoleDaoTest {

    private lateinit var database: AppDatabaseRoom
    private lateinit var moleDao: MoleDao

    @Before
    fun setup() {
        // Use an in-memory database so the data is not persistent.
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabaseRoom::class.java
        ).allowMainThreadQueries().build()
        
        moleDao = database.moleDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveMole() = runBlocking {
        // Arrange
        val moleId = UUID.randomUUID().toString()
        val moleEntity = MoleEntity(
            id = moleId,
            profileName = "TestProfile",
            x = 100f,
            y = 200f,
            side = "front",
            color = "color_safe",
            createdAt = "2026-06-16"
        )

        // Act
        moleDao.insertMole(moleEntity)
        val molesFromDb = moleDao.getMolesWithHistory("TestProfile")

        // Assert
        assertEquals(1, molesFromDb.size)
        assertEquals(moleId, molesFromDb[0].mole.id)
        assertEquals("color_safe", molesFromDb[0].mole.color)
    }

    @Test
    fun deleteMole_removesFromDatabase() = runBlocking {
        // Arrange
        val moleId = "mole-123"
        val moleEntity = MoleEntity(
            id = moleId,
            profileName = "TestProfile",
            x = 100f,
            y = 200f,
            side = "front",
            color = "color_alarm",
            createdAt = "2026-06-16"
        )
        moleDao.insertMole(moleEntity)

        // Act
        moleDao.deleteMole(moleId)
        val molesFromDb = moleDao.getMolesWithHistory("TestProfile")

        // Assert
        assertTrue(molesFromDb.isEmpty())
    }

    @Test
    fun insertMoleWithHistory_storesBothEntities() = runBlocking {
        // Arrange
        val moleId = "mole-history-test"
        val entryId = "entry-1"
        val moleEntity = MoleEntity(
            id = moleId,
            profileName = "HistoryProfile",
            x = 0f,
            y = 0f,
            side = "back",
            color = "color_monitor",
            createdAt = "2026-06-16"
        )
        val historyEntry = HistoryEntryEntity(
            id = entryId,
            moleId = moleId,
            date = "2026-06-16",
            imagePath = "/fake/path.jpg",
            notes = "Test notes"
        )

        // Act
        moleDao.insertMoleWithHistory(moleEntity, historyEntry)
        val retrievedMole = moleDao.getMoleByIdWithHistory(moleId).first()

        // Assert
        assertEquals(moleId, retrievedMole?.mole?.id)
        assertEquals(1, retrievedMole?.history?.size)
        assertEquals(entryId, retrievedMole?.history?.get(0)?.id)
        assertEquals("Test notes", retrievedMole?.history?.get(0)?.notes)
    }
}
