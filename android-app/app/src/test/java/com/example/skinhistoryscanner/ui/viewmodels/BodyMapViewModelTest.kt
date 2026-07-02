package com.example.skinhistoryscanner.ui.viewmodels

import app.cash.turbine.test
import com.example.skinhistoryscanner.data.domain.BodySide
import com.example.skinhistoryscanner.data.domain.BodyType
import com.example.skinhistoryscanner.data.domain.ColorSetting
import com.example.skinhistoryscanner.data.domain.Gender
import com.example.skinhistoryscanner.data.domain.MoleMapSummary
import com.example.skinhistoryscanner.data.local.datastore.SettingsRepository
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
class BodyMapViewModelTest {

    private lateinit var viewModel: BodyMapViewModel
    private val moleRepository: MoleRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mock SettingsRepository
        every { settingsRepository.currentProfile } returns flowOf("Mamma")
        every { settingsRepository.profileImage } returns flowOf(null)
        every { settingsRepository.gender } returns flowOf(Gender.FEMALE)
        every { settingsRepository.bodyType } returns flowOf(BodyType.SLIM)
        every { settingsRepository.keepLegendVisible } returns flowOf(false)
        every { settingsRepository.rapidInsertionMode } returns flowOf(false)
        every { settingsRepository.rapidUpdateMode } returns flowOf(false)

        // Mock two colors: Red is visible, Black is hidden
        val colorSettings = listOf(
            ColorSetting("#FF0000", "Red", true),
            ColorSetting("#000000", "Black", false)
        )
        every { settingsRepository.colorSettings } returns flowOf(colorSettings)

        // Mock MoleRepository response
        val moles = listOf(
            MoleMapSummary("1", "Mamma", 10f, 20f, BodySide.FRONT, "#FF0000", null)
        )
        every { moleRepository.getMolesForMap(any(), any(), any(), any()) } returns flowOf(moles)

        viewModel = BodyMapViewModel(moleRepository, settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `bodyMapUiState computes correct visible moles and filters out hidden colors`() = runTest {
        // We expect the ViewModel to call getMolesForMap with ONLY the visible colors ("#FF0000")
        
        viewModel.bodyMapUiState.test {
            // Wait for initial value (isLoading = true)
            val initial = awaitItem()
            if (initial.isLoading) {
                // Wait for the combined flow to emit the actual data
                val dataState = awaitItem()
                assertEquals("Mamma", dataState.profileName)
                assertEquals(1, dataState.moles.size)
                assertEquals("#FF0000", dataState.moles[0].color)
            } else {
                assertEquals("Mamma", initial.profileName)
                assertEquals(1, initial.moles.size)
            }
            
            // Verify moleRepository was called correctly with ONLY visible colors
            coVerify { 
                moleRepository.getMolesForMap(
                    profileName = "Mamma",
                    side = BodySide.FRONT,
                    maxDate = any(),
                    colors = listOf("#FF0000")
                ) 
            }
            cancelAndIgnoreRemainingEvents()
        }
        advanceTimeBy(5001)
    }

    @Test
    fun `setBodySide updates state to BACK`() = runTest {
        viewModel.setBodySide(false)
        advanceUntilIdle()

        viewModel.bodyMapUiState.test {
            val state = awaitItem()
            if (!state.isLoading) {
                assertEquals(false, state.isFront)
            } else {
                val nextState = awaitItem()
                assertEquals(false, nextState.isFront)
            }
            cancelAndIgnoreRemainingEvents()
        }
        advanceTimeBy(5001)
    }
}
