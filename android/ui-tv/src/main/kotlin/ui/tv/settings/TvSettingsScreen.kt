package ui.tv.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import setup.AppearanceViewModel
import setup.ProfileSettingsViewModel
import setup.SettingsViewModel
import setup.profileStatus
import setup.telegramStatus
import system.CacheBudgetViewModel
import system.LanCacheViewModel
import system.SystemViewModel
import system.storageStatus
import system.systemStatus
import ui.settings.IndexStatus
import ui.settings.SettingsSection
import ui.tv.setup.TvConfirmDialog
import ui.tv.system.TvSystemSection

/**
 * Settings and System, one hub, television-side: an index (Telegram ·
 * Storage · Appearance · System, each with its own one-line status) beside
 * the section it is showing — the approved round-2 mockups' own EXPANDED
 * layout, at ten-foot sizes; [TvMenuBranches] opens this on [initial],
 * `TELEGRAM` for the Settings menu row, `SYSTEM` for the System shortcut.
 * Every ViewModel underneath is the one Settings and System always used;
 * only the layout is new.
 *
 * [section] and [focusInContent] are hoisted here rather than kept inside
 * [TvSettingsPanes]: a library-choice or an address panel takes the whole
 * screen over that composable, unmounting it, and both have to survive that
 * for Back closing the panel to land back where it left.
 *
 * Read afresh once per visit, not once per section shown: an index status
 * has to be true while a different section is on screen, so
 * [SettingsViewModel.refresh], [CacheBudgetViewModel.refresh] and
 * [LanCacheViewModel.open] all run here, not inside whichever section
 * happens to be entered first.
 */
@Composable
internal fun TvSettingsScreen(initial: SettingsSection) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val state by settingsViewModel.state.collectAsStateWithLifecycle()
    val appearanceViewModel: AppearanceViewModel = hiltViewModel()
    val appearance by appearanceViewModel.state.collectAsStateWithLifecycle()
    val profileViewModel: ProfileSettingsViewModel = hiltViewModel()
    val profile by profileViewModel.profile.collectAsStateWithLifecycle()
    val profileSubtitle by profileViewModel.subtitle.collectAsStateWithLifecycle()
    val cacheViewModel: CacheBudgetViewModel = hiltViewModel()
    val cacheState by cacheViewModel.state.collectAsStateWithLifecycle()
    val lanViewModel: LanCacheViewModel = hiltViewModel()
    val lanState by lanViewModel.state.collectAsStateWithLifecycle()
    val systemViewModel: SystemViewModel = hiltViewModel()
    val systemState by systemViewModel.state.collectAsStateWithLifecycle()
    val systemFailure by systemViewModel.failure.collectAsStateWithLifecycle()

    var section by rememberSaveable { mutableStateOf(initial) }
    // Always false on a fresh open: even the System shortcut lands on its
    // own index row first, the remote's Right or OK the only way in.
    var focusInContent by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf<TvSettingsPanel?>(null) }
    var lastPanel by rememberSaveable { mutableStateOf<TvSettingsPanel?>(null) }
    var openedBeforeAction by rememberSaveable { mutableStateOf(state.completedActionId) }
    var askingSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settingsViewModel.refresh()
        cacheViewModel.refresh()
        lanViewModel.open()
    }

    // Independent of the library's acknowledgement: both surfaces must see
    // success, even when this form was detached when the action finished.
    LaunchedEffect(state.completedActionId) {
        if (state.completedActionId > openedBeforeAction) panel = null
    }

    // lastPanel only means something for the one section it closed back
    // into; left set, a later plain index re-entry (no panel involved at
    // all) would wrongly replay that panel's own return-focus instead of
    // this section's default first control.
    LaunchedEffect(panel) { if (panel == null) lastPanel = null }

    val open = { which: TvSettingsPanel ->
        settingsViewModel.clearNotice()
        openedBeforeAction = state.completedActionId
        panel = which
        lastPanel = which
    }

    // A local val, not the `panel` property itself: only a true local can
    // be smart-cast to the non-null enum the panel screen takes, once this
    // branch is the one running.
    val openPanel = panel
    if (openPanel != null) {
        TvSettingsPanelScreen(panel = openPanel, state = state, settingsViewModel = settingsViewModel, onClose = { panel = null })
    } else {
        val statuses =
            mapOf(
                SettingsSection.TELEGRAM to IndexStatus(telegramStatus(state)),
                SettingsSection.STORAGE to storageStatus(cacheState, lanState).let { (text, held) -> IndexStatus(text, held) },
                SettingsSection.APPEARANCE to IndexStatus("${appearance.accent.label} · ${appearance.backdrop.label}"),
                SettingsSection.PROFILE to IndexStatus(profileStatus(profile)),
                SettingsSection.SYSTEM to IndexStatus(systemStatus(systemState, systemFailure)),
            )
        TvSettingsPanes(
            section = section,
            statuses = statuses,
            focusInContent = focusInContent,
            onSelectSection = { section = it },
            onFocusInContentChange = { focusInContent = it },
        ) { shown, entered, entryRequester ->
            when (shown) {
                SettingsSection.TELEGRAM ->
                    TvTelegramSection(
                        state = state,
                        focusInContent = entered,
                        returningFrom = lastPanel,
                        entryFocusRequester = entryRequester,
                        onChangeLibrary = {
                            open(TvSettingsPanel.Library)
                            settingsViewModel.listLibraries()
                        },
                        onChangeApplication = { open(TvSettingsPanel.Application) },
                        onSignOut = { askingSignOut = true },
                        onRetryProfiles = settingsViewModel::retryProfiles,
                        onLoadSessions = settingsViewModel::loadSessions,
                        onRevokeSession = settingsViewModel::revokeSession,
                    )

                SettingsSection.STORAGE ->
                    TvStorageSection(
                        focusInContent = entered,
                        returningFrom = lastPanel,
                        entryFocusRequester = entryRequester,
                        onOpenLanCache = open,
                    )

                SettingsSection.APPEARANCE ->
                    TvAppearanceSection(
                        accent = appearance.accent,
                        backdrop = appearance.backdrop,
                        focusInContent = entered,
                        entryFocusRequester = entryRequester,
                        onSelectAccent = appearanceViewModel::chooseAccent,
                        onSelectBackdrop = appearanceViewModel::chooseBackdrop,
                    )

                SettingsSection.PROFILE ->
                    TvProfileSection(
                        profile = profile,
                        subtitle = profileSubtitle,
                        focusInContent = entered,
                        entryFocusRequester = entryRequester,
                        onChooseSubtitle = profileViewModel::chooseSubtitle,
                    )

                SettingsSection.SYSTEM ->
                    TvSystemSection(
                        focusInContent = entered,
                        current = systemState,
                        failure = systemFailure,
                        entryFocusRequester = entryRequester,
                        onRetry = systemViewModel::retry,
                    )
            }
        }
    }

    if (askingSignOut) {
        TvConfirmDialog(
            title = "Sign out?",
            body = SIGN_OUT_BODY,
            confirmLabel = "Sign out",
            confirm = {
                askingSignOut = false
                settingsViewModel.signOut()
            },
            cancel = { askingSignOut = false },
        )
    }
}

/**
 * The phone's words, worded against start over's: this ends the login and
 * takes the account's catalog with it, but keeps what is the device's own,
 * so signing back in is all that is left to do.
 */
private const val SIGN_OUT_BODY =
    "This signs this device out of Telegram and removes the library it " +
        "was reading. The api_id, api_hash and TMDB key stay; signing in " +
        "again is all it takes to come back."
