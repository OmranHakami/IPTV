package com.oh.tv.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.oh.tv.R

private val LoadingBackground = Color(0xFF090B10)
private val LoadingText = Color(0xFFF7F8FC)
private val LoadingMuted = Color(0xFF98A3B7)
private val LoadingAccent = Color(0xFF7C9CFF)

@Composable
fun AppLoadingScreen() {
    val transition = rememberInfiniteTransition(label = "app loading")
    val sweepX by transition.animateFloat(
        initialValue = -132f,
        targetValue = 400f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
        label = "loading progress sweep",
    )
    BoxWithConstraints(Modifier.fillMaxSize().background(LoadingBackground)) {
        val density = LocalDensity.current
        val scale = minOf(maxWidth.value / 1440f, maxHeight.value / 810f)
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            Box(Modifier.align(Alignment.Center).width(1440.dp).height(810.dp).background(LoadingBackground)) {
                Column(
                    Modifier.align(Alignment.Center).width(500.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.pairing_brand), null, Modifier.size(52.dp))
                        Text("CHROMA", color = LoadingText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
                    }
                    Spacer(Modifier.height(46.dp))
                    Text("Opening your library", color = LoadingText, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(10.dp))
                    Text("Getting your movies, shows, and preferences ready.", color = LoadingMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(34.dp))
                    Box(Modifier.width(400.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF273146))) {
                        Box(
                            Modifier.offset(x = sweepX.dp).width(132.dp).height(7.dp)
                                .testTag("loading-progress-sweep")
                                .background(Brush.horizontalGradient(listOf(Color.Transparent, LoadingAccent, Color.Transparent))),
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        PairingIcon("loading", LoadingAccent, Modifier.size(20.dp).testTag("loading-spinner"))
                        Text("Loading your profile…", color = Color(0xFFC7D0E0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(22.dp))
                    Text("This usually takes only a few seconds.", color = Color(0xFF6F7B90), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 64.dp, vertical = 54.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Your library stays on this device", color = Color(0xFF667287), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF71E5B1)))
                        Text("Secure local startup", color = Color(0xFF667287), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
