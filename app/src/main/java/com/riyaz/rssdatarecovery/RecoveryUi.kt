package com.riyaz.rssdatarecovery

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class Screen { HOME, SCAN, RESULTS, PREMIUM, SETTINGS }
private enum class Palette(val label: String, val seed: Color) {
    GOLD("RSS Gold", Color(0xFFD4A72C)), BLUE("Ocean Blue", Color(0xFF3478F6)),
    GREEN("Emerald", Color(0xFF16A477)), VIOLET("Violet", Color(0xFF7654D8))
}
private data class RecoverableFile(val name: String, val type: String, val size: String, val confidence: Int)
private val demoFiles = listOf(
    RecoverableFile("IMG_20260814_183201.jpg", "JPEG image", "3.8 MB", 98),
    RecoverableFile("VID_20260729_221045.mp4", "MP4 video", "18.4 MB", 94),
    RecoverableFile("document_2026.pdf", "PDF document", "1.2 MB", 91),
    RecoverableFile("WhatsApp Image 2026.jpg", "JPEG image", "2.6 MB", 88)
)

@Composable
fun RecoveryApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("rss_recovery", 0) }
    var registered by remember { mutableStateOf(prefs.getBoolean("registered", false)) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    var palette by remember { mutableStateOf(Palette.values().getOrElse(prefs.getInt("palette", 0)) { Palette.GOLD }) }
    val light = when (palette) {
        Palette.GOLD -> lightColorScheme(primary = Color(0xFFB8870B), secondary = Color(0xFF725B18), surface = Color(0xFFFFFBF2))
        Palette.BLUE -> lightColorScheme(primary = Color(0xFF246BCE), secondary = Color(0xFF4F5D95))
        Palette.GREEN -> lightColorScheme(primary = Color(0xFF087F5B), secondary = Color(0xFF3C6E63))
        Palette.VIOLET -> lightColorScheme(primary = Color(0xFF6647C6), secondary = Color(0xFF66558F))
    }
    val darkScheme = when (palette) {
        Palette.GOLD -> darkColorScheme(primary = Color(0xFFFFD76A), secondary = Color(0xFFE8C96A))
        Palette.BLUE -> darkColorScheme(primary = Color(0xFFA8C7FA), secondary = Color(0xFFB9C4FF))
        Palette.GREEN -> darkColorScheme(primary = Color(0xFF7BE0BC), secondary = Color(0xFFA7D7CC))
        Palette.VIOLET -> darkColorScheme(primary = Color(0xFFD0BCFF), secondary = Color(0xFFCCC0E8))
    }
    MaterialTheme(colorScheme = if (dark) darkScheme else light) {
        if (!registered) {
            RegistrationScreen { name, email ->
                prefs.edit().putBoolean("registered", true).putString("name", name).putString("email", email).apply()
                registered = true
            }
        } else {
            RecoveryShell(palette, dark,
                onDarkChange = { dark = it; prefs.edit().putBoolean("dark", it).apply() },
                onPaletteChange = { palette = it; prefs.edit().putInt("palette", it.ordinal).apply() })
        }
    }
}

@Composable
private fun RegistrationScreen(onRegister: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    val valid = name.trim().length >= 2 && email.trim().contains("@") && email.trim().contains(".")
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BrandMark()
                Spacer(Modifier.height(18.dp))
                Text("RSS Data Recovery", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Recover what matters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(28.dp))
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Full name") }, leadingIcon = { Icon(Icons.Default.Person, null) })
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Email address") }, leadingIcon = { Icon(Icons.Default.Email, null) })
                Spacer(Modifier.height(16.dp))
                Text("Your customer account is registered through RSS Licensing Service.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(18.dp))
                Button({ onRegister(name.trim(), email.trim()) }, Modifier.fillMaxWidth().height(52.dp), enabled = valid) { Text("Create account") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecoveryShell(palette: Palette, dark: Boolean, onDarkChange: (Boolean) -> Unit, onPaletteChange: (Palette) -> Unit) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var drawerOpen by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(if (drawerOpen) DrawerValue.Open else DrawerValue.Closed)
    val context = LocalContext.current
    LaunchedEffect(drawerOpen) { if (drawerOpen) drawerState.open() else drawerState.close() }
    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
        ModalDrawerSheet(Modifier.width(310.dp)) {
            Spacer(Modifier.height(22.dp))
            Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                BrandMark(); Spacer(Modifier.width(12.dp)); Column { Text("RSS Data Recovery", fontWeight = FontWeight.Bold); Text("Professional recovery", style = MaterialTheme.typography.labelSmall) }
            }
            Spacer(Modifier.height(20.dp))
            DrawerItem("Home", Icons.Default.Home, screen == Screen.HOME) { screen = Screen.HOME; drawerOpen = false }
            DrawerItem("Deep Recovery", Icons.Default.Search, screen == Screen.SCAN) { screen = Screen.SCAN; drawerOpen = false }
            DrawerItem("Recovery Results", Icons.Default.Folder, screen == Screen.RESULTS) { screen = Screen.RESULTS; drawerOpen = false }
            DrawerItem("Premium", Icons.Default.WorkspacePremium, screen == Screen.PREMIUM) { screen = Screen.PREMIUM; drawerOpen = false }
            DrawerItem("Settings", Icons.Default.Settings, screen == Screen.SETTINGS) { screen = Screen.SETTINGS; drawerOpen = false }
            Spacer(Modifier.weight(1f)); HorizontalDivider()
            Column(Modifier.padding(18.dp)) {
                Text("RSS Data Recovery", fontWeight = FontWeight.SemiBold)
                Text("Razeen Secure Solution", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(10.dp))
                LinkRow(Icons.Default.Phone, "+94 77 115 5504 | +94 70 155 5504") { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+94771155504"))) }
                LinkRow(Icons.Default.Email, "rsscctvsolution@gmail.com") { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:rsscctvsolution@gmail.com"))) }
                LinkRow(Icons.Default.Language, "www.rsscctvsolution.eu.cc") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.rsscctvsolution.eu.cc"))) }
                Spacer(Modifier.height(8.dp))
                Text("RSS", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.rsscctvsolution.eu.cc"))) })
            }
        }
    }) {
        Scaffold(topBar = {
            TopAppBar(title = { Text(screenTitle(screen)) }, navigationIcon = {
                IconButton({ drawerOpen = true }) { Icon(Icons.Default.Menu, "Menu") }
            }, actions = { if (screen == Screen.HOME) IconButton({ screen = Screen.SETTINGS }) { Icon(Icons.Default.Settings, "Settings") } })
        }) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (screen) {
                    Screen.HOME -> HomeScreen { screen = Screen.SCAN }
                    Screen.SCAN -> ScanScreen { screen = Screen.RESULTS }
                    Screen.RESULTS -> ResultsScreen { screen = Screen.PREMIUM }
                    Screen.PREMIUM -> PremiumScreen()
                    Screen.SETTINGS -> SettingsScreen(dark, onDarkChange, palette, onPaletteChange)
                }
            }
        }
    }
}

private fun screenTitle(screen: Screen) = when (screen) {
    Screen.HOME -> "Overview"; Screen.SCAN -> "Deep Recovery"; Screen.RESULTS -> "Recovery Results"; Screen.PREMIUM -> "Premium"; Screen.SETTINGS -> "Settings"
}

@Composable private fun DrawerItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(label = { Text(label) }, icon = { Icon(icon, null) }, selected = selected, onClick = onClick, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
}
@Composable private fun LinkRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(17.dp)); Spacer(Modifier.width(9.dp)); Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
@Composable private fun BrandMark() {
    Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        Text("RSS", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
    }
}

@Composable private fun HomeScreen(start: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Data Recovery", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Scan first. Preview recoverable files. Recover with Premium.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), Alignment.Center) { Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.onPrimary) }; Spacer(Modifier.width(14.dp)); Column { Text("Deep Recovery", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Free preview available") } }
                Spacer(Modifier.height(18.dp)); Text("Find deleted or lost files and inspect their recoverability before purchasing Premium.")
                Spacer(Modifier.height(18.dp)); Button(start, Modifier.fillMaxWidth().height(50.dp)) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Start Deep Scan") }
            }
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { StatCard("Last scan", "Not yet", Icons.Default.History, Modifier.weight(1f)); StatCard("Recoverable", "— files", Icons.Default.Folder, Modifier.weight(1f)) } }
        item { Text("Recovery protection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item { InfoRow(Icons.Default.VerifiedUser, "Original filename", "Preserve the original name when recovery is possible.") }
        item { InfoRow(Icons.Default.DataObject, "Original metadata", "Preserve available metadata during recovery.") }
        item { InfoRow(Icons.Default.Lock, "Premium recovery", "Scanning and preview are free; saving recovered files requires Premium.") }
    }
}
@Composable private fun StatCard(title: String, value: String, icon: ImageVector, modifier: Modifier) { Card(modifier = modifier, shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(8.dp)); Text(title, style = MaterialTheme.typography.labelMedium); Text(value, fontWeight = FontWeight.Bold) } } }
@Composable private fun InfoRow(icon: ImageVector, title: String, text: String) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column { Text(title, fontWeight = FontWeight.SemiBold); Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }

@Composable private fun ScanScreen(onResults: () -> Unit) {
    var scanning by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(22.dp)) { Text("Deep Recovery", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("Free users can scan and inspect recoverable files. Actual recovery is Premium-only."); Spacer(Modifier.height(18.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Storage, null); Spacer(Modifier.width(10.dp)); Column { Text("Internal storage", fontWeight = FontWeight.SemiBold); Text("Scan device storage for recoverable data", style = MaterialTheme.typography.bodySmall) } } } }
        Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(20.dp)) { Text("Scan scope", fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(10.dp)); FilterChip(selected = true, onClick = {}, label = { Text("Deep scan") }); Spacer(Modifier.height(8.dp)); Text("Images • Videos • Documents • Audio", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        if (scanning) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Scanning storage…", color = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.weight(1f)); Button({ scanning = true; onResults() }, Modifier.fillMaxWidth().height(54.dp)) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text(if (scanning) "Scan running…" else "Scan now") }
    }
}

@Composable private fun ResultsScreen(onPremium: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Recoverable files", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Preview is free • Recovery requires Premium", style = MaterialTheme.typography.bodySmall) }; AssistChip(onClick = onPremium, label = { Text("Premium") }) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(true, {}, label = { Text("All") }); FilterChip(false, {}, label = { Text("Images") }); FilterChip(false, {}, label = { Text("Videos") }) } }
        items(demoFiles) { file -> FileCard(file, onPremium) }
    }
}
@Composable private fun FileCard(file: RecoverableFile, onPremium: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.secondaryContainer), Alignment.Center) { Icon(Icons.Default.InsertDriveFile, null) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${file.type} • ${file.size}", style = MaterialTheme.typography.bodySmall) }; Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(12.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text("Recoverability ${file.confidence}%", style = MaterialTheme.typography.labelMedium); Spacer(Modifier.weight(1f)); Text("View details", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onPremium() }) }
        Spacer(Modifier.height(8.dp)); LinearProgressIndicator(progress = { file.confidence / 100f }, Modifier.fillMaxWidth())
    } }
}

@Composable private fun PremiumScreen() {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(24.dp)) { Icon(Icons.Default.WorkspacePremium, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(12.dp)); Text("Unlock full recovery", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Scan and inspect recoverable files for free. Premium unlocks saving recovered files."); Spacer(Modifier.height(18.dp)); Button({}, Modifier.fillMaxWidth().height(52.dp)) { Text("Upgrade to Premium") } } } }
        item { Text("Premium includes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { InfoRow(Icons.Default.CheckCircle, "Actual recovery", "Save recovered files to the RSS Data Recovery folder.") }
        item { InfoRow(Icons.Default.Folder, "Original filenames", "Keep original names whenever technically recoverable.") }
        item { InfoRow(Icons.Default.DataObject, "Metadata preservation", "Preserve original metadata when available.") }
        item { InfoRow(Icons.Default.SelectAll, "Batch recovery", "Recover multiple selected files in one operation.") }
    }
}

@Composable private fun SettingsScreen(dark: Boolean, onDarkChange: (Boolean) -> Unit, palette: Palette, onPaletteChange: (Palette) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { Card(shape = RoundedCornerShape(20.dp)) { Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (dark) Icons.Default.DarkMode else Icons.Default.LightMode, null); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Appearance", fontWeight = FontWeight.SemiBold); Text("Light | Dark", style = MaterialTheme.typography.bodySmall) }; SingleChoiceSegmentedButtonRow { SegmentedButton(selected = !dark, onClick = { onDarkChange(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Light") }; SegmentedButton(selected = dark, onClick = { onDarkChange(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Dark") } } } } }
        item { Text("Color theme", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("App color selection is independent from Appearance.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(Palette.values().toList()) { p -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(if (palette == p) 2.dp else 1.dp, if (palette == p) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)).clickable { onPaletteChange(p) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(38.dp).clip(CircleShape).background(p.seed)); Spacer(Modifier.width(14.dp)); Text(p.label, fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f)); if (palette == p) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) } }
        item { Text("Account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { InfoRow(Icons.Default.VerifiedUser, "RSS Licensing Service", "Customer identity and license state are managed centrally.") }
        item { InfoRow(Icons.Default.Folder, "Recovery folder", "RSS Data Recovery") }
    }
}
