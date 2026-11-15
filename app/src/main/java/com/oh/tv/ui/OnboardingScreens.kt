package com.oh.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.oh.tv.data.CatalogCounts
import com.oh.tv.data.SyncProgress
import com.oh.tv.data.SyncScopes
import com.oh.tv.data.SyncState
import com.oh.tv.data.remote.AccountInput
import com.oh.tv.ui.components.FocusSurface
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

private val Ink = Color(0xFF070A10)
private val Panel = Color(0xFF151C2A)
private val PanelDeep = Color(0xFF0B1019)
private val Stroke = Color(0xFF2C3649)
private val Bright = Color(0xFFF7F8FC)
private val Quiet = Color(0xFF98A3B7)
private val Peach = Color(0xFFFFB86B)
private val Coral = Color(0xFFFF7B72)
private val ChoiceBlue = Color(0xFF5B7CFF)
private val Success = Color(0xFF71E5B1)

@Composable
private fun DesignedScreen(content: @Composable () -> Unit) {
    val profile = LocalDeviceProfile.current
    val horizontalPadding = if (profile.isTv) 42.dp else 30.dp
    val verticalPadding = if (profile.isTv) 24.dp else 18.dp
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Ink, Color(0xFF0D1220), Color(0xFF12101B))),
        ),
    ) {
        Box(
            Modifier.size(520.dp).offset(x = 760.dp, y = (-300).dp)
                .background(Brush.radialGradient(listOf(Color(0x30FF9D66), Color.Transparent)), RoundedCornerShape(260.dp)),
        )
        Box(
            Modifier.size(520.dp).offset(x = (-280).dp, y = 360.dp)
                .background(Brush.radialGradient(listOf(Color(0x245B7CFF), Color.Transparent)), RoundedCornerShape(260.dp)),
        )
        Box(Modifier.fillMaxSize().padding(horizontal = horizontalPadding, vertical = verticalPadding)) { content() }
    }
}

@Composable
private fun DesignTopBar(step: String? = null, onBack: (() -> Unit)? = null, onSettings: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onBack != null) CompactAction("‹", onBack)
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
                    .background(Brush.linearGradient(listOf(Peach, Coral))),
                contentAlignment = Alignment.Center,
            ) { Text("▶", color = Color(0xFF17100C), fontSize = 15.sp, fontWeight = FontWeight.Black) }
            Text("OH", color = Bright, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
        }
        when {
            onSettings != null -> CompactAction("⚙  Settings", onSettings)
            step != null -> Text(step, color = Quiet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CompactAction(label: String, onClick: () -> Unit) {
    FocusSurface(onClick, background = Panel, focusedBackground = Color(0xFF273249), contentPadding = 8.dp) {
        Text(label, color = Bright, fontSize = 12.sp)
    }
}

@Composable
fun OnboardingChoiceScreen(onPhone: () -> Unit, onManual: () -> Unit) {
    val phoneFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { phoneFocus.requestFocus() }
    DesignedScreen {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            DesignTopBar(step = "Connect service  •  1 / 3")
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Connect your TV service", color = Bright, fontSize = 31.sp, fontWeight = FontWeight.Normal)
                Text("Use your phone for the fastest setup, or enter the provider details with your remote.", color = Quiet, fontSize = 14.sp)
            }
            Row(Modifier.fillMaxSize().padding(horizontal = 110.dp, vertical = 10.dp).focusGroup(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                SetupChoiceCard(
                    icon = "▣",
                    badge = "FASTEST WAY",
                    title = "Finish on your phone",
                    description = "Open a secure one-time link and enter your provider details using your phone keyboard.",
                    action = "Create pairing code",
                    onClick = onPhone,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    focusRequester = phoneFocus,
                    primary = true,
                )
                SetupChoiceCard(
                    icon = "⌨",
                    badge = "TV REMOTE",
                    title = "Enter details on this TV",
                    description = "Use the on-screen keyboard to enter the server URL, username and password.",
                    action = "Use manual setup",
                    onClick = onManual,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun SetupChoiceCard(
    icon: String,
    badge: String,
    title: String,
    description: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier,
    focusRequester: FocusRequester? = null,
    primary: Boolean = false,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier,
        background = Panel,
        focusedBackground = Color(0xFF1B2638),
        focusRequester = focusRequester,
    ) {
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(if (primary) Color(0x24FFB86B) else Color(0x245B7CFF)), contentAlignment = Alignment.Center) {
                    Text(icon, color = if (primary) Peach else ChoiceBlue, fontSize = 26.sp)
                }
                Text(badge, color = if (primary) Peach else ChoiceBlue, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(title, color = Bright, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = Quiet, fontSize = 13.sp, lineHeight = 19.sp)
            Text("$action  →", color = if (primary) Peach else ChoiceBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PhonePairingScreen(state: PairingUiState, onRetry: () -> Unit, onManual: () -> Unit, onBack: () -> Unit) {
    var remainingMs by remember(state.expiresAtEpochMs) { mutableLongStateOf((state.expiresAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0)) }
    LaunchedEffect(state.expiresAtEpochMs, state.phase) {
        while (state.phase == PairingPhase.Waiting && remainingMs > 0) {
            remainingMs = (state.expiresAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0)
            delay(1_000)
        }
    }
    val manualFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(state.phase) {
        if (state.phase in setOf(PairingPhase.Expired, PairingPhase.Failed)) retryFocus.requestFocus()
        else manualFocus.requestFocus()
    }
    DesignedScreen {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            DesignTopBar(step = "Connect service", onBack = onBack)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(
                    Modifier.weight(1.8f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Panel).border(1.dp, Stroke, RoundedCornerShape(24.dp)).padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Text("FASTEST WAY", color = Peach, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    Text("Finish setup on your phone", color = Bright, fontSize = 30.sp)
                    Text("Open the secure link, enter this code, and your TV connects automatically.", color = Quiet, fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        InfoPanel("ON YOUR PHONE, VISIT", state.linkUrl.substringBefore('?').ifBlank { "Pairing service unavailable" }, Modifier.weight(1f))
                        InfoPanel("YOUR ONE-TIME CODE", state.code.ifBlank { "— — —" }, Modifier.weight(1.45f), warm = true)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        StepPanel("1", "Open the link", "Use any phone or tablet", Modifier.weight(1f))
                        StepPanel("2", "Enter the code", if (remainingMs > 0) "Expires in ${formatRemaining(remainingMs)}" else "Waiting for a code", Modifier.weight(1f))
                        StepPanel("3", "Confirm provider", "OH verifies and starts sync", Modifier.weight(1f))
                    }
                    state.message?.let { Text(it, color = if (state.phase == PairingPhase.Received) Success else Coral, fontSize = 13.sp) }
                    if (state.phase in setOf(PairingPhase.Expired, PairingPhase.Failed)) {
                        FocusSurface(onRetry, Modifier.fillMaxWidth().focusRequester(retryFocus), ChoiceBlue, ChoiceBlue, 12.dp) {
                            Text("Create a new code", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Column(Modifier.weight(0.85f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(Panel).border(1.dp, Stroke, RoundedCornerShape(20.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Connection status", color = Bright, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(pairingStatusText(state.phase), color = if (state.phase == PairingPhase.Received) Success else Peach, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("OH TV", color = Bright, fontSize = 15.sp)
                        Text("Local session • same Wi-Fi only", color = Quiet, fontSize = 12.sp)
                    }
                    Column(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(Panel).border(1.dp, Stroke, RoundedCornerShape(20.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Prefer the remote?", color = Bright, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("Enter provider details directly on this TV.", color = Quiet, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        FocusSurface(onManual, Modifier.fillMaxWidth(), PanelDeep, Color(0xFF273249), 11.dp, manualFocus) {
                            Text("Use manual setup  →", color = Bright, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPanel(label: String, value: String, modifier: Modifier, warm: Boolean = false) {
    Column(
        modifier.height(105.dp).clip(RoundedCornerShape(17.dp)).background(if (warm) Brush.linearGradient(listOf(Color(0xFFFFF3E2), Color(0xFFFFD8BC))) else Brush.linearGradient(listOf(PanelDeep, PanelDeep))).padding(17.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = if (warm) Color(0xFF79533C) else Quiet, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        Text(value, color = if (warm) Color(0xFF21140D) else Bright, fontSize = if (warm) 27.sp else 16.sp, fontWeight = if (warm) FontWeight.ExtraBold else FontWeight.Normal, letterSpacing = if (warm) 3.sp else 0.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StepPanel(number: String, title: String, detail: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF0D121B)).border(1.dp, Color(0xFF242D3D), RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x24FFB86B)), contentAlignment = Alignment.Center) { Text(number, color = Peach, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            Text(title, color = Bright, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(detail, color = Quiet, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ManualSetupScreen(busy: Boolean, message: String?, onConnect: (AccountInput) -> Unit, onBack: () -> Unit) {
    var accountName by remember { mutableStateOf("Living Room TV") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val serverFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { serverFocus.requestFocus() }
    DesignedScreen {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(17.dp)) {
            DesignTopBar(step = "Provider details", onBack = onBack)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Column(Modifier.weight(0.85f).fillMaxHeight().padding(10.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    Text("REMOTE SETUP", color = ChoiceBlue, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    Text("Enter your provider details", color = Bright, fontSize = 29.sp)
                    Text("You’ll find these details in the welcome email or account page from your TV provider.", color = Quiet, fontSize = 13.sp, lineHeight = 19.sp)
                    GuideLine("⌁", "Server URL", "Starts with http:// or https://")
                    GuideLine("@", "Username", "Usually case-sensitive")
                    GuideLine("●", "Password", "Exactly as provided")
                    Spacer(Modifier.weight(1f))
                    Text("▣  Credentials stay encrypted on this TV", color = Success, fontSize = 12.sp)
                }
                Column(
                    Modifier.weight(1.35f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Panel).border(1.dp, Stroke, RoundedCornerShape(24.dp)).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Connect your service", color = Bright, fontSize = 21.sp, fontWeight = FontWeight.SemiBold); Text("Use the exact details supplied by your provider.", color = Quiet, fontSize = 11.sp) }
                        Text("XTREAM CODES", color = Success, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    DesignField(accountName, { accountName = it }, "ACCOUNT NAME", "Living Room TV")
                    DesignField(server, { server = it }, "SERVER URL", "https://line.provider.tv:8080", focusRequester = serverFocus)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DesignField(username, { username = it }, "USERNAME", "Provider username", Modifier.weight(1f))
                        DesignField(password, { password = it }, "PASSWORD", "Provider password", Modifier.weight(1f), password = true)
                    }
                    Text(if (server.isBlank() || server.startsWith("http://") || server.startsWith("https://")) "✓  Server address format looks correct" else "Server URL must start with http:// or https://", color = if (server.isBlank() || server.startsWith("http")) Success else Coral, fontSize = 10.sp)
                    message?.let { Text(it, color = Coral, fontSize = 11.sp, maxLines = 2) }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FocusSurface(onBack, Modifier.weight(0.65f), PanelDeep, Color(0xFF273249), 11.dp) { Text("Cancel", color = Bright) }
                        FocusSurface(
                            onClick = { if (!busy) onConnect(AccountInput(accountName, server, username, password)) },
                            modifier = Modifier.weight(1.35f), background = ChoiceBlue, focusedBackground = ChoiceBlue, contentPadding = 11.dp,
                        ) { Text(if (busy) "Connecting…" else "Log in  →", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideLine(icon: String, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x245B7CFF)), contentAlignment = Alignment.Center) { Text(icon, color = ChoiceBlue, fontWeight = FontWeight.Bold) }
        Column { Text(title, color = Bright, fontSize = 13.sp, fontWeight = FontWeight.Bold); Text(detail, color = Quiet, fontSize = 11.sp) }
    }
}

@Composable
private fun DesignField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    password: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(13.dp)
    Column(
        modifier.clip(shape).background(PanelDeep)
            .border(if (focused) 3.dp else 1.dp, if (focused) Bright else Stroke, shape)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, color = if (focused) Peach else Quiet, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Bright, fontSize = 12.sp),
            cursorBrush = SolidColor(Peach),
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            decorationBox = { input -> if (value.isBlank()) Text(placeholder, color = Color(0xFF667289), fontSize = 12.sp) else input() },
            modifier = Modifier.fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        )
    }
}

@Composable
fun LibraryPreparationScreen(state: OhUiState, onOpenReady: () -> Unit, onRetry: () -> Unit) {
    val openFocus = remember { FocusRequester() }
    val usable = state.syncProgress.any { it.state in setOf(SyncState.Ready, SyncState.Stale) }
    val finished = state.syncProgress.count { it.state in setOf(SyncState.Ready, SyncState.Empty, SyncState.Stale, SyncState.Failed) }
    val percent = (finished * 100 / 3).coerceIn(0, 100)
    LaunchedEffect(usable) { if (usable) openFocus.requestFocus() }
    DesignedScreen {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            DesignTopBar(step = "Connected securely")
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Column(Modifier.weight(0.72f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(Color(0x24FFB86B)), contentAlignment = Alignment.Center) { Text("↻", color = Peach, fontSize = 27.sp) }
                    Text("Preparing OH", color = Bright, fontSize = 29.sp)
                    Text("Ready sections can be opened while the rest finishes.", color = Quiet, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Overall progress", color = Bright, fontSize = 11.sp); Text("$percent%", color = Peach, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF242B3A))) {
                        Box(Modifier.fillMaxWidth(percent / 100f).height(6.dp).background(Brush.horizontalGradient(listOf(Coral, Peach))))
                    }
                    SyncRow("Movies", state.syncProgress.firstOrNull { it.scope == SyncScopes.MOVIES })
                    SyncRow("TV Shows", state.syncProgress.firstOrNull { it.scope == SyncScopes.TV_SHOWS })
                    SyncRow("Live TV", state.syncProgress.firstOrNull { it.scope == SyncScopes.LIVE_TV })
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(Panel).border(1.dp, Stroke, RoundedCornerShape(20.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Text("✦", color = ChoiceBlue, fontSize = 20.sp); Text("TMDB enrichment", color = Bright, fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Text("ON DEMAND", color = ChoiceBlue, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold) }
                        Text("Posters, ratings and richer details load safely as titles become visible. OH does not launch tens of thousands of requests at once.", color = Quiet, fontSize = 12.sp, lineHeight = 18.sp)
                        Spacer(Modifier.weight(1f))
                        Text("Automatic while browsing", color = Success, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    state.message?.let { Text(it, color = Coral, fontSize = 12.sp) }
                    if (usable) {
                        FocusSurface(onOpenReady, Modifier.fillMaxWidth(), ChoiceBlue, ChoiceBlue, 13.dp, openFocus) { Text("▶  Start watching ready content", color = Color.White, fontWeight = FontWeight.Bold) }
                    } else if (!state.busy) {
                        FocusSurface(onRetry, Modifier.fillMaxWidth(), ChoiceBlue, ChoiceBlue, 13.dp, openFocus) { Text("Try synchronization again", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                    Text("This screen updates automatically", color = Quiet, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}

@Composable
private fun SyncRow(label: String, progress: SyncProgress?) {
    val stateText = when (progress?.state) {
        SyncState.Running -> "Syncing"
        SyncState.Ready -> "Ready"
        SyncState.Empty -> "Empty"
        SyncState.Stale -> "Cached"
        SyncState.Failed -> "Failed"
        else -> "Waiting"
    }
    val color = when (progress?.state) { SyncState.Ready, SyncState.Stale -> Success; SyncState.Failed -> Coral; SyncState.Running -> Peach; else -> Quiet }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).border(1.dp, Stroke, RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column { Text(label, color = Bright, fontSize = 13.sp, fontWeight = FontWeight.Bold); Text(stateText, color = color, fontSize = 10.sp) }
        Text(formatCount(progress?.processed ?: 0) + " items", color = Quiet, fontSize = 11.sp)
    }
}

@Composable
fun ExperienceChoiceScreen(counts: CatalogCounts, onMovies: () -> Unit, onLive: () -> Unit, onSettings: () -> Unit) {
    val moviesFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { moviesFocus.requestFocus() }
    DesignedScreen {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(17.dp)) {
            DesignTopBar(onSettings = onSettings)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column { Text("What would you like to watch?", color = Bright, fontSize = 30.sp); Text("Choose an experience. You can switch anytime from the home screen.", color = Quiet, fontSize = 13.sp) }
                Text("●  LIBRARY READY", color = Success, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }
            Row(Modifier.fillMaxSize().padding(bottom = 20.dp).focusGroup(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ExperienceCard("◉", "${formatCount(counts.titles)} TITLES", "Movies & TV Shows", "Browse your library, favorites and continue watching with enriched artwork and details.", "Open movies & shows", onMovies, Modifier.weight(1f), moviesFocus, Peach, primary = true)
                ExperienceCard("▦", "${formatCount(counts.liveChannels)} CHANNELS", "Live TV Channels", "Jump into live channels, browse categories and open the electronic program guide.", "Open live TV", onLive, Modifier.weight(1f), accent = ChoiceBlue)
            }
        }
    }
}

@Composable
private fun ExperienceCard(icon: String, count: String, title: String, description: String, action: String, onClick: () -> Unit, modifier: Modifier, focusRequester: FocusRequester? = null, accent: Color, primary: Boolean = false) {
    FocusSurface(onClick, modifier, Panel, Color(0xFF1B2638), focusRequester = focusRequester, borderColor = if (primary) Peach else null) {
        Column(Modifier.fillMaxSize().padding(26.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { Text(icon, color = accent, fontSize = 27.sp) }
                Text("●  $count", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Text(title, color = Bright, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = Quiet, fontSize = 13.sp, lineHeight = 19.sp)
            Box(
                Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(15.dp))
                    .background(if (primary) Brush.horizontalGradient(listOf(Peach, Color(0xFFFF936C))) else SolidColor(Color(0xFF1C2635)))
                    .border(if (primary) 2.dp else 1.dp, if (primary) Color(0xFFFFF1D8) else Color(0xFF43526B), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(action, color = if (primary) Color(0xFF25150D) else Color(0xFFD8E1F0), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Text("→", color = if (primary) Color(0xFF25150D) else Color(0xFF9DB2D1), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun pairingStatusText(phase: PairingPhase) = when (phase) {
    PairingPhase.Creating -> "Creating secure session…"
    PairingPhase.Waiting -> "Waiting for your phone"
    PairingPhase.Received -> "Details received"
    PairingPhase.Unavailable -> "Local network unavailable"
    PairingPhase.Expired -> "Code expired"
    PairingPhase.Failed -> "Connection failed"
    else -> "Ready to pair"
}

private fun formatRemaining(milliseconds: Long): String {
    val seconds = milliseconds / 1_000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

private fun formatCount(value: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(value)
