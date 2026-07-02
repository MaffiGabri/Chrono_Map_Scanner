package com.example.skinhistoryscanner.ui.viewmodels

import android.content.Context
import app.cash.turbine.test
import com.example.skinhistoryscanner.data.domain.BodyType
import com.example.skinhistoryscanner.data.domain.Gender
import com.example.skinhistoryscanner.data.local.datastore.SettingsRepository
import com.example.skinhistoryscanner.data.repository.BackupRepository
import com.example.skinhistoryscanner.data.repository.FileRepository
import com.example.skinhistoryscanner.data.repository.MoleRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var viewModel: SettingsViewModel
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val moleRepository: MoleRepository = mockk(relaxed = true)
    private val backupRepository: BackupRepository = mockk(relaxed = true)
    private val fileRepository: FileRepository = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mock StateFlows for SettingsRepository
        val currentProfileFlow = MutableStateFlow("Default")
        every { settingsRepository.currentProfile } returns currentProfileFlow
        every { settingsRepository.profileImage } returns flowOf(null)
        every { settingsRepository.gender } returns flowOf(Gender.MALE)
        every { settingsRepository.bodyType } returns flowOf(BodyType.SLIM)
        every { settingsRepository.remindersEnabled } returns flowOf(false)
        every { settingsRepository.remindersValue } returns flowOf(1)
        every { settingsRepository.remindersUnit } returns flowOf(com.example.skinhistoryscanner.data.domain.ReminderUnit.MONTHS)
        every { settingsRepository.lastReminderDate } returns flowOf<String?>(null)
        every { settingsRepository.colorSettings } returns flowOf(emptyList())
        every { settingsRepository.keepLegendVisible } returns flowOf(false)
        every { settingsRepository.rapidInsertionMode } returns flowOf(false)
        every { settingsRepository.rapidUpdateMode } returns flowOf(false)

        // Mock MoleRepository
        every { moleRepository.getAllProfileNames() } returns flowOf(listOf("Default", "Lavoro"))
        every { moleRepository.getMolesCountForProfile(any()) } returns flowOf(5)

        viewModel = SettingsViewModel(
            settingsRepository,
            moleRepository,
            backupRepository,
            fileRepository,
            testScope,
            context
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `allProfiles combines distinct sorted profiles from repository and datastore`() = runTest {
        // We expect the ViewModel to combine ["Default", "Lavoro"] (from moleRepository)
        // with "Default" (from settingsRepository currentProfile)
        // Output should be sorted: ["Default", "Lavoro"]
        viewModel.allProfiles.test {
            val initial = awaitItem()
            // initial value before combine executes
            assertEquals(listOf("Default"), initial)
            
            val combined = awaitItem()
            assertEquals(listOf("Default", "Lavoro"), combined)
            
            cancelAndIgnoreRemainingEvents()
        }
        advanceTimeBy(5001)
    }

    @Test
    fun `switchProfile calls repository setCurrentProfile`() = runTest {
        viewModel.switchProfile("Mamma")
        advanceUntilIdle() // Wait for coroutines to complete
        coVerify { settingsRepository.setCurrentProfile("Mamma") }
        advanceTimeBy(5001)
    }
}
