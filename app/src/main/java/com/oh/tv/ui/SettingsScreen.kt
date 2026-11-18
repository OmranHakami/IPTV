package com.oh.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.oh.tv.R
import com.oh.tv.data.CatalogCounts
import com.oh.tv.security.SubtitleCredentialStore
import com.oh.tv.ui.components.FocusSurface
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale

private val SettingsBackground = Color(0xFF090B10)
private val SettingsPanel = Color(0xFF0D131F)
private val SettingsSurface = Color(0xFF111927)
private val SettingsInset = Color(0xFF18233A)
private val SettingsStroke = Color(0xFF202A3A)
private val SettingsBright = Color(0xFFF5F2EB)
private val SettingsMuted = Color(0xFF8996AA)
private val SettingsQuiet = Color(0xFF71809B)
private val SettingsBlue = Color(0xFF637DF7)
private val SettingsPeach = Color(0xFFFF8A4C)
private val SettingsSuccess = Color(0xFF47D18C)
private val SettingsDanger = Color(0xFFFF718A)

private enum class SettingsSection(val title: String, val description: String, val icon: String) {
    Account("Account", "Connection and subscription", "user"),
    Library("Library", "Refresh saved catalog", "refresh"),
    Subtitles("Subtitles", "OpenSubtitles access", "settings"),
}

@Composable
fun SettingsScreen(
    state: OhUiState,
    counts: CatalogCounts,
    back: () -> Unit,
    sync: () -> Unit,
    removeAccount: () -> Unit,
) {
    val context = LocalContext.current
    val credentialStore = remember(context) { SubtitleCredentialStore(context) }
    var section by remember { mutableStateOf(SettingsSection.Account) }
    var subtitleKey by remember { mutableStateOf("") }
    var keyConfigured by remember { mutableStateOf(credentialStore.isConfigured()) }
    var keyStatus by remember { mutableStateOf<String?>(null) }
    var confirmRemoval by remember { mutableStateOf(false) }

    BackHandler(enabled = confirmRemoval) { confirmRemoval = false }
    BoxWithConstraints(Modifier.fillMaxSize().background(SettingsBackground)) {
        val density = LocalDensity.current
        val scale = minOf(maxWidth.value / 1440f, maxHeight.value / 810f)
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            Box(Modifier.align(Alignment.Center).size(1440.dp, 810.dp).background(SettingsBackground)) {
                SettingsTopBar(state.account?.displayName ?: "My service", back)
                Row(
                    Modifier.offset(48.dp, 112.dp).size(1344.dp, 646.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    SettingsSidebar(section) { section = it }
                    Box(
                        Modifier.fillMaxHeight().weight(1f).clip(RoundedCornerShape(18.dp))
                            .background(SettingsPanel).border(1.dp, SettingsStroke, RoundedCornerShape(18.dp))
                            .padding(horizontal = 30.dp, vertical = 26.dp),
                    ) {
                        when (section) {
                            SettingsSection.Account -> AccountSettings(
                                state = state,
                                onRemove = { confirmRemoval = true },
                            )
                            SettingsSection.Library -> LibrarySettings(counts, sync)
                            SettingsSection.Subtitles -> SubtitleSettings(
                                key = subtitleKey,
                                configured = keyConfigured,
                                status = keyStatus,
                                onKeyChange = { subtitleKey = it; keyStatus = null },
                                onSave = {
                                    if (subtitleKey.isBlank()) {
                                        keyStatus = "Enter an API key first"
                                    } else {
                                        runCatching { credentialStore.save(subtitleKey) }
                                            .onSuccess {
                                                subtitleKey = ""
                                                keyConfigured = true
                                                keyStatus = "API key saved securely on this TV"
                                            }
                                            .onFailure { keyStatus = "Could not save API key" }
                                    }
                                },
                                onRemove = {
                                    runCatching { credentialStore.save("") }
                                        .onSuccess {
                                            subtitleKey = ""
                                            keyConfigured = false
                                            keyStatus = "API key removed"
                                        }
                                        .onFailure { keyStatus = "Could not remove API key" }
                                },
                            )
                        }
                    }
                }
                SettingsFooter()
                if (confirmRemoval) {
                    RemoveAccountConfirmation(
                        onCancel = { confirmRemoval = false },
                        onConfirm = {
                            confirmRemoval = false
                            removeAccount()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsTopBar(profileName: String, onBack: () -> Unit) {
    Row(
        Modifier.offset(32.dp, 18.dp).size(1376.dp, 56.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            FocusSurface(onBack, Modifier.size(44.dp), SettingsSurface, SettingsInset) {
                PairingIcon("back", SettingsBright, Modifier.size(20.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(painterResource(R.drawable.pairing_brand), null, Modifier.size(32.dp))
                Text("CHROMA", color = SettingsBright, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            }
        }
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF101725))
                .border(1.dp, Color(0xFF253047), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            PairingIcon("user", Color(0xFF91A7FF), Modifier.size(17.dp))
            Text(profileName, color = Color(0xFFDCE4F5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingsSidebar(selected: SettingsSection, onSelect: (SettingsSection) -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
    Column(Modifier.width(256.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("SETTINGS", color = SettingsQuiet, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
        Text("Settings", color = SettingsBright, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Text("Only controls that change how CHROMA works on this TV.", color = SettingsMuted, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(16.dp))
        SettingsSection.entries.forEachIndexed { index, section ->
            val isSelected = selected == section
            FocusSurface(
                onClick = { onSelect(section) },
                modifier = Modifier.fillMaxWidth().height(72.dp)
                    .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                    .testTag("settings-${section.name.lowercase()}"),
                background = if (isSelected) SettingsInset else SettingsPanel,
                focusedBackground = Color(0xFF263247),
                contentPadding = 0.dp,
                borderColor = if (isSelected) SettingsBlue else SettingsStroke,
                borderWidth = 1.dp,
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) SettingsBlue else Color(0xFF151E2E)),
                        contentAlignment = Alignment.Center,
                    ) { PairingIcon(section.icon, if (isSelected) Color.White else Color(0xFF8FA0B9), Modifier.size(18.dp)) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(section.title, color = if (isSelected) Color.White else Color(0xFFC8D1E0), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(section.description, color = Color(0xFF76859C), fontSize = 10.sp)
                    }
                    if (isSelected) Box(Modifier.width(3.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(SettingsPeach))
                }
            }
        }
    }
}

@Composable
private fun AccountSettings(state: OhUiState, onRemove: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SettingsPanelHeader("Account", "Your provider connection on this device.", "ACTIVE", true)
        Divider()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsDetail("SERVICE", state.account?.displayName ?: "Unavailable", "tv")
            SettingsDetail("SERVER", state.account?.baseUrl?.substringAfter("://") ?: "Unavailable", "link")
            SettingsDetail(
                "SUBSCRIPTION",
                state.account?.expiresAtEpochSeconds?.let { "${state.account.status} · expires ${Instant.ofEpochSecond(it).toString().take(10)}" }
                    ?: state.account?.status ?: "Unavailable",
                "shield",
            )
        }
        SecurityNote("Credentials are encrypted and kept on this TV.")
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Remove account", color = Color(0xFFE8EDF7), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Deletes this account and its local data from this TV.", color = SettingsQuiet, fontSize = 11.sp)
            }
            SettingsButton(
                "Remove account",
                "alert",
                modifier = Modifier.testTag("remove-account-button"),
                danger = true,
                onClick = onRemove,
            )
        }
    }
}

@Composable
private fun LibrarySettings(counts: CatalogCounts, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SettingsPanelHeader("Library", "Refresh the catalog stored on this TV.", "READY", true)
        Divider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LibraryCount("Movies", counts.movies, "movie", Modifier.weight(1f))
            LibraryCount("TV Shows", counts.series, "shows", Modifier.weight(1f))
            LibraryCount("Live TV", counts.liveChannels, "radio", Modifier.weight(1f))
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(SettingsSurface).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Refresh all content", color = Color(0xFFE8EDF7), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "Updates movies, shows, episodes, categories, and channels from your provider. Watchlist items and playback progress stay saved.",
                color = SettingsMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                PairingIcon("shield", Color(0xFF62CBB1), Modifier.size(16.dp))
                Text("Safe to run at any time", color = Color(0xFF9ED9CC), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.weight(1f))
        SettingsButton("Refresh library", "refresh", primary = true, modifier = Modifier.fillMaxWidth(), onClick = onRefresh)
    }
}

@Composable
private fun SubtitleSettings(
    key: String,
    configured: Boolean,
    status: String?,
    onKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SettingsPanelHeader("Subtitles", "Connect OpenSubtitles for downloaded subtitle tracks.", if (configured) "CONFIGURED" else "NOT CONFIGURED", configured)
        Divider()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("OPENSUBTITLES API KEY", color = SettingsQuiet, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            SecureKeyField(key, onKeyChange)
            Text("Saving a new key replaces the currently configured key.", color = SettingsQuiet, fontSize = 11.sp)
            SettingsButton("Save API key", "check", primary = true, modifier = Modifier.fillMaxWidth(), onClick = onSave)
            status?.let { Text(it, color = if (it.startsWith("Could") || it.startsWith("Enter")) SettingsDanger else SettingsSuccess, fontSize = 11.sp) }
        }
        SecurityNote("The API key is encrypted with Android Keystore and never displayed again.")
        Spacer(Modifier.weight(1f))
        if (configured) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Disconnect OpenSubtitles", color = Color(0xFFE8EDF7), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Removes the saved API key from this TV.", color = SettingsQuiet, fontSize = 11.sp)
                }
                SettingsButton("Remove API key", "alert", danger = true, onClick = onRemove)
            }
        }
    }
}

@Composable
private fun SettingsPanelHeader(title: String, subtitle: String, badge: String, active: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = SettingsBright, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = SettingsMuted, fontSize = 13.sp)
        }
        Row(
            Modifier.clip(CircleShape).background(if (active) Color(0xFF10271F) else SettingsSurface)
                .border(1.dp, if (active) Color(0xFF235840) else SettingsStroke, CircleShape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (active) SettingsSuccess else SettingsQuiet))
            Text(badge, color = if (active) Color(0xFF79E5AD) else SettingsMuted, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun SettingsDetail(label: String, value: String, icon: String) {
    Row(
        Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(12.dp)).background(SettingsSurface).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(SettingsInset), contentAlignment = Alignment.Center) {
            PairingIcon(icon, Color(0xFF91A7FF), Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = SettingsQuiet, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
            Text(value, color = Color(0xFFE8EDF7), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LibraryCount(label: String, count: Int, icon: String, modifier: Modifier) {
    Column(
        modifier.height(116.dp).clip(RoundedCornerShape(13.dp)).background(SettingsSurface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = SettingsMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            PairingIcon(icon, Color(0xFF91A7FF), Modifier.size(17.dp))
        }
        Text(NumberFormat.getIntegerInstance(Locale.US).format(count), color = SettingsBright, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SecurityNote(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0E1C1B))
            .border(1.dp, Color(0xFF1D4640), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PairingIcon("lock", Color(0xFF62CBB1), Modifier.size(17.dp))
        Text(text, color = Color(0xFF9ED9CC), fontSize = 12.sp)
    }
}

@Composable
private fun SecureKeyField(value: String, onValueChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFFE8EDF7), fontSize = 13.sp),
        visualTransformation = PasswordVisualTransformation(),
        cursorBrush = SolidColor(SettingsBlue),
        modifier = Modifier.fillMaxWidth().height(56.dp).onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(12.dp)).background(SettingsSurface)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color(0xFF35415A), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp),
        decorationBox = { input ->
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PairingIcon("lock", Color(0xFF91A7FF), Modifier.size(18.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text("Enter a new API key", color = SettingsQuiet, fontSize = 13.sp)
                    input()
                }
            }
        },
    )
}

@Composable
private fun SettingsButton(
    label: String,
    icon: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val background = when {
        primary -> SettingsBlue
        danger -> Color(0xFF211318)
        else -> SettingsSurface
    }
    val content = if (danger) SettingsDanger else Color.White
    FocusSurface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        background = background,
        focusedBackground = when {
            danger -> Color(0xFF7B2B3B)
            primary -> Color(0xFF7389FF)
            else -> SettingsInset
        },
        contentPadding = 0.dp,
        borderColor = if (danger) Color(0xFF7B2B3B) else null,
        borderWidth = 1.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            PairingIcon(icon, content, Modifier.size(17.dp))
            Text(label, color = content, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RemoveAccountConfirmation(onCancel: () -> Unit, onConfirm: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    Box(
        Modifier.fillMaxSize().background(Color(0xCC02050A)).testTag("remove-account-confirmation"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.size(540.dp, 338.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF111827))
                .border(1.dp, Color(0xFF38445A), RoundedCornerShape(18.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF351820)), contentAlignment = Alignment.Center) {
                PairingIcon("alert", SettingsDanger, Modifier.size(23.dp))
            }
            Text("Remove this account?", color = SettingsBright, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "This deletes the provider account, downloaded catalog, watchlist, and playback progress from this TV. This cannot be undone.",
                color = Color(0xFF9AA7BA),
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusSurface(
                    onCancel,
                    Modifier.weight(1f).height(48.dp).focusRequester(cancelFocus),
                    Color(0xFF1B2433),
                    SettingsInset,
                    borderColor = Color(0xFF39465A),
                    borderWidth = 1.dp,
                ) { Text("Cancel", color = Color(0xFFE8EDF7), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                SettingsButton("Remove account", "alert", Modifier.weight(1f), danger = true, onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(SettingsStroke))

@Composable
private fun SettingsFooter() {
    Row(
        Modifier.offset(48.dp, 780.dp).size(1344.dp, 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Use ↑ ↓ to navigate · OK to select · Back to return", color = Color(0xFF56647A), fontSize = 10.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(SettingsSuccess))
            Text("Saved on this device", color = SettingsQuiet, fontSize = 10.sp)
        }
    }
}
