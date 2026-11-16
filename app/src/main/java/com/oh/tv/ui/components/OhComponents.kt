package com.oh.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.oh.tv.ui.theme.FocusBlue
import com.oh.tv.ui.theme.Graphite
import com.oh.tv.ui.theme.Muted
import com.oh.tv.ui.theme.Surface
import com.oh.tv.ui.theme.WarmWhite

@Composable
fun Screen(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val horizontal = 26.dp
    val vertical = 16.dp
    Box(modifier.fillMaxSize().background(Graphite).padding(horizontal = horizontal, vertical = vertical)) { content() }
}

@Composable
fun OhTopBar(title: String? = null, onHome: (() -> Unit)? = null, onSettings: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onHome != null) OhButton("⌂", onHome, compact = true)
            Text(title ?: "OH", color = WarmWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        if (onSettings != null) OhButton("⚙  Settings", onSettings)
    }
}

@Composable
fun OhButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, compact: Boolean = false) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier,
        background = if (primary) FocusBlue else Surface,
        focusedBackground = if (primary) FocusBlue else Color(0xFF303640),
        contentPadding = if (compact) 7.dp else 10.dp,
    ) {
        Text(label, color = WarmWhite, fontSize = if (compact) 14.sp else 13.sp, maxLines = 1)
    }
}

@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Surface,
    focusedBackground: Color = Surface,
    contentPadding: androidx.compose.ui.unit.Dp = 0.dp,
    focusRequester: FocusRequester? = null,
    borderColor: Color? = null,
    borderWidth: androidx.compose.ui.unit.Dp = 3.dp,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.035f else 1f, label = "focusScale")
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (focused) focusedBackground else background)
            .then(
                when {
                    focused -> Modifier.border(BorderStroke(3.dp, WarmWhite), shape)
                    borderColor != null -> Modifier.border(BorderStroke(borderWidth, borderColor), shape)
                    else -> Modifier
                },
            )
            .onFocusChanged { focused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .focusable()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun StatusPanel(title: String, message: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = WarmWhite, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(message, color = Muted, fontSize = 14.sp)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(22.dp))
            OhButton(action, onAction, primary = true)
        }
    }
}

@Composable
fun MetadataLine(text: String) {
    Text(text, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
