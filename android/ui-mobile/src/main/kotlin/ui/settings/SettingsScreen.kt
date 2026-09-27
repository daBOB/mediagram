package ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import designsystem.Spacing
import setup.AppearanceViewModel
import setup.SettingsViewModel
import setup.telegramStatus
import system.CacheBudgetViewModel
import system.LanCacheViewModel
import system.SystemViewModel
import system.storageStatus
import system.systemStatus
import ui.setup.LibraryScreen
import ui.setup.TelegramApplicationScreen

/** A part of Settings that takes the whole screen while it is open. */
private enum class SettingsPanel { Library, Application }

/**
 * Settings + System, one frame: an index (Telegram · Storage · Appearance ·
 * System, each with its own one-line status) and a page — two panes on
 * EXPANDED width, one pane below it — the approved round-2 mockups. Every
 * ViewModel underneath is the one Settings and System always used; only the
 * layout is new.
 *
 * @param initial The section the page opens on — `null` shows the compact
 * index first (the Settings menu item); a section opens straight to it (the
 * System menu shortcut).
 * @param leavesFromSection On compact width, whether Back from an open
 * section leaves the frame directly rather than returning to the index —
 * true only for a section opened directly, never for one reached by tapping
 * an index row.
 */
@Composable
internal fun SettingsScreen(
    initial: SettingsSection?,
    leavesFromSection: Boolean,
    tally: List<String>,
    onLeave: () -> Unit,
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
    val appearanceViewModel: AppearanceViewModel = hiltViewModel()
    val appearance by appearanceViewModel.state.collectAsStateWithLifecycle()
    val cacheViewModel: CacheBudgetViewModel = hiltViewModel()
    val cacheState by cacheViewModel.state.collectAsStateWithLifecycle()
    val lanViewModel: LanCacheViewModel = hiltViewModel()
    val lanState by lanViewModel.state.collectAsStateWithLifecycle()
    val systemViewModel: SystemViewModel = hiltViewModel()
    val systemState by systemViewModel.state.collectAsStateWithLifecycle()
    val systemFailure by systemViewModel.failure.collectAsStateWithLifecycle()

    var section by rememberSaveable { mutableStateOf(initial) }
    var panel by rememberSaveable { mutableStateOf<SettingsPanel?>(null) }
    var openedBeforeAction by rememberSaveable { mutableStateOf(settingsState.completedActionId) }
    var askingSignOut by remember { mutableStateOf(false) }

    // Read afresh on every visit: every ViewModel here outlives this
    // screen, and a row read before a sign-out and a new sign-in — or a
    // budget changed on another visit — would still name the old answer.
    LaunchedEffect(Unit) {
        settingsViewModel.refresh()
        cacheViewModel.refresh()
        lanViewModel.open()
    }

    // Independent of the library's acknowledgement: both surfaces must see
    // success, even when this form was detached when the action finished.
    LaunchedEffect(settingsState.completedActionId) {
        if (settingsState.completedActionId > openedBeforeAction) panel = null
    }

    when (panel) {
        SettingsPanel.Library -> {
            BackHandler { panel = null }
            Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                LibraryScreen(
                    choices = settingsState.choices,
                    error = settingsState.notice,
                    // One install at a time; a second tap waits for the first.
                    onChoose = { handle -> if (!settingsState.busy) settingsViewModel.chooseLibrary(handle) },
                    onLookAgain = settingsViewModel::listLibraries,
                )
            }
        }

        SettingsPanel.Application -> {
            BackHandler { panel = null }
            Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                if (settingsState.profileReloadNeeded) {
                    ProfileReload(settingsState, settingsViewModel::retryProfiles)
                } else {
                    TelegramApplicationScreen(
                        error = settingsState.notice,
                        onSubmit = settingsViewModel::changeApplication,
                        initialApiId = settingsState.apiId?.toString().orEmpty(),
                    )
                }
            }
        }

        null -> {
            val statuses =
                mapOf(
                    SettingsSection.TELEGRAM to IndexStatus(telegramStatus(settingsState)),
                    SettingsSection.STORAGE to storageStatus(cacheState, lanState).let { (text, held) -> IndexStatus(text, held) },
                    SettingsSection.APPEARANCE to IndexStatus("${appearance.theme.label} · ${appearance.accent.label}"),
                    SettingsSection.SYSTEM to IndexStatus(systemStatus(systemState, systemFailure)),
                )
            SettingsPanes(
                section = section,
                statuses = statuses,
                tally = tally,
                leavesFromSection = leavesFromSection,
                onSelectSection = { section = it },
                onLeave = onLeave,
            ) { shown ->
                SettingsPage(
                    section = shown,
                    settingsState = settingsState,
                    appearance = appearance,
                    onChangeLibrary = {
                        settingsViewModel.clearNotice()
                        openedBeforeAction = settingsState.completedActionId
                        panel = SettingsPanel.Library
                        settingsViewModel.listLibraries()
                    },
                    onChangeApplication = {
                        settingsViewModel.clearNotice()
                        openedBeforeAction = settingsState.completedActionId
                        panel = SettingsPanel.Application
                    },
                    onSignOut = { askingSignOut = true },
                    onLoadSessions = settingsViewModel::loadSessions,
                    onRevokeSession = settingsViewModel::revokeSession,
                    onChooseTheme = appearanceViewModel::chooseTheme,
                    onChooseAccent = appearanceViewModel::chooseAccent,
                    onChooseBackdrop = appearanceViewModel::chooseBackdrop,
                )
            }
        }
    }

    SignOutConfirmation(
        asking = askingSignOut,
        onDismiss = { askingSignOut = false },
        onConfirm = settingsViewModel::signOut,
    )
}
