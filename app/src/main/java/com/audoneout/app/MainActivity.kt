package com.audoneout.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AudOneOutTheme {
                AudOneOutApp()
            }
        }
    }
}

private sealed class AppDestination(
    val route: String,
    val label: String,
    val glyph: String
) {
    data object Home : AppDestination("home", "Home", "H")
    data object Library : AppDestination("library", "Library", "L")
    data object Player : AppDestination("player", "Player", "P")
    data object Settings : AppDestination("settings", "Settings", "S")
}

private val bottomDestinations = listOf(
    AppDestination.Home,
    AppDestination.Library,
    AppDestination.Player,
    AppDestination.Settings
)

@Composable
private fun AudOneOutApp(viewModel: MainViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val selected = bottomDestinations.firstOrNull { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    } ?: AppDestination.Home

    Scaffold(
        topBar = {
            AudOneOutTopBar(selected.label)
        },
        bottomBar = {
            NavigationBar(
                containerColor = AudOneOutColors.surface,
                tonalElevation = 0.dp
            ) {
                bottomDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            SymbolBadge(
                                glyph = destination.glyph,
                                contentDescription = destination.label,
                                selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                            )
                        },
                        label = { Text(destination.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        },
        containerColor = AudOneOutColors.background
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route,
            modifier = Modifier.padding(contentPadding)
        ) {
            composable(AppDestination.Home.route) {
                HomeScreen(uiState)
            }
            composable(AppDestination.Library.route) {
                LibraryScreen(uiState.library)
            }
            composable(AppDestination.Player.route) {
                PlayerScreen(uiState.nowPlayingTitle)
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen(uiState.scanMessage)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudOneOutTopBar(title: String) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "AudOneOut",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = AudOneOutColors.textMuted
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = AudOneOutColors.background,
            titleContentColor = AudOneOutColors.textPrimary
        )
    )
}

@Composable
private fun HomeScreen(uiState: MainUiState) {
    ScreenFrame {
        HeroPanel()
        MetricGrid(uiState.library)
        EmptyState(
            glyph = "SR",
            title = "Ready for your local library",
            body = "AudOneOut will scan music stored on this phone, then turn it into a focused library for listening, queues, and smart mixes."
        )
    }
}

@Composable
private fun LibraryScreen(snapshot: LibrarySnapshot) {
    ScreenFrame {
        SectionTitle("Library")
        FeatureRow("SO", "Songs", "${snapshot.trackCount} tracks indexed")
        FeatureRow("AL", "Albums", "${snapshot.albumCount} releases ready")
        FeatureRow("AR", "Artists", "${snapshot.artistCount} artists found")
        FeatureRow("FO", "Folders", "${snapshot.folderCount} music folders")
        EmptyState(
            glyph = "LB",
            title = "No songs yet",
            body = "MediaStore scanning and permission handling land next, without broad filesystem access."
        )
    }
}

@Composable
private fun PlayerScreen(nowPlayingTitle: String) {
    ScreenFrame {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(AudOneOutColors.accentPurple, AudOneOutColors.accentCoral)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            WaveformMark()
        }
        Text(
            text = nowPlayingTitle,
            color = AudOneOutColors.textPrimary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        FeatureRow("PL", "Playback engine", "Media3 background playback arrives in Milestone 5")
        FeatureRow("QU", "Queue", "Queue management is staged after the first library pass")
    }
}

@Composable
private fun SettingsScreen(scanMessage: String) {
    ScreenFrame {
        SectionTitle("Settings")
        FeatureRow("PV", "Privacy first", "No account, no analytics, no audio upload")
        FeatureRow("EX", "Folder exclusions", "WhatsApp Audio, ringtones, voice recordings, and similar folders will be skippable")
        FeatureRow("SC", "Scanner", scanMessage)
        EmptyState(
            glyph = "LC",
            title = "Local-only by default",
            body = "Online playlist interpretation will be optional and clearly labelled when it is added."
        )
    }
}

@Composable
private fun ScreenFrame(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        content()
    }
}

@Composable
private fun HeroPanel() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AudOneOutColors.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AudOneOutColors.accentCoral, AudOneOutColors.accentPurple)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "AO",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
        }
        Text(
            text = "Your local music, amplified.",
            color = AudOneOutColors.textPrimary,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "A dark-first player for high-control listening, clean queues, and future prompt-built playlists.",
            color = AudOneOutColors.textSecondary,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun MetricGrid(snapshot: LibrarySnapshot) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        MetricCard("Tracks", snapshot.trackCount.toString(), Modifier.weight(1f))
        MetricCard("Albums", snapshot.albumCount.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = AudOneOutColors.surfaceAlt),
        border = BorderStroke(1.dp, AudOneOutColors.line)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = value, color = AudOneOutColors.textPrimary, style = MaterialTheme.typography.headlineSmall)
            Text(text = label, color = AudOneOutColors.textMuted, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun FeatureRow(glyph: String, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AudOneOutColors.surfaceAlt)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SymbolBadge(glyph = glyph, contentDescription = title)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = AudOneOutColors.textPrimary, fontWeight = FontWeight.SemiBold)
            Text(text = body, color = AudOneOutColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = AudOneOutColors.textPrimary,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun EmptyState(glyph: String, title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AudOneOutColors.surface)
            .padding(20.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SymbolBadge(glyph = glyph, contentDescription = title)
        Text(text = title, color = AudOneOutColors.textPrimary, style = MaterialTheme.typography.titleMedium)
        Text(text = body, color = AudOneOutColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SymbolBadge(
    glyph: String,
    contentDescription: String,
    selected: Boolean = false
) {
    val background = if (selected) {
        AudOneOutColors.accentCoral
    } else {
        AudOneOutColors.accentPurple.copy(alpha = 0.18f)
    }
    val foreground = if (selected) Color.White else AudOneOutColors.accentCoral

    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(background)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = glyph,
            color = foreground,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun WaveformMark() {
    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 28.dp)) {
        val stroke = 8.dp.toPx()
        val step = size.width / 11f
        for (index in 0..10) {
            val centerX = index * step
            val heightFactor = listOf(0.28f, 0.52f, 0.36f, 0.86f, 0.62f, 1f, 0.68f, 0.4f, 0.78f, 0.48f, 0.3f)[index]
            val lineHeight = size.height * heightFactor
            drawLine(
                color = Color.White.copy(alpha = 0.78f),
                start = Offset(centerX, (size.height - lineHeight) / 2f),
                end = Offset(centerX, (size.height + lineHeight) / 2f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

private object AudOneOutColors {
    val background = Color(0xFF101113)
    val surface = Color(0xFF191A1F)
    val surfaceAlt = Color(0xFF22232A)
    val line = Color(0xFF32333B)
    val textPrimary = Color(0xFFF6F1F4)
    val textSecondary = Color(0xFFD8CDD3)
    val textMuted = Color(0xFFA99DA5)
    val accentPurple = Color(0xFF7E5CFF)
    val accentCoral = Color(0xFFFF6B5F)
}

@Composable
private fun AudOneOutTheme(content: @Composable () -> Unit) {
    val colorScheme = androidx.compose.material3.darkColorScheme(
        primary = AudOneOutColors.accentCoral,
        secondary = AudOneOutColors.accentPurple,
        background = AudOneOutColors.background,
        surface = AudOneOutColors.surface,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = AudOneOutColors.textPrimary,
        onSurface = AudOneOutColors.textPrimary
    )

    MaterialTheme(colorScheme = colorScheme, content = {
        Surface(color = AudOneOutColors.background) {
            content()
        }
    })
}
