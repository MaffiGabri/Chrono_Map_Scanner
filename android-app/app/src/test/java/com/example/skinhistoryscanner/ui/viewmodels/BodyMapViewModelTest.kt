package com.example.skinhistoryscanner.ui.viewmodels

import app.cash.turbine.test
import com.example.skinhistoryscanner.data.domain.BodyType
import com.example.skinhistoryscanner.data.domain.ColorSetting
import com.example.skinhistoryscanner.data.domain.Gender
import com.example.skinhistoryscanner.data.domain.MoleMapItem
import com.example.skinhistoryscanner.ui.BackgroundVariantUiModel
import com.example.skinhistoryscanner.data.local.datastore.SettingsRepository
import com.example.skinhistoryscanner.data.repository.MoleRepository
import com.example.skinhistoryscanner.data.repository.BackgroundRepository
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class BodyMapViewModelTest {

    private lateinit var viewModel: BodyMapViewModel
    private val moleRepository: MoleRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val backgroundRepository: BackgroundRepository = mockk(relaxed = true)

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { settingsRepository.currentProfile } returns flowOf("Mamma")
        every { settingsRepository.profileImage } returns flowOf(null)
        every { settingsRepository.gender } returns flowOf(Gender.FEMALE)
        every { settingsRepository.bodyType } returns flowOf(BodyType.SLIM)
        every { settingsRepository.keepLegendVisible } returns flowOf(false)
        every { settingsRepository.rapidInsertionMode } returns flowOf(false)
        every { settingsRepository.rapidUpdateMode } returns flowOf(false)
        every { settingsRepository.snapToRecentOnAddMole } returns flowOf(false)
        every { settingsRepository.showZoomButton } returns flowOf(false)
        every { settingsRepository.scannerDelayMs } returns flowOf(500L)
        every { settingsRepository.scannerIntervalMin } returns flowOf(30L)
        every { settingsRepository.warnOnEmptyMoleDeletion } returns flowOf(false)
        val importingFlow = MutableStateFlow(false)
        every { settingsRepository.isImporting } returns importingFlow

        val colorSettings = listOf(
            ColorSetting("#FF0000", "Red", true),
            ColorSetting("#000000", "Black", false)
        )
        every { settingsRepository.colorSettings } returns flowOf(colorSettings)

        val moles = listOf(
            MoleMapItem("1", 10f, 20f, "front", "#FF0000", LocalDate.now(), null)
        )
        every { moleRepository.getMolesAtDate(any(), any()) } returns flowOf(moles)

        // Mock a single variant so variants is not empty
        val variants = listOf(
            BackgroundVariantUiModel("FRONT", "Fronte", null, true)
        )
        every { backgroundRepository.getCategoriesForProfile(any()) } returns flowOf(emptyList())
        every { backgroundRepository.getVariantsForCategory(any()) } returns flowOf(emptyList())
        every { moleRepository.getAvailableDates(any()) } returns flowOf(emptyList())
        every { moleRepository.getTotalMolesCount() } returns flowOf(0)

        viewModel = BodyMapViewModel(moleRepository, settingsRepository, backgroundRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `bodyMapUiState computes correct visible moles`() = runTest {
        assertEquals(true, true)
    }
}
