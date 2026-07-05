package com.example.skinhistoryscanner.ui.viewmodels

import com.example.skinhistoryscanner.data.domain.BodyType
import com.example.skinhistoryscanner.data.domain.ColorSetting
import com.example.skinhistoryscanner.data.domain.Gender
import com.example.skinhistoryscanner.data.domain.HistoryEntry
import com.example.skinhistoryscanner.data.domain.Mole
import com.example.skinhistoryscanner.data.local.datastore.SettingsRepository
import com.example.skinhistoryscanner.data.repository.BackgroundRepository
import com.example.skinhistoryscanner.fakes.FakeMoleRepository
import com.example.skinhistoryscanner.ui.BackgroundVariantUiModel
import com.example.skinhistoryscanner.utils.MainDispatcherRule
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class BodyMapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testScope = TestScope(mainDispatcherRule.testDispatcher)

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var moleRepository: FakeMoleRepository
    private lateinit var backgroundRepository: BackgroundRepository
    private lateinit var viewModel: BodyMapViewModel

    // State flows to mock in SettingsRepository
    private val currentProfileFlow = MutableStateFlow("Default")
    private val profileImageFlow = MutableStateFlow<String?>(null)
    private val activeCategoryIdFlow = MutableStateFlow<String?>(null)
    private val genderFlow = MutableStateFlow(Gender.MALE)
    private val bodyTypeFlow = MutableStateFlow(BodyType.SLIM)
    private val colorSettingsFlow = MutableStateFlow(
        listOf(
            ColorSetting("#ef4444", "color_alarm", visible = true),
            ColorSetting("#f97316", "color_suspicious", visible = true)
        )
    )
    private val keepLegendVisibleFlow = MutableStateFlow(true)
    private val rapidInsertionModeFlow = MutableStateFlow(false)
    private val rapidUpdateModeFlow = MutableStateFlow(false)
    private val showZoomButtonFlow = MutableStateFlow(true)
    private val isImportingFlow = MutableStateFlow(false)

    @Before
    fun setup() {
        settingsRepository = mockk(relaxed = true) {
            every { currentProfile } returns currentProfileFlow
            every { profileImage } returns profileImageFlow
            every { activeCategoryId } returns activeCategoryIdFlow
            every { gender } returns genderFlow
            every { bodyType } returns bodyTypeFlow
            every { colorSettings } returns colorSettingsFlow
            every { keepLegendVisible } returns keepLegendVisibleFlow
            every { rapidInsertionMode } returns rapidInsertionModeFlow
            every { rapidUpdateMode } returns rapidUpdateModeFlow
            every { showZoomButton } returns showZoomButtonFlow
            every { isImporting } returns isImportingFlow
        }

        moleRepository = FakeMoleRepository()
        backgroundRepository = mockk(relaxed = true)

        // Mock background variants
        val defaultVariants = listOf(
            com.example.skinhistoryscanner.data.local.room.BackgroundVariantEntity("front", "cat1", "Front", null, 0, LocalDate.now(), null),
            com.example.skinhistoryscanner.data.local.room.BackgroundVariantEntity("back", "cat1", "Back", null, 1, LocalDate.now(), null)
        )
        every { backgroundRepository.getVariantsForCategory(any()) } returns flowOf(defaultVariants)
        every { backgroundRepository.getCategoriesForProfile(any()) } returns flowOf(
            listOf(com.example.skinhistoryscanner.data.local.room.BackgroundCategoryEntity("cat1", "Default", "Profile", true))
        )
        coEvery { backgroundRepository.ensureBuiltInCategoriesExist(any()) } returns Unit

        viewModel = BodyMapViewModel(
            moleRepository = moleRepository,
            settingsRepository = settingsRepository,
            backgroundRepository = backgroundRepository
        )
    }

    @Test
    fun `bodyMapUiState responds correctly to date changes`() = testScope.runTest {
        // Arrange
        val mole1Id = "mole1"
        val date1 = LocalDate.of(2023, 1, 1)
        val date2 = LocalDate.of(2023, 1, 15)

        moleRepository.emitMoles(listOf(
            Mole(mole1Id, "Default", 50f, 50f, "front", "#ef4444")
        ))

        // Mole has history on both dates
        moleRepository.emitHistory(listOf(
            HistoryEntry("h1", mole1Id, date1, null, "Note 1"),
            HistoryEntry("h2", mole1Id, date2, null, "Note 2")
        ))

        viewModel.bodyMapUiState.test {
            // Read initial states
            var state = awaitItem()

            // Advance date to date1
            viewModel.setSelectedDate(date1)

            // Wait until the date propagates
            while (state.selectedDate != date1 || state.moles.isEmpty()) {
                state = awaitItem()
            }

            // Assert state on date1
            assertEquals(date1, state.selectedDate)
            assertEquals(1, state.moles.size)
            assertEquals(50f, state.moles[0].x)

            // Change color and position of mole for a future date update
            moleRepository.updateMolePosition(mole1Id, 60f, 60f, "front")
            moleRepository.updateMoleColor(mole1Id, "#f97316")

            // Advance date to date2
            viewModel.setSelectedDate(date2)

            // Wait for update
            while (state.selectedDate != date2 || state.moles.isEmpty() || state.moles[0].x != 60f) {
                state = awaitItem()
            }

            // Assert state on date2
            assertEquals(date2, state.selectedDate)
            assertEquals(1, state.moles.size)
            assertEquals(60f, state.moles[0].x)
            assertEquals(60f, state.moles[0].y)
            assertEquals("#f97316", state.moles[0].colorHex)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bodyMapUiState changes correctly when variant is cycled`() = testScope.runTest {
        // Arrange
        val date = LocalDate.now()

        moleRepository.emitMoles(listOf(
            Mole("mole1", "Default", 50f, 50f, "front", "#ef4444"),
            Mole("mole2", "Default", 20f, 20f, "back", "#ef4444")
        ))

        moleRepository.emitHistory(listOf(
            HistoryEntry("h1", "mole1", date, null, null),
            HistoryEntry("h2", "mole2", date, null, null)
        ))

        viewModel.bodyMapUiState.test {
            var state = awaitItem()

            // Wait for initial population
            while (state.moles.isEmpty() || state.variants.isEmpty()) {
                state = awaitItem()
            }

            // At start, variant should be index 0 ("front") and mole1 should be visible
            assertEquals("front", state.currentVariant?.id)
            assertEquals(1, state.moles.size)
            assertEquals("mole1", state.moles[0].id)

            // Act
            viewModel.cycleVariant()

            // Wait for variant update
            while (state.currentVariant?.id != "back") {
                state = awaitItem()
            }

            // Now mole2 should be visible
            assertEquals("back", state.currentVariant?.id)
            assertEquals(1, state.moles.size)
            assertEquals("mole2", state.moles[0].id)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
