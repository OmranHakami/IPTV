package com.oh.tv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.oh.tv.data.CatalogCounts
import com.oh.tv.data.SyncProgress
import com.oh.tv.data.SyncScopes
import com.oh.tv.data.SyncState
import com.oh.tv.data.remote.AccountInput
import com.oh.tv.R
import com.oh.tv.ui.components.FocusSurface
import com.oh.tv.ui.theme.Manrope
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

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
fun PhonePairingScreen(state: PairingUiState, onRetry: () -> Unit, onManual: () -> Unit) {
    var remainingMs by remember(state.expiresAtEpochMs) { mutableLongStateOf((state.expiresAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0)) }
    LaunchedEffect(state.expiresAtEpochMs, state.phase) {
        while (state.phase == PairingPhase.Waiting && remainingMs > 0) {
            remainingMs = (state.expiresAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0)
            delay(1_000)
        }
    }
    var copied by remember(state.linkUrl) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }
    var checked by remember(state.phase, state.code) { mutableStateOf(false) }
    val context = LocalContext.current
    val link = state.linkUrl.substringBefore('?')
    val expired = state.phase == PairingPhase.Expired || (state.phase == PairingPhase.Waiting && state.expiresAtEpochMs > 0 && remainingMs == 0L)
    val expiring = state.phase == PairingPhase.Waiting && remainingMs in 1..60_000
    val retry = expired || state.phase in setOf(PairingPhase.Failed, PairingPhase.Unavailable, PairingPhase.Idle)
    val loading = state.phase in setOf(PairingPhase.Creating, PairingPhase.Received)
    val statusPulseTransition = rememberInfiniteTransition(label = "pairingStatusPulse")
    val statusPulse by statusPulseTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pairingStatusPulse",
    )
    val primaryFocus = remember { FocusRequester() }
    val manualFocus = remember { FocusRequester() }
    LaunchedEffect(state.phase) { if (loading) manualFocus.requestFocus() else primaryFocus.requestFocus() }
    BoxWithConstraints(Modifier.fillMaxSize().background(PairingBackground)) {
        val density = LocalDensity.current
        val scale = minOf(maxWidth.value / 1440f, maxHeight.value / 810f)
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            Column(Modifier.align(Alignment.Center).width(1440.dp).height(810.dp).padding(top = 36.dp, start = 64.dp, end = 64.dp)) {
                Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(Peach, Coral))), contentAlignment = Alignment.Center) {
                            Image(painterResource(R.drawable.pairing_brand), contentDescription = null, modifier = Modifier.size(40.dp))
                        }
                        Text("CHROMA", color = Bright, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
                    }
                    Box(Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(PairingSurface).border(1.dp, PairingStroke, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text("Phone pairing", color = Color(0xFFC6CFDE), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(48.dp))
                Row(Modifier.fillMaxWidth().height(580.dp).padding(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier.width(824.dp).height(560.dp).clip(RoundedCornerShape(20.dp)).background(PairingSurface)
                            .border(1.dp, PairingStroke, RoundedCornerShape(20.dp)).padding(horizontal = 40.dp, vertical = 36.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("CONNECT WITH YOUR PHONE", color = Peach, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                            Text("Finish setup on your phone", color = Bright, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp)
                            Text("Open the link on any phone or tablet, then enter the code shown below.", color = Quiet, fontSize = 15.sp, lineHeight = 21.75.sp)
                        }
                        Row(Modifier.fillMaxWidth().height(154.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            var linkFocused by remember { mutableStateOf(false) }
                            val linkShape = RoundedCornerShape(14.dp)
                            Column(
                                Modifier.weight(1f).fillMaxHeight().clip(linkShape).background(PairingInset)
                                    .border(if (linkFocused) 2.dp else 1.dp, if (linkFocused) Peach else PairingStroke, linkShape)
                                    .onFocusChanged { linkFocused = it.isFocused }
                                    .clickable(enabled = link.isNotBlank(), onClickLabel = "Copy pairing link") {
                                        context.getSystemService(android.content.ClipboardManager::class.java)
                                            .setPrimaryClip(android.content.ClipData.newPlainText("Pairing link", link))
                                        copied = true
                                    }.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                            ) {
                                Text("VISIT", color = Quiet, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
                                Text(link.ifBlank { "Preparing link…" }, color = Bright, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                                AnimatedVisibility(copied, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
                                    Text("Copied to clipboard", color = Success, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            val codeShape = RoundedCornerShape(14.dp)
                            Column(
                                Modifier.width(300.dp).fillMaxHeight().clip(codeShape).background(PairingInset)
                                    .border(if (expired || expiring) 2.dp else 1.dp, when { expired -> Coral; expiring -> Peach; else -> PairingStroke }, codeShape).padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                            ) {
                                Text("ONE-TIME CODE", color = if (expired) Coral else Quiet, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
                                Text(if (expired) "— — —" else state.code.ifBlank { "— — —" }.replace("-", " — "), color = if (expired) Quiet else Bright, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                                if (expired || expiring) Text(if (expired) "Generate a new code" else "Expires in ${formatRemaining(remainingMs)}", color = if (expired) Coral else Peach, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("1  Use any phone or tablet", color = Color(0xFFCBD3E3), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(when { expired -> "2  Code expired"; remainingMs > 0 -> "2  Code expires in ${formatRemaining(remainingMs)}"; loading -> "2  ${if (state.phase == PairingPhase.Creating) "Creating a secure code…" else "Provider details received"}"; else -> "2  Create a new code to continue" }, color = if (expired) Coral else Color(0xFFCBD3E3), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("3  This TV connects automatically", color = Color(0xFFCBD3E3), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        PairingAction(
                            label = when { retry -> "Create a new code"; state.phase == PairingPhase.Creating -> "Creating code…"; state.phase == PairingPhase.Received -> "Connecting…"; else -> "Refresh status" },
                            onClick = { if (retry) onRetry() else { remainingMs = (state.expiresAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0); checked = true } },
                            modifier = Modifier.fillMaxWidth().height(56.dp), focusRequester = primaryFocus, loading = loading, primary = true,
                        )
                    }
                    Column(Modifier.weight(1f).padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PairingIcon(
                                if (loading) "loading" else if (retry) "alert" else "phone",
                                if (loading) Peach else if (retry) Coral else PairingAccent,
                                Modifier.size(28.dp).graphicsLayer {
                                    val scale = if (loading || state.phase == PairingPhase.Waiting) statusPulse else 1f
                                    scaleX = scale
                                    scaleY = scale
                                },
                            )
                            Text(if (expired) "Code expired" else pairingStatusText(state.phase), color = Bright, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(state.message ?: when {
                                expired -> "Generate a new code to finish connecting this TV."
                                state.phase == PairingPhase.Creating -> "Creating a secure session on your local network."
                                state.phase == PairingPhase.Received -> "Verifying your provider and connecting this TV now."
                                retry -> "We couldn’t verify the provider. Try the connection again."
                                checked -> "Status refreshed. Still waiting for your phone."
                                else -> "We’ll update this screen as soon as your provider is confirmed."
                            }, color = Quiet, fontSize = 14.sp, lineHeight = 21.sp)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(PairingStroke))
                            Spacer(Modifier.height(3.dp))
                            Text("Can’t use a phone?", color = Bright, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Enter your provider details with the remote instead.", color = Quiet, fontSize = 13.sp, lineHeight = 18.85.sp)
                            PairingAction("Use manual setup", onManual, Modifier.height(42.dp), manualFocus)
                        }
                    }
                }
            }
        }
    }
}

private val PairingBackground = Color(0xFF090B10)
private val PairingSurface = Color(0xFF121722)
private val PairingInset = Color(0xFF181F2C)
private val PairingStroke = Color(0xFF283247)
private val PairingAccent = Color(0xFF7C9CFF)

@Composable
private fun PairingAction(label: String, onClick: () -> Unit, modifier: Modifier, focusRequester: FocusRequester, loading: Boolean = false, primary: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val focusScale by animateFloatAsState(if (focused) 1.015f else 1f, tween(140), label = "pairingActionFocus")
    val shape = RoundedCornerShape(if (primary) 12.dp else 10.dp)
    Row(
        modifier.graphicsLayer { scaleX = focusScale; scaleY = focusScale }
            .clip(shape).background(if (primary && focused && !loading) Peach else PairingInset)
            .border(if (!primary && focused) 2.dp else 1.dp, when { loading -> PairingAccent; focused -> Peach; else -> PairingStroke }, shape)
            .onFocusChanged { focused = it.isFocused }.focusRequester(focusRequester)
            .clickable(enabled = !loading, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(if (primary) 10.dp else 8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (primary) PairingIcon(if (loading) "loading" else "refresh", if (loading) PairingAccent else if (focused) PairingBackground else Peach, Modifier.size(18.dp))
        Text(label, color = if (primary && focused && !loading) PairingBackground else Bright, fontSize = if (primary) 15.sp else 13.sp, fontWeight = if (primary) FontWeight.ExtraBold else FontWeight.Bold)
        if (!primary) PairingIcon("arrow", if (focused) Peach else Quiet, Modifier.size(15.dp))
    }
}

@Composable
fun PairingIcon(name: String, color: Color, modifier: Modifier) {
    val rotation = if (name == "loading") {
        val transition = rememberInfiniteTransition(label = "loadingIcon")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
            label = "loadingIconRotation",
        )
        angle
    } else 0f
    val vector = remember(name) {
        val path = when (name) {
            "phone" -> "M7,2 L17,2 Q19,2 19,4 L19,20 Q19,22 17,22 L7,22 Q5,22 5,20 L5,4 Q5,2 7,2 Z M12,18 L12.01,18"
            "arrow" -> "M5,12 L19,12 M12,5 L19,12 L12,19"
            "back" -> "M19,12 L5,12 M12,19 L5,12 L12,5"
            "check" -> "M20,6 L9,17 L4,12"
            "move" -> "M12,19 L12,5 M6,11 L12,5 L18,11"
            "tv" -> "M4,5 L20,5 L20,17 L4,17 Z M8,21 L16,21"
            "link" -> "M10,13 A5,5 0,0 0,17,13 L20,10 A5,5 0,0 0,13,3 L11,5 M14,11 A5,5 0,0 1,7,11 L4,14 A5,5 0,0 0,11,21 L13,19"
            "user" -> "M20,21 A8,8 0,0 0,4,21 M12,11 A4,4 0,1 0,12,3 A4,4 0,1 0,12,11"
            "lock" -> "M5,11 L19,11 L19,21 L5,21 Z M8,11 L8,7 A4,4 0,0 1,16,7 L16,11"
            "login" -> "M15,3 L21,3 L21,21 L15,21 M10,17 L15,12 L10,7 M15,12 L3,12"
            "play" -> "M8,5 L19,12 L8,19 Z"
            "clock" -> "M12,2 A10,10 0,1 1,2,12 A10,10 0,1 1,12,2 M12,6 L12,12 L16,14"
            "shield" -> "M12,3 L20,6 L20,12 C20,17 16,20 12,22 C8,20 4,17 4,12 L4,6 Z M8,12 L11,15 L16,9"
            "dot" -> "M12,8 A4,4 0,1 1,8,12 A4,4 0,1 1,12,8 M12,2 A10,10 0,1 1,2,12 A10,10 0,1 1,12,2"
            "sparkles" -> "M12,3 L13.5,8.5 L19,10 L13.5,11.5 L12,17 L10.5,11.5 L5,10 L10.5,8.5 Z M19,16 L19.7,18.3 L22,19 L19.7,19.7 L19,22 L18.3,19.7 L16,19 L18.3,18.3 Z"
            "party" -> "M4,20 L9,7 L17,15 Z M14,4 L14,7 M19,7 L17,9 M20,13 L17,13"
            "settings" -> "M12,15.5 A3.5,3.5 0,1 1,12,8.5 A3.5,3.5 0,1 1,12,15.5 M19.4,15 A1.7,1.7 0,0 0,19.7,16.9 L19.8,17.1 L17.1,19.8 L16.9,19.7 A1.7,1.7 0,0 0,15,19.4 A1.7,1.7 0,0 0,14,21 L14,21.3 L10,21.3 L10,21 A1.7,1.7 0,0 0,9,19.4 A1.7,1.7 0,0 0,7.1,19.7 L6.9,19.8 L4.2,17.1 L4.3,16.9 A1.7,1.7 0,0 0,4.6,15 A1.7,1.7 0,0 0,3,14 L2.7,14 L2.7,10 L3,10 A1.7,1.7 0,0 0,4.6,9 A1.7,1.7 0,0 0,4.3,7.1 L4.2,6.9 L6.9,4.2 L7.1,4.3 A1.7,1.7 0,0 0,9,4.6 A1.7,1.7 0,0 0,10,3 L10,2.7 L14,2.7 L14,3 A1.7,1.7 0,0 0,15,4.6 A1.7,1.7 0,0 0,16.9,4.3 L17.1,4.2 L19.8,6.9 L19.7,7.1 A1.7,1.7 0,0 0,19.4,9 A1.7,1.7 0,0 0,21,10 L21.3,10 L21.3,14 L21,14 A1.7,1.7 0,0 0,19.4,15"
            "movie" -> "M3,6 L21,6 L21,20 L3,20 Z M3,10 L21,10 M6,6 L9,2 M12,6 L15,2 M18,6 L21,2"
            "shows" -> "M4,5 L20,5 L20,19 L4,19 Z M9,9 L15,12 L9,15 Z"
            "radio" -> "M12,14 A2,2 0,1 1,12,10 A2,2 0,1 1,12,14 M8.5,7.5 A6,6 0,0 0,8.5,16.5 M15.5,7.5 A6,6 0,0 1,15.5,16.5 M5.5,4.5 A10,10 0,0 0,5.5,19.5 M18.5,4.5 A10,10 0,0 1,18.5,19.5"
            "remote" -> "M9,3 L15,3 Q17,3 17,5 L17,19 Q17,21 15,21 L9,21 Q7,21 7,19 L7,5 Q7,3 9,3 Z M12,7 L12.01,7 M10,11 L14,11 M12,9 L12,13"
            "select" -> "M12,8 A4,4 0,1 1,8,12 A4,4 0,1 1,12,8 M12,2 A10,10 0,1 1,2,12 A10,10 0,1 1,12,2"
            "horizontal" -> "M5,12 L19,12 M9,8 L5,12 L9,16 M15,8 L19,12 L15,16"
            "search" -> "M19,11 A8,8 0,1 1,3,11 A8,8 0,1 1,19,11 M16.65,16.65 L21,21"
            "grid" -> "M4,4 L10,4 L10,10 L4,10 Z M14,4 L20,4 L20,10 L14,10 Z M4,14 L10,14 L10,20 L4,20 Z M14,14 L20,14 L20,20 L14,20 Z"
            "sort" -> "M4,7 L16,7 M4,12 L13,12 M4,17 L10,17 M18,5 L18,19 M15,16 L18,19 L21,16"
            "chevron" -> "M6,9 L12,15 L18,9"
            "bookmark" -> "M6,3 L18,3 L18,21 L12,17 L6,21 Z"
            "bookmark-check" -> "M6,3 L18,3 L18,21 L12,17 L6,21 Z M8.5,10.5 L11,13 L15.5,8.5"
            "star" -> "M12,3 L14.8,8.7 L21,9.6 L16.5,14 L17.6,20.3 L12,17.3 L6.4,20.3 L7.5,14 L3,9.6 L9.2,8.7 Z"
            "info" -> "M12,2 A10,10 0,1 1,2,12 A10,10 0,1 1,12,2 M12,11 L12,17 M12,7 L12.01,7"
            "plus" -> "M12,5 L12,19 M5,12 L19,12"
            "volume" -> "M4,10 L8,10 L13,6 L13,18 L8,14 L4,14 Z M16,9 A4,4 0,0 1,16,15 M18.5,6.5 A8,8 0,0 1,18.5,17.5"
            "alert" -> "M22,12 A10,10 0,1 1,2,12 A10,10 0,1 1,22,12 M12,8 L12,12 M12,16 L12.01,16"
            "loading" -> "M12,2 A10,10 0,1 1,2,12"
            else -> "M3,12 A9,9 0,0 1,18.36,5.64 L21,8 M21,3 L21,8 L16,8 M21,12 A9,9 0,0 1,5.64,18.36 L3,16 M3,21 L3,16 L8,16"
        }
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(PathParser().parsePathString(path).toNodes(), stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
    }
    Image(vector, contentDescription = null, modifier = modifier.graphicsLayer { rotationZ = rotation }, colorFilter = ColorFilter.tint(color))
}

@Composable
fun ManualSetupScreen(busy: Boolean, message: String?, onConnect: (AccountInput) -> Unit, onBack: () -> Unit) {
    var accountName by remember { mutableStateOf("Living room") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val accountFocus = remember { FocusRequester() }
    val serverFocus = remember { FocusRequester() }
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { serverFocus.requestFocus() }
    val validServer = server.startsWith("http://") || server.startsWith("https://")
    val canSubmit = accountName.isNotBlank() && validServer && username.isNotBlank() && password.isNotBlank() && !busy
    BoxWithConstraints(Modifier.fillMaxSize().background(PairingBackground)) {
        val density = LocalDensity.current
        val scale = minOf(maxWidth.value / 1440f, maxHeight.value / 810f)
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            Box(Modifier.align(Alignment.Center).width(1440.dp).height(810.dp)) {
                Row(
                    Modifier.offset(64.dp, 30.dp).width(1312.dp).height(64.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        ManualAction(onBack, Modifier.size(44.dp), enabled = !busy, icon = "back")
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.pairing_brand), contentDescription = null, modifier = Modifier.size(40.dp))
                            Text("CHROMA", color = Bright, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.8.sp)
                        }
                    }
                    Box(
                        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(PairingSurface)
                            .border(1.dp, PairingStroke, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Manual setup", color = Color(0xFFC6CFDE), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                }
                Row(
                    Modifier.offset(80.dp, 122.dp).width(1280.dp).height(620.dp),
                    horizontalArrangement = Arrangement.spacedBy(64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("MANUAL SETUP  ·  02", color = Peach, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                        Text("Connect your TV provider", color = Bright, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, lineHeight = 43.sp)
                        Text("Enter the credentials supplied by your provider. You can review everything before connecting.", color = Quiet, fontSize = 16.sp, lineHeight = 24.8.sp)
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PairingSurface)
                                .border(1.dp, PairingStroke, RoundedCornerShape(14.dp)).padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(13.dp),
                        ) {
                            Text("BEFORE YOU START", color = Bright, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp)
                            ManualRequirement("Server URL")
                            ManualRequirement("Username")
                            ManualRequirement("Password")
                        }
                        Row(
                            Modifier.fillMaxWidth().height(68.dp).clip(RoundedCornerShape(12.dp)).background(PairingSurface).padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(PairingInset), contentAlignment = Alignment.Center) {
                                PairingIcon("move", Bright, Modifier.size(16.dp))
                            }
                            Text("Use the remote to move between fields", color = Quiet, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Column(
                        Modifier.width(856.dp).height(600.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF101622))
                            .border(1.dp, Color(0xFF303B50), RoundedCornerShape(22.dp)).padding(horizontal = 34.dp, vertical = 30.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("Provider credentials", color = Bright, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                            Text("Use the remote to select a field and open the on-screen keyboard.", color = Quiet, fontSize = 13.sp)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ManualField(accountName, { accountName = it }, "ACCOUNT NAME", "Living room", "tv", Modifier.width(788.dp), enabled = !busy, focusRequester = accountFocus, nextFocusRequester = serverFocus)
                            ManualField(server, { server = it }, "SERVER URL", "https://stream.provider.com", "link", Modifier.width(788.dp), enabled = !busy, error = server.isNotBlank() && !validServer, focusRequester = serverFocus, nextFocusRequester = usernameFocus)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ManualField(username, { username = it }, "USERNAME", "Provider username", "user", Modifier.width(388.dp), enabled = !busy, focusRequester = usernameFocus, nextFocusRequester = passwordFocus)
                                ManualField(password, { password = it }, "PASSWORD", "Provider password", "lock", Modifier.width(388.dp), password = true, enabled = !busy, focusRequester = passwordFocus)
                            }
                        }
                        message?.let { Text(it, color = Coral, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        Row(
                            Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF101A22))
                                .border(1.dp, Color(0xFF233745), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PairingIcon("shield", Success, Modifier.size(19.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Stored securely on this TV", color = Bright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Credentials are encrypted and never shown after setup.", color = Quiet, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End)) {
                            ManualAction(onBack, Modifier.width(130.dp).height(50.dp), enabled = !busy, label = "Cancel")
                            ManualAction(
                                onClick = { onConnect(AccountInput(accountName, server, username, password)) },
                                modifier = Modifier.width(220.dp).height(50.dp),
                                enabled = canSubmit,
                                primary = true,
                                loading = busy,
                                label = if (busy) "Connecting…" else "Log in",
                                icon = if (busy) "loading" else "login",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualRequirement(label: String) {
    Row(Modifier.height(24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        PairingIcon("check", Success, Modifier.size(15.dp))
        Text(label, color = Color(0xFFCBD3E3), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun ManualField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    password: Boolean = false,
    enabled: Boolean = true,
    error: Boolean = false,
    focusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val inputFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(value) {
        if (value != fieldValue.text) fieldValue = fieldValue.copy(text = value, selection = TextRange(value.length))
    }
    LaunchedEffect(editing) { if (editing) inputFocus.requestFocus() }
    val shape = RoundedCornerShape(12.dp)
    val accentColor by animateColorAsState(when { error -> Coral; focused -> Peach; else -> Quiet }, tween(140), label = "manualFieldAccent")
    val strokeColor by animateColorAsState(when { error -> Coral; focused -> Peach; else -> PairingStroke }, tween(140), label = "manualFieldStroke")
    val strokeWidth by animateDpAsState(if (focused && !error) 2.dp else 1.dp, tween(140), label = "manualFieldStrokeWidth")
    Row(
        modifier.height(76.dp).alpha(if (enabled) 1f else 0.55f).clip(shape)
            .background(if (enabled) PairingInset else Color(0xFF111620))
            .border(strokeWidth, strokeColor, shape)
            .onFocusChanged {
                focused = it.hasFocus
                if (!it.hasFocus) editing = false
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { editing = true }
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PairingIcon(icon, accentColor, Modifier.size(19.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, color = accentColor, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            BasicTextField(
                value = fieldValue,
                onValueChange = {
                    fieldValue = it
                    if (it.text != value) onValueChange(it.text)
                },
                enabled = enabled && editing,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = if (enabled) Bright else Quiet, fontFamily = Manrope, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(Peach),
                visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
                    imeAction = if (nextFocusRequester == null) ImeAction.Done else ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = {
                        editing = false
                        nextFocusRequester?.requestFocus()
                        keyboard?.hide()
                    },
                    onDone = {
                        editing = false
                        (nextFocusRequester ?: focusRequester)?.requestFocus()
                        keyboard?.hide()
                    },
                ),
                decorationBox = { input ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (fieldValue.text.isBlank()) {
                            Text(placeholder, color = Quiet, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        // Compose the editable inner field even when the placeholder is visible.
                        input()
                    }
                },
                modifier = Modifier.fillMaxWidth().tvTextFieldNavigation(focusManager, fieldValue)
                    .focusRequester(inputFocus),
            )
        }
        AnimatedVisibility(error, enter = fadeIn(tween(140)), exit = fadeOut(tween(140))) {
            PairingIcon("alert", Coral, Modifier.size(18.dp))
        }
        AnimatedVisibility(focused && !error, enter = fadeIn(tween(140)), exit = fadeOut(tween(140))) {
            Box(Modifier.height(24.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF2A2119)).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Text(if (editing) if (nextFocusRequester == null) "DONE" else "NEXT" else "OK", color = Peach, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun ManualAction(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    label: String? = null,
    icon: String? = null,
    primary: Boolean = false,
    loading: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val active = enabled || loading
    val shape = RoundedCornerShape(if (primary) 11.dp else 12.dp)
    val background by animateColorAsState(when { primary && active -> Peach; primary -> Color(0xFF2B3039); focused -> PairingInset; else -> Color.Transparent }, tween(140), label = "manualActionBackground")
    val borderColor by animateColorAsState(if (focused) Peach else if (primary) background else PairingStroke, tween(140), label = "manualActionBorder")
    val borderWidth by animateDpAsState(if (focused) 2.dp else 1.dp, tween(140), label = "manualActionBorderWidth")
    val focusScale by animateFloatAsState(if (focused && active) 1.02f else 1f, tween(140), label = "manualActionFocus")
    Row(
        modifier.graphicsLayer { scaleX = focusScale; scaleY = focusScale }.alpha(if (active) 1f else 0.55f)
            .clip(shape).background(background)
            .border(borderWidth, borderColor, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled && !loading, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) PairingIcon(icon, if (primary && active) PairingBackground else Quiet, Modifier.size(if (label == null) 20.dp else 17.dp))
        if (label != null) Text(label, color = if (primary && active) PairingBackground else Quiet, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun LibraryPreparationScreen(state: OhUiState, onOpenReady: () -> Unit, onRetry: () -> Unit) {
    val openFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val usable = state.syncProgress.any { it.state in setOf(SyncState.Ready, SyncState.Stale) }
    val failed = state.syncProgress.any { it.state == SyncState.Failed }
    val progressWeight = SyncScopes.userVisible.sumOf { scope ->
        when (state.syncProgress.firstOrNull { it.scope == scope }?.state) {
            SyncState.Ready, SyncState.Empty, SyncState.Stale -> 1.0
            SyncState.Running -> 0.5
            else -> 0.0
        }
    }
    val percent = ((progressWeight / SyncScopes.userVisible.size) * 100).toInt().coerceIn(0, 100)
    val progressColor = when { failed && !state.busy -> Coral; percent == 100 -> Success; else -> Peach }
    val animatedPercent by animateFloatAsState(percent.toFloat(), tween(500), label = "libraryProgress")
    val animatedProgressColor by animateColorAsState(progressColor, tween(300), label = "libraryProgressColor")
    val prepared = state.syncProgress.sumOf { it.processed }
    val readyProgress = state.syncProgress.filter { it.state in setOf(SyncState.Ready, SyncState.Stale, SyncState.Empty) }
    val readyItems = readyProgress.sumOf { it.processed }
    val readySections = readyProgress.map { it.scope }
    LaunchedEffect(usable, state.busy) { if (usable || (!state.busy && failed)) openFocus.requestFocus() }
    BoxWithConstraints(Modifier.fillMaxSize().background(PairingBackground)) {
        val density = LocalDensity.current
        val scale = minOf(maxWidth.value / 1440f, maxHeight.value / 810f)
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            Box(Modifier.align(Alignment.Center).width(1440.dp).height(810.dp)) {
                Row(
                    Modifier.offset(64.dp, 36.dp).width(1312.dp).height(58.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.pairing_brand), contentDescription = null, modifier = Modifier.size(40.dp))
                        Text("CHROMA", color = Bright, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(99.dp)).background(PairingSurface)
                            .border(1.dp, PairingStroke, RoundedCornerShape(99.dp)).padding(horizontal = 13.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PairingIcon("shield", Success, Modifier.size(15.dp))
                        Text("Connected", color = Success, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(
                    Modifier.offset(170.dp, 132.dp).width(1100.dp).height(610.dp),
                    horizontalArrangement = Arrangement.spacedBy(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.width(500.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Row(
                            Modifier.clip(RoundedCornerShape(99.dp)).background(Peach.copy(alpha = 0.09f)).padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PairingIcon("sparkles", Peach, Modifier.size(15.dp))
                            Text("YOUR LIBRARY IS ALMOST READY", color = Peach, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Great stories are\ncoming into focus.", color = Bright, fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.4).sp, lineHeight = 49.7.sp)
                            Text("Start watching completed sections now. We’ll keep organizing the rest quietly in the background.", color = Quiet, fontSize = 15.sp, lineHeight = 23.25.sp)
                        }
                        Column(
                            Modifier.width(500.dp).clip(RoundedCornerShape(16.dp)).background(PairingSurface)
                                .border(1.dp, PairingStroke, RoundedCornerShape(16.dp)).padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Overall library", color = Color(0xFFCBD3E3), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(if (failed && !state.busy) "Paused" else "${animatedPercent.toInt()}%", color = animatedProgressColor, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(PairingInset)) {
                                Box(Modifier.fillMaxWidth(animatedPercent / 100f).fillMaxHeight().background(animatedProgressColor))
                            }
                        }
                        LibraryPrimaryAction(
                            onClick = if (usable) onOpenReady else onRetry,
                            enabled = usable || (!state.busy && failed),
                            loading = state.busy && !usable,
                            label = when {
                                usable -> "Start watching ready content"
                                !state.busy && failed -> "Try synchronization again"
                                state.busy -> "Organizing your library…"
                                else -> "Available when content is ready"
                            },
                            focusRequester = openFocus,
                        )
                        if (usable && failed && !state.busy) {
                            LibraryPrimaryAction(
                                onClick = onRetry,
                                enabled = true,
                                loading = false,
                                label = "Retry failed sections",
                                focusRequester = retryFocus,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PairingIcon("dot", Quiet, Modifier.size(14.dp))
                            Text(if (usable) "Press OK to open ready content" else "Ready sections will unlock automatically", color = Quiet, fontSize = 12.sp)
                        }
                    }
                    Column(
                        Modifier.width(544.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xCC101622))
                            .border(1.dp, PairingStroke, RoundedCornerShape(22.dp)).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Library activity", color = Bright, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("${formatCount(prepared)} items prepared", color = Quiet, fontSize = 12.sp)
                            }
                            Row(
                                Modifier.clip(RoundedCornerShape(99.dp)).background(PairingInset).padding(horizontal = 11.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PairingIcon("clock", if (state.busy) Peach else progressColor, Modifier.size(14.dp))
                                Text(if (state.busy) "In progress" else if (percent == 100) "Ready" else "Paused", color = if (state.busy) Peach else progressColor, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        LibraryStatusRow("Movies", state.syncProgress.firstOrNull { it.scope == SyncScopes.MOVIES })
                        LibraryStatusRow("TV Shows", state.syncProgress.firstOrNull { it.scope == SyncScopes.TV_SHOWS })
                        LibraryStatusRow("Live TV", state.syncProgress.firstOrNull { it.scope == SyncScopes.LIVE_TV })
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Success.copy(alpha = 0.05f))
                                .border(1.dp, Success.copy(alpha = 0.2f), RoundedCornerShape(14.dp)).padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Success.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                PairingIcon("party", Success, Modifier.size(20.dp))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${formatCount(readyItems)} items are ready", color = Bright, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    state.message ?: if (readySections.isNotEmpty()) "${readySections.joinToString()} can be opened now" else "Content will appear here as sections finish",
                                    color = if (state.message == null) Quiet else Coral,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryStatusRow(label: String, progress: SyncProgress?) {
    val stateText = when (progress?.state) {
        SyncState.Running -> "Syncing"
        SyncState.Ready, SyncState.Stale, SyncState.Empty -> "Ready"
        SyncState.Failed -> "Retry"
        else -> "Queued"
    }
    val accent = when (progress?.state) {
        SyncState.Running -> Peach
        SyncState.Ready, SyncState.Stale, SyncState.Empty -> Success
        SyncState.Failed -> Coral
        else -> Quiet
    }
    val animatedAccent by animateColorAsState(accent, tween(240), label = "libraryStatusAccent")
    val icon = when (progress?.state) {
        SyncState.Running -> "loading"
        SyncState.Ready, SyncState.Stale, SyncState.Empty -> "check"
        SyncState.Failed -> "alert"
        else -> "clock"
    }
    val metadata = when (progress?.state) {
        SyncState.Running -> "${formatCount(progress.processed)} items · ${if (label == "Movies") "indexing artwork" else "preparing"}"
        SyncState.Ready, SyncState.Stale -> "${formatCount(progress.processed)} items · available now"
        SyncState.Empty -> "No items found · complete"
        SyncState.Failed -> progress.failure ?: "Couldn’t sync provider metadata"
        else -> "Waiting for previous sections"
    }
    Row(
        Modifier.fillMaxWidth().height(74.dp).clip(RoundedCornerShape(14.dp)).background(PairingSurface)
            .border(1.dp, PairingStroke, RoundedCornerShape(14.dp)).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(animatedAccent.copy(alpha = 0.09f)), contentAlignment = Alignment.Center) {
            PairingIcon(icon, animatedAccent, Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = Bright, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(metadata, color = Quiet, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(
            Modifier.clip(RoundedCornerShape(99.dp)).background(animatedAccent.copy(alpha = 0.09f)).padding(horizontal = 11.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(animatedAccent))
            Text(stateText, color = animatedAccent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun LibraryPrimaryAction(onClick: () -> Unit, enabled: Boolean, loading: Boolean, label: String, focusRequester: FocusRequester) {
    var focused by remember { mutableStateOf(false) }
    val active = enabled && !loading
    val shape = RoundedCornerShape(13.dp)
    val background by animateColorAsState(if (active) Peach else PairingInset, tween(180), label = "libraryActionBackground")
    val borderColor by animateColorAsState(if (focused && active) Color(0xFFFFF3DE) else if (active) Peach else PairingStroke, tween(180), label = "libraryActionBorder")
    val borderWidth by animateDpAsState(if (focused && active) 3.dp else 1.dp, tween(180), label = "libraryActionBorderWidth")
    val focusScale by animateFloatAsState(if (focused && active) 1.015f else 1f, tween(180), label = "libraryActionFocus")
    Row(
        Modifier.width(500.dp).height(56.dp).graphicsLayer { scaleX = focusScale; scaleY = focusScale }
            .alpha(if (active || loading) 1f else 0.45f).clip(shape)
            .background(background)
            .border(borderWidth, borderColor, shape)
            .onFocusChanged { focused = it.isFocused }.focusRequester(focusRequester)
            .clickable(enabled = active, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PairingIcon(if (loading) "loading" else if (active) "play" else "lock", if (active) PairingBackground else Quiet, Modifier.size(18.dp))
        Text(label, color = if (active) PairingBackground else Quiet, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun ExperienceChoiceScreen(
    counts: CatalogCounts,
    onMovies: () -> Unit,
    onShows: () -> Unit,
    onLive: () -> Unit,
    onReload: () -> Unit,
    onSettings: () -> Unit,
) {
    val initialFocus = remember { FocusRequester() }
    var openingIndex by remember { mutableStateOf<Int?>(null) }
    val initialIndex = when {
        counts.movies > 0 -> 0
        counts.series > 0 -> 1
        counts.liveChannels > 0 -> 2
        else -> -1
    }
    LaunchedEffect(initialIndex) { initialFocus.requestFocus() }
    BoxWithConstraints(Modifier.fillMaxSize().background(PairingBackground)) {
        val density = LocalDensity.current
        val scale = minOf(maxWidth.value / 1440f, maxHeight.value / 810f)
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            Box(Modifier.align(Alignment.Center).width(1440.dp).height(810.dp)) {
                Row(
                    Modifier.offset(64.dp, 30.dp).width(1312.dp).height(48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.pairing_brand), contentDescription = null, modifier = Modifier.size(40.dp))
                        Text("CHROMA", color = Bright, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChoiceUtilityButton("Reload content", "refresh", onReload, focusRequester = if (initialIndex == -1) initialFocus else null)
                        ChoiceUtilityButton("Settings", "settings", onSettings)
                    }
                }
                Column(
                    Modifier.offset(64.dp, 104.dp).width(1312.dp).height(642.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(120.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                PairingIcon("remote", Peach, Modifier.size(18.dp))
                                Text("CHOOSE WITH YOUR REMOTE", color = Peach, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                            }
                            Text("What do you want to watch?", color = Bright, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().height(532.dp).focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        ChoiceExperienceCard(
                            icon = "movie", count = counts.movies, unit = "titles", title = "Movies",
                            description = "Blockbusters, favorites and everything you saved — all in one place.",
                            hint = "Press OK to open", action = "Open movies", accent = Peach,
                            opening = openingIndex == 0,
                            onClick = { openingIndex = 0; onMovies() }, modifier = Modifier.weight(1f), focusRequester = if (initialIndex == 0) initialFocus else null,
                        )
                        ChoiceExperienceCard(
                            icon = "shows", count = counts.series, unit = "titles", title = "TV Shows",
                            description = "Series, saved episodes and your continue-watching queue.",
                            hint = "Updated now", action = "Open TV shows", accent = Color(0xFF9C7CFF),
                            opening = openingIndex == 1,
                            onClick = { openingIndex = 1; onShows() }, modifier = Modifier.weight(1f), focusRequester = if (initialIndex == 1) initialFocus else null,
                        )
                        ChoiceExperienceCard(
                            icon = "radio", count = counts.liveChannels, unit = "channels", title = "Live TV",
                            description = "Live channels, sports and a fast, familiar program guide.",
                            hint = "Guide available", action = "Open live TV", accent = Color(0xFF7C9CFF),
                            opening = openingIndex == 2,
                            onClick = { openingIndex = 2; onLive() }, modifier = Modifier.weight(1f), focusRequester = if (initialIndex == 2) initialFocus else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceUtilityButton(label: String, icon: String, onClick: () -> Unit, focusRequester: FocusRequester? = null) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(11.dp)
    val background by animateColorAsState(if (focused) Color(0xFF202839) else Color.Transparent, tween(140), label = "choiceUtilityBackground")
    val borderColor by animateColorAsState(if (focused) PairingAccent else PairingStroke, tween(140), label = "choiceUtilityBorder")
    val borderWidth by animateDpAsState(if (focused) 2.dp else 1.dp, tween(140), label = "choiceUtilityBorderWidth")
    val focusScale by animateFloatAsState(if (focused) 1.02f else 1f, tween(140), label = "choiceUtilityFocus")
    Row(
        Modifier.height(44.dp).graphicsLayer { scaleX = focusScale; scaleY = focusScale }.clip(shape).background(background)
            .border(borderWidth, borderColor, shape)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PairingIcon(icon, if (focused) Bright else Color(0xFFAAB5C6), Modifier.size(18.dp))
        Text(label, color = if (focused) Bright else Color(0xFFC6CFDD), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ChoiceExperienceCard(
    icon: String,
    count: Int,
    unit: String,
    title: String,
    description: String,
    hint: String,
    action: String,
    accent: Color,
    opening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val available = count > 0
    val shape = RoundedCornerShape(18.dp)
    val cardBackground = if (focused && available) Brush.verticalGradient(listOf(Color(0xFF1D1B20), PairingSurface, PairingSurface)) else SolidColor(PairingSurface)
    val borderColor by animateColorAsState(if (focused && available) Peach else PairingStroke, tween(180), label = "choiceCardBorder")
    val borderWidth by animateDpAsState(if (focused && available) 3.dp else 1.dp, tween(180), label = "choiceCardBorderWidth")
    val focusScale by animateFloatAsState(if (focused && available) 1.012f else 1f, tween(180), label = "choiceCardFocus")
    val statusColor by animateColorAsState(when { opening || focused -> Peach; available -> Success; else -> Color(0xFF7E899D) }, tween(180), label = "choiceCardStatus")
    val statusText = when { opening -> "OPENING"; focused -> "FOCUSED"; available -> "READY"; else -> "UNAVAILABLE" }
    Column(
        modifier.fillMaxHeight().graphicsLayer { scaleX = focusScale; scaleY = focusScale }.alpha(if (available) 1f else 0.52f)
            .clip(shape).background(cardBackground)
            .border(borderWidth, borderColor, shape)
            .onFocusChanged { state -> focused = state.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(enabled = available && !opening, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(26.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(PairingInset)
                        .border(1.dp, if (focused && available) Peach.copy(alpha = 0.33f) else Color(0xFF334057), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) { PairingIcon(icon, accent, Modifier.size(24.dp)) }
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(statusColor.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(statusColor))
                    Text(statusText, color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(title, color = Bright, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
                Text(description, color = Quiet, fontSize = 14.sp, lineHeight = 20.3.sp)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${formatCount(count)} $unit", color = Quiet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(if (focused && available) "Press OK to open" else hint, color = if (focused && available) Peach else Color(0xFF667289), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp))
                    .background(if ((focused && available) || opening) Brush.horizontalGradient(listOf(if (opening) Color(0xFFE88954) else Peach, Color(0xFFFF936C))) else SolidColor(Color.Transparent))
                    .border(if (focused && available) 3.dp else 1.dp, if (focused && available) Color(0xFFFFF0D5) else PairingStroke, RoundedCornerShape(12.dp)),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (opening) "Opening…" else if (available) action else "Not available", color = if ((focused && available) || opening) Color(0xFF24130C) else Color(0xFFE5EBF5), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                PairingIcon(if (opening) "loading" else if (available) "arrow" else "lock", if ((focused && available) || opening) Color(0xFF24130C) else Color(0xFFE5EBF5), Modifier.size(18.dp))
            }
        }
    }
}

private fun pairingStatusText(phase: PairingPhase) = when (phase) {
    PairingPhase.Creating -> "Creating secure session…"
    PairingPhase.Waiting -> "Waiting for your phone"
    PairingPhase.Received -> "Connecting your account"
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
