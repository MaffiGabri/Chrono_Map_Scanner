package com.example.skinhistoryscanner.ui.viewmodels

import android.content.Context
import android.net.Uri
import com.example.skinhistoryscanner.data.domain.BodyType
import com.example.skinhistoryscanner.data.domain.Gender
import com.example.skinhistoryscanner.data.domain.ReminderUnit
import com.example.skinhistoryscanner.data.local.datastore.SettingsRepository
import com.example.skinhistoryscanner.data.repository.BackgroundRepository
import com.example.skinhistoryscanner.data.repository.BackupRepository
import com.example.skinhistoryscanner.data.repository.FileRepository
import com.example.skinhistoryscanner.data.local.room.AppDatabaseRoom
import com.example.skinhistoryscanner.fakes.FakeMoleRepository
import com.example.skinhistoryscanner.utils.MainDispatcherRule
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import com.example.skinhistoryscanner.data.domain.ColorSetting
import io.mockk.mockkObject
import io.mockk.unmockkAll

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testScope = TestScope(mainDispatcherRule.testDispatcher)

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var moleRepository: FakeMoleRepository
    private lateinit var backupRepository: BackupRepository
    private lateinit var fileRepository: FileRepository
    private lateinit var backgroundRepository: BackgroundRepository
    private lateinit var database: AppDatabaseRoom
    private lateinit var context: Context

    private lateinit var viewModel: SettingsViewModel

    // State flows to mock in SettingsRepository
    private val currentProfileFlow = MutableStateFlow("Default")
    private val profileImageFlow = MutableStateFlow<String?>(null)
    private val genderFlow = MutableStateFlow(Gender.MALE)
    private val bodyTypeFlow = MutableStateFlow(BodyType.SLIM)
    private val remindersEnabledFlow = MutableStateFlow(false)
    private val remindersValueFlow = MutableStateFlow(1)
    private val remindersUnitFlow = MutableStateFlow(ReminderUnit.MONTHS)
    private val lastReminderDateFlow = MutableStateFlow<String?>(null)
    private val isImportingFlow = MutableStateFlow(false)
    private val colorSettingsFlow = MutableStateFlow<List<ColorSetting>>(emptyList())
    private val keepLegendVisibleFlow = MutableStateFlow(true)
    private val rapidInsertionModeFlow = MutableStateFlow(false)
    private val rapidUpdateModeFlow = MutableStateFlow(false)
    private val showZoomButtonFlow = MutableStateFlow(true)
    private val snapToRecentOnAddMoleFlow = MutableStateFlow(true)
    private val scannerDelayMsFlow = MutableStateFlow(500L)
    private val scannerIntervalMinFlow = MutableStateFlow(5L)
    private val warnOnEmptyMoleDeletionFlow = MutableStateFlow(true)

    @Before
    fun setup() {
        settingsRepository = mockk(relaxed = true) {
            every { currentProfile } returns currentProfileFlow
            every { profileImage } returns profileImageFlow
            every { gender } returns genderFlow
            every { bodyType } returns bodyTypeFlow
            every { remindersEnabled } returns remindersEnabledFlow
            every { remindersValue } returns remindersValueFlow
            every { remindersUnit } returns remindersUnitFlow
            every { lastReminderDate } returns lastReminderDateFlow
            every { isImporting } returns isImportingFlow
            every { colorSettings } returns colorSettingsFlow
            every { keepLegendVisible } returns keepLegendVisibleFlow
            every { rapidInsertionMode } returns rapidInsertionModeFlow
            every { rapidUpdateMode } returns rapidUpdateModeFlow
            every { showZoomButton } returns showZoomButtonFlow
            every { snapToRecentOnAddMole } returns snapToRecentOnAddMoleFlow
            every { scannerDelayMs } returns scannerDelayMsFlow
            every { scannerIntervalMin } returns scannerIntervalMinFlow
            every { warnOnEmptyMoleDeletion } returns warnOnEmptyMoleDeletionFlow

            // Mock setCurrentProfile
            coEvery { setCurrentProfile(any()) } answers {
                currentProfileFlow.value = firstArg()
            }
        }

        moleRepository = FakeMoleRepository()
        backupRepository = mockk(relaxed = true)
        fileRepository = mockk(relaxed = true)
        backgroundRepository = mockk(relaxed = true)
        database = mockk(relaxed = true)
        context = mockk(relaxed = true)

        viewModel = SettingsViewModel(
            settingsRepository = settingsRepository,
            moleRepository = moleRepository,
            backupRepository = backupRepository,
            fileRepository = fileRepository,
            backgroundRepository = backgroundRepository,
            database = database,
            applicationScope = testScope,
            context = context
        )
    }

    @Test
    fun `settingsUiState emits correctly based on combine flows`() = testScope.runTest {
        // Arrange
        moleRepository.emitMoles(emptyList()) // No moles initially

        viewModel.settingsUiState.test {
            var initialState = awaitItem()
            // initial empty state is emitted before combine finishes
            if (initialState.profileName == "") {
                initialState = awaitItem()
            }

            // Assert Initial state defaults
            assertEquals("Default", initialState.profileName)
            assertEquals(Gender.MALE, initialState.userSettings.gender)
            assertEquals(BodyType.SLIM, initialState.userSettings.bodyType)
            assertEquals(false, initialState.reminderSettings.enabled)
            assertEquals(0, initialState.moleCount)
            assertEquals(true, initialState.keepLegendVisible)
            assertEquals(false, initialState.rapidInsertionMode)

            // Act: Update flows
            currentProfileFlow.value = "John"
            genderFlow.value = Gender.FEMALE
            keepLegendVisibleFlow.value = false

            // Add a mole to trigger profile mole count update in repository
            moleRepository.emitMoles(listOf(
                com.example.skinhistoryscanner.data.domain.Mole("1", "John", 0f, 0f, "front", "#000")
            ))

            // Skip intermediate state emissions
            var updatedState = awaitItem()
            while (updatedState.profileName != "John" || updatedState.userSettings.gender != Gender.FEMALE || updatedState.moleCount != 1) {
                updatedState = awaitItem()
            }

            // Assert Updated state
            assertEquals("John", updatedState.profileName)
            assertEquals(Gender.FEMALE, updatedState.userSettings.gender)
            assertEquals(false, updatedState.keepLegendVisible)
            assertEquals(1, updatedState.moleCount)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switchProfile calls repository setCurrentProfile`() = testScope.runTest {
        viewModel.switchProfile("Alice")
        assertEquals("Alice", currentProfileFlow.value)
    }

    @Test
    fun `generateGlobalReport sets isGeneratingGlobalReport state correctly`() = testScope.runTest {
        // Arrange
        mockkObject(com.example.skinhistoryscanner.utils.GlobalReportGenerator)
        val mockedFile = File("mocked.pdf")
        coEvery {
            com.example.skinhistoryscanner.utils.GlobalReportGenerator.generateGlobalPdf(any(), any(), any(), any(), any())
        } returns mockedFile

        var callbackFile: File? = null

        viewModel.settingsUiState.test {
            var state = awaitItem()
            if (state.profileName == "") {
                state = awaitItem()
            }

            assertFalse(state.isGeneratingGlobalReport)

            // Act
            viewModel.generateGlobalReport(getColorLabel = { "Label" }) { file ->
                callbackFile = file
            }

            // Assert
            state = awaitItem()
            assertTrue(state.isGeneratingGlobalReport)

            state = awaitItem()
            assertFalse(state.isGeneratingGlobalReport)

            assertEquals(mockedFile, callbackFile)

            cancelAndIgnoreRemainingEvents()
        }

        unmockkAll()
    }

    @Test
    fun `deleteProfile active profile switches to another available profile`() = testScope.runTest {
        // Arrange: Make sure there's another profile by adding moles for another profile
        moleRepository.emitMoles(listOf(
            com.example.skinhistoryscanner.data.domain.Mole("1", "Bob", 0f, 0f, "front", "#000"),
            com.example.skinhistoryscanner.data.domain.Mole("2", "Default", 0f, 0f, "front", "#000")
        ))

        currentProfileFlow.value = "Default"

        viewModel.allProfiles.test {
            // Wait for allProfiles to populate properly
            val profiles = awaitItem()
            if(!profiles.contains("Bob")) awaitItem()

            // Act
            viewModel.deleteProfile("Default")

            // Should switch to Bob
            assertEquals("Bob", currentProfileFlow.value)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
