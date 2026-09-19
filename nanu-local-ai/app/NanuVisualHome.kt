package com.example.llama

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

enum class NanuDestination { CHAT, TALK, FILES, CREATE, TAROT, SAFETY, PRO }

data class NanuHomeState(
    val onlineTools: Boolean = true,
    val modelName: String? = null,
    val freeStorageGb: String = "—",
    val savedChats: Int = 0,
    val imageModelReady: Boolean = false,
    val speechReady: Boolean = false,
    val proEnabled: Boolean = false,
    val reportReady: Boolean = false,
    val onboardingComplete: Boolean = false
)

private val NanuDark = darkColorScheme(
    primary = Color(0xFF40DDF4),
    onPrimary = Color(0xFF002A31),
    secondary = Color(0xFFB9A4FF),
    tertiary = Color(0xFF50E2A4),
    background = Color(0xFF061018),
    surface = Color(0xFF0E1A24),
    surfaceVariant = Color(0xFF142432),
    onBackground = Color(0xFFF3F8FC),
    onSurface = Color(0xFFF3F8FC),
    onSurfaceVariant = Color(0xFFACBECA),
    outline = Color(0xFF355365),
    error = Color(0xFFFF8491)
)

private val NanuLight = lightColorScheme(
    primary = Color(0xFF007C91),
    onPrimary = Color.White,
    secondary = Color(0xFF65558F),
    tertiary = Color(0xFF087A55),
    background = Color(0xFFF4FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE4F0F4),
    onBackground = Color(0xFF102027),
    onSurface = Color(0xFF102027),
    onSurfaceVariant = Color(0xFF49606B),
    outline = Color(0xFF76909B),
    error = Color(0xFFBA1A1A)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NanuVisualHome(
    state: NanuHomeState,
    themeMode: NanuThemeMode,
    showOnboarding: Boolean,
    onFinishOnboarding: () -> Unit,
    onTheme: (NanuThemeMode) -> Unit,
    onToggleOnline: () -> Unit,
    onOpen: (NanuDestination) -> Unit,
    onPrompt: (String) -> Unit
) {
    val dark = when (themeMode) {
        NanuThemeMode.DARK -> true
        NanuThemeMode.LIGHT -> false
        NanuThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    var themeDialog by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = if (dark) NanuDark else NanuLight) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(
                modifier = Modifier.background(
                    Brush.radialGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f), Color.Transparent),
                        center = Offset(120f, 20f),
                        radius = 820f
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Header(onTheme = { themeDialog = true })
                    Hero(state)
                    QuickActions(onPrompt, onOpen)
                    StatusStrip(state, onToggleOnline)
                    FeatureGrid(state, onOpen)
                    TrustCard(state, onOpen)
                    Text(
                        "Private by default. Online tools run only when you ask for live information.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
            }
        }

        if (themeDialog) ThemeDialog(themeMode, onDismiss = { themeDialog = false }) {
            themeDialog = false
            onTheme(it)
        }
        if (showOnboarding) Onboarding(onFinishOnboarding)
    }
}

@Composable
private fun Header(onTheme: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("NANU", letterSpacing = 3.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("Local intelligence", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)) {
            IconButton(onClick = onTheme) {
                Icon(Icons.Rounded.Palette, contentDescription = "Choose appearance", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun Hero(state: NanuHomeState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(30.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            val wide = maxWidth >= 600.dp
            if (wide) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NanuOrb(150)
                    Spacer(Modifier.width(26.dp))
                    HeroText(state, Modifier.weight(1f))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NanuOrb(124)
                    Spacer(Modifier.height(14.dp))
                    HeroText(state, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun HeroText(state: NanuHomeState, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("One private workspace for thinking and creating.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Chat offline, speak naturally, read documents with OCR, create images, or ask for current information with visible sources.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusPill(if (state.modelName != null) "AI ready" else "Choose a model", state.modelName != null)
            StatusPill(if (state.onlineTools) "Live tools on" else "Offline only", state.onlineTools)
        }
        state.modelName?.let {
            Text(it, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun NanuOrb(size: Int) {
    val animations = ValueAnimator.areAnimatorsEnabled()
    val transition = rememberInfiniteTransition(label = "nanu-orb")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animations) 360f else 0f,
        animationSpec = infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "orb-phase"
    )
    Canvas(Modifier.size(size.dp).graphicsLayer { rotationZ = phase * 0.03f }) {
        val center = this.center
        val radius = this.size.minDimension * 0.29f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFFE1FCFF), Color(0xFF4DE3F5), Color(0xFF127B99)),
                center,
                radius * 1.18f
            ),
            radius = radius
        )
        drawCircle(Color.White.copy(alpha = 0.66f), radius * 0.48f, center - Offset(radius * 0.24f, radius * 0.28f))
        repeat(3) { ring ->
            drawCircle(
                color = if (ring % 2 == 0) Color(0xFF56E7F7) else Color(0xFFB7A3FF),
                radius = radius * (1.38f + ring * 0.23f),
                style = Stroke(width = (3 - ring).coerceAtLeast(1) * density, cap = StrokeCap.Round),
                alpha = 0.45f - ring * 0.1f
            )
        }
        repeat(8) { index ->
            val angle = Math.toRadians((phase + index * 45f).toDouble())
            val orbit = radius * (1.52f + (index % 3) * 0.16f)
            drawCircle(
                color = if (index % 2 == 0) Color(0xFF4CE5B0) else Color(0xFFC2AFFF),
                radius = (2.4f + index % 3) * density,
                center = center + Offset((cos(angle) * orbit).toFloat(), (sin(angle) * orbit).toFloat()),
                alpha = 0.85f
            )
        }
    }
}

@Composable
private fun QuickActions(onPrompt: (String) -> Unit, onOpen: (NanuDestination) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("QUICK START")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickChip("Kanpur weather", Icons.Rounded.Public) { onPrompt("What is the current weather in Kanpur?") }
            QuickChip("Latest AI news", Icons.Rounded.AutoAwesome) { onPrompt("Show me the latest AI news with sources") }
            QuickChip("BTC now", Icons.Rounded.Draw) { onPrompt("What is the current price of BTC?") }
            QuickChip("Three-card Tarot", Icons.Rounded.Style) { onOpen(NanuDestination.TAROT) }
        }
    }
}

@Composable
private fun QuickChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
        tonalElevation = 2.dp
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(7.dp))
            Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StatusStrip(state: NanuHomeState, onToggleOnline: () -> Unit) {
    Card(
        onClick = onToggleOnline,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Public, null, tint = if (state.onlineTools) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (state.onlineTools) "Online tools enabled" else "Offline-only mode", fontWeight = FontWeight.SemiBold)
                Text("Tap to ${if (state.onlineTools) "disable" else "enable"} weather, prices, news and search", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text(if (state.onlineTools) "ON" else "OFF", color = if (state.onlineTools) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FeatureGrid(state: NanuHomeState, onOpen: (NanuDestination) -> Unit) {
    val features = listOf(
        Feature(NanuDestination.CHAT, "Chat + Models", "Local chat, coding, study and attachments", Icons.Rounded.ChatBubble, state.modelName != null),
        Feature(NanuDestination.TALK, "Continuous Talk", "Speak, hear Nanu, and keep the conversation flowing", Icons.Rounded.Mic, state.speechReady),
        Feature(NanuDestination.FILES, "Ask My Files", "PDF, Office, text, photos and scanned-page OCR", Icons.Rounded.Description, true),
        Feature(NanuDestination.CREATE, "Create Studio", "Private local image generation and editing", Icons.Rounded.AutoAwesome, state.imageModelReady),
        Feature(NanuDestination.TAROT, "Nanu Tarot", "78 cards with upright and reversed readings", Icons.Rounded.Style, true),
        Feature(NanuDestination.PRO, "Projects + Agents", "Files, memory, photo batches and bounded assistants", Icons.Rounded.WorkspacePremium, state.proEnabled)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("YOUR WORKSPACE")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 640.dp) 2 else 1
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                features.chunked(columns).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowItems.forEach { feature -> FeatureCard(feature, Modifier.weight(1f)) { onOpen(feature.destination) } }
                        repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private data class Feature(val destination: NanuDestination, val title: String, val detail: String, val icon: ImageVector, val ready: Boolean)

@Composable
private fun FeatureCard(feature: Feature, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)) {
                    Icon(feature.icon, null, Modifier.padding(10.dp).size(23.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (feature.ready) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline))
            }
            Text(feature.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(feature.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TrustCard(state: NanuHomeState, onOpen: (NanuDestination) -> Unit) {
    Card(onClick = { onOpen(NanuDestination.SAFETY) }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f))) {
        Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Security, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Privacy, safety and diagnostics", fontWeight = FontWeight.Bold)
                Text(
                    "${state.savedChats} saved chat${if (state.savedChats == 1) "" else "s"} • ${state.freeStorageGb} GB free • reports ${if (state.reportReady) "ready" else "export-only"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, active: Boolean) {
    Surface(shape = RoundedCornerShape(50), color = (if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline).copy(alpha = 0.14f)) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SectionTitle(value: String) {
    Text(value, letterSpacing = 1.4.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun ThemeDialog(current: NanuThemeMode, onDismiss: () -> Unit, onChoose: (NanuThemeMode) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Appearance") },
        text = {
            Column {
                ThemeOption("Use device setting", Icons.Rounded.SettingsBrightness, current == NanuThemeMode.SYSTEM) { onChoose(NanuThemeMode.SYSTEM) }
                ThemeOption("Light", Icons.Rounded.LightMode, current == NanuThemeMode.LIGHT) { onChoose(NanuThemeMode.LIGHT) }
                ThemeOption("Dark", Icons.Rounded.DarkMode, current == NanuThemeMode.DARK) { onChoose(NanuThemeMode.DARK) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun ThemeOption(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(label, Modifier.weight(1f))
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun Onboarding(onFinish: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val pages = listOf(
        Triple("Private by default", "Your local model, chats, files and generated images stay on this device unless you choose to share them.", Icons.Rounded.Security),
        Triple("More than chat", "Speak continuously, search several documents with OCR, generate images, and organize projects from one workspace.", Icons.Rounded.AutoAwesome),
        Triple("You control live access", "Weather, prices, news and web search use the internet only when live tools are enabled and your request needs them.", Icons.Rounded.Public)
    )
    val item = pages[page]
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(item.third, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary) },
        title = { Text(item.first) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(item.second, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                Text("${page + 1} of ${pages.size}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
        },
        confirmButton = {
            Button(onClick = { if (page == pages.lastIndex) onFinish() else page++ }) {
                Text(if (page == pages.lastIndex) "Start using Nanu" else "Next")
            }
        },
        dismissButton = {
            AnimatedVisibility(page > 0) { TextButton(onClick = { page-- }) { Text("Back") } }
        }
    )
}
