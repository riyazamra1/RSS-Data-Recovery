package com.riyaz.rssdatarecovery

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import kotlinx.coroutines.delay

private enum class RScreen { HOME, SCAN, RESULTS, PREMIUM, SETTINGS }
private enum class RMode(val title: String, val subtitle: String) { QUICK("Quick Recovery", "Normal Scan"), DEEP("Deep Recovery", "Full Scan") }
private enum class RCategory(val title: String, val icon: ImageVector) { PHOTOS("Photos", Icons.Default.Image), VIDEOS("Videos", Icons.Default.VideoLibrary), AUDIOS("Audios", Icons.Default.AudioFile), DOCUMENTS("Files / Documents", Icons.Default.Description) }

@Composable
fun RecoveryAppV2() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("rss_recovery", 0) }
    var registered by remember { mutableStateOf(prefs.getBoolean("registered", false)) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    val scheme = if (dark) darkColorScheme(primary = Color(0xFFFFD76A), secondary = Color(0xFFE8C96A)) else lightColorScheme(primary = Color(0xFFB8870B), secondary = Color(0xFF725B18), surface = Color(0xFFFFFBF2))
    MaterialTheme(colorScheme = scheme) {
        if (!registered) RegistrationV2 { name, email -> prefs.edit().putBoolean("registered", true).putString("name", name).putString("email", email).apply(); registered = true }
        else RecoveryShellV2(dark, { dark = it; prefs.edit().putBoolean("dark", it).apply() })
    }
}

@Composable private fun RegistrationV2(done: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) { Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) { BrandV2(); Spacer(Modifier.height(16.dp)); Text("RSS Data Recovery", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Recover what matters.", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(24.dp)); OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Full name") }, singleLine = true); Spacer(Modifier.height(10.dp)); OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email address") }, singleLine = true); Spacer(Modifier.height(18.dp)); Button({ done(name.trim(), email.trim()) }, Modifier.fillMaxWidth().height(52.dp), enabled = name.trim().length > 1 && email.contains("@")) { Text("Create account") } } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun RecoveryShellV2(dark: Boolean, setDark: (Boolean) -> Unit) {
    var screen by remember { mutableStateOf(RScreen.HOME) }; var history by remember { mutableStateOf(listOf(RScreen.HOME)) }; var menu by remember { mutableStateOf(false) }; var mode by remember { mutableStateOf(RMode.QUICK) }; var category by remember { mutableStateOf<RCategory?>(null) }
    fun go(to: RScreen) { if (to != screen) { history += to; screen = to } }; fun back() { if (menu) menu = false else if (history.size > 1) { history = history.dropLast(1); screen = history.last() } }; fun home() { history = listOf(RScreen.HOME); screen = RScreen.HOME }
    BackHandler(menu || history.size > 1) { back() }
    val drawer = rememberDrawerState(if (menu) DrawerValue.Open else DrawerValue.Closed); LaunchedEffect(menu) { if (menu) drawer.open() else drawer.close() }
    ModalNavigationDrawer(drawer, { ModalDrawerSheet(Modifier.width(315.dp)) { Spacer(Modifier.height(24.dp)); Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { BrandV2(); Spacer(Modifier.width(12.dp)); Text("RSS Data Recovery", fontWeight = FontWeight.Bold) }; DrawerV2("Home", Icons.Default.Home, screen == RScreen.HOME) { home(); menu = false }; DrawerV2("Quick Recovery", Icons.Default.FlashOn, false) { mode = RMode.QUICK; category = null; go(RScreen.SCAN); menu = false }; DrawerV2("Deep Recovery", Icons.Default.Search, false) { mode = RMode.DEEP; category = null; go(RScreen.SCAN); menu = false }; DrawerV2("Recovery Results", Icons.Default.Folder, screen == RScreen.RESULTS) { go(RScreen.RESULTS); menu = false }; DrawerV2("Premium", Icons.Default.WorkspacePremium, screen == RScreen.PREMIUM) { go(RScreen.PREMIUM); menu = false }; Spacer(Modifier.height(6.dp)); Card(Modifier.padding(horizontal = 12.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.WorkspacePremium, null); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text("Premium Upgrade", fontWeight = FontWeight.Bold); Text("Unlock recovery", style = MaterialTheme.typography.labelSmall) }; Button({ go(RScreen.PREMIUM); menu = false }) { Text("Upgrade") } } }; DrawerV2("Settings", Icons.Default.Settings, screen == RScreen.SETTINGS) { go(RScreen.SETTINGS); menu = false }; Spacer(Modifier.weight(1f)); HorizontalDivider(); Column(Modifier.padding(18.dp)) { Text("RSS Data Recovery", fontWeight = FontWeight.Bold); Text("Razeen Secure Solution", style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(8.dp)); ContactV2(Icons.Default.Phone, "+94 77 115 5504") { dial(context, "+94771155504") }; ContactV2(Icons.Default.Email, "rsscctvsolution@gmail.com") { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:rsscctvsolution@gmail.com"))) }; ContactV2(Icons.Default.Language, "www.rsscctvsolution.eu.cc") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.rsscctvsolution.eu.cc"))) } } } }) {
        Scaffold(topBar = { TopAppBar(title = { Text(screen.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) }, navigationIcon = { if (screen == RScreen.HOME) IconButton({ menu = true }) { Icon(Icons.Default.Menu, "Menu") } else IconButton({ back() }) { Icon(Icons.Default.ArrowBack, "Back") } }, actions = { if (screen != RScreen.SETTINGS) IconButton({ go(RScreen.SETTINGS) }) { Icon(Icons.Default.Settings, "Settings") } }) }) { pad -> Box(Modifier.padding(pad).fillMaxSize()) { when (screen) { RScreen.HOME -> HomeV2({ mode = RMode.QUICK; category = null; go(RScreen.SCAN) }, { mode = RMode.DEEP; category = null; go(RScreen.SCAN) }, { category = it; mode = RMode.QUICK; go(RScreen.SCAN) }, { go(RScreen.PREMIUM) }); RScreen.SCAN -> ScanV2(mode, category) { go(RScreen.RESULTS) }; RScreen.RESULTS -> ResultsV2 { go(RScreen.PREMIUM) }; RScreen.PREMIUM -> PremiumV2(); RScreen.SETTINGS -> SettingsV2(dark, setDark) } } } }
}

@Composable private fun HomeV2(quick: () -> Unit, deep: () -> Unit, category: (RCategory) -> Unit, premium: () -> Unit) {
    val slides = listOf(
        Triple("Premium Recovery", "Free users can scan and preview. Premium unlocks saving recovered files.", Icons.Default.WorkspacePremium),
        Triple("Recovery Protection", "Protected workflow for original filenames and available metadata.", Icons.Default.Security),
        Triple("Deep Recovery", "Full storage scan for harder-to-find recoverable files.", Icons.Default.Search),
        Triple("Recovery Details", "See file type, size and recoverability before recovery.", Icons.Default.Info),
        Triple("RSS CLOUD SYNC", "RSS cloud synchronization and backup project.", Icons.Default.CloudSync),
        Triple("RSS Ai Assistant", "RSS Android AI assistant project.", Icons.Default.AutoAwesome),
        Triple("RSS INVOICE MAKER", "Sales representative order and invoice workflow.", Icons.Default.ReceiptLong),
        Triple("RSS MONEY MANAGER", "Personal finance and wallet management project.", Icons.Default.AccountBalanceWallet),
        Triple("RSS LAUNCHER", "Custom Android launcher and productivity project.", Icons.Default.Launch),
        Triple("RSS DEVICE GUARDIAN", "Device protection, privacy and optimization project.", Icons.Default.Security),
        Triple("RSS CLIPBOARD", "Lightweight clipboard and cloud backup project.", Icons.Default.ContentPaste)
    )
    var index by remember { mutableIntStateOf(0) }; LaunchedEffect(Unit) { while (true) { delay(3000); index = (index + 1) % slides.size } }; val s = slides[index]
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Data Recovery", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Choose how you want to recover your files.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Card(Modifier.fillMaxWidth().clickable { premium() }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), Alignment.Center) { Icon(s.third, null, tint = MaterialTheme.colorScheme.onPrimary) }; Spacer(Modifier.width(14.dp)); Column { Text(s.first, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(s.second, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(5.dp)); Text("RSS PROJECTS • changing every 3 seconds", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) } } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { ModeCardV2(RMode.QUICK, Icons.Default.FlashOn, quick, Modifier.weight(1f)); ModeCardV2(RMode.DEEP, Icons.Default.Search, deep, Modifier.weight(1f)) } }
        item { Text("Recover by category", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(RCategory.values().toList()) { c -> Card(Modifier.fillMaxWidth().clickable { category(c) }, shape = RoundedCornerShape(20.dp)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(c.icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp)); Text(c.title, Modifier.weight(1f), fontWeight = FontWeight.Bold); Icon(Icons.Default.ChevronRight, null) } } }
        item { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.WorkspacePremium, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Unlock Premium", fontWeight = FontWeight.Bold); Text("Recover and save selected files", style = MaterialTheme.typography.bodySmall) }; Button(onClick = premium) { Text("Upgrade") } } } }
    }
}

@Composable private fun ModeCardV2(mode: RMode, icon: ImageVector, click: () -> Unit, modifier: Modifier) { Card(modifier.clickable { click() }, shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(17.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(9.dp)); Text(mode.title, fontWeight = FontWeight.Bold); Text(mode.subtitle, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall); Text(if (mode == RMode.QUICK) "Fast normal scan" else "Full storage scan", style = MaterialTheme.typography.bodySmall) } } }
@Composable private fun ScanV2(mode: RMode, category: RCategory?, results: () -> Unit) { Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(22.dp)) { Text(mode.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(mode.subtitle); Spacer(Modifier.height(10.dp)); Text(if (mode == RMode.QUICK) "Normal scan" else "Full scan" ); if (category != null) Text("Category: ${category.title}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } }; Text("Free users can scan and preview recoverable files. Premium is required only to save/recover selected files.", style = MaterialTheme.typography.bodySmall); Spacer(Modifier.weight(1f)); Button(results, Modifier.fillMaxWidth().height(54.dp)) { Text("Start ${mode.title}") } } }
@Composable private fun ResultsV2(upgrade: () -> Unit) { LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Recoverable files", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Preview is free") }; Button(upgrade) { Text("Upgrade") } } }; items(listOf("IMG_20260814_183201.jpg", "VID_20260729_221045.mp4", "document_2026.pdf")) { n -> Card(shape = RoundedCornerShape(18.dp)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.InsertDriveFile, null); Spacer(Modifier.width(10.dp)); Text(n, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); Button(upgrade) { Text("Recover") } } } } } }

@Composable private fun PremiumV2() {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item { Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(24.dp)) { Icon(Icons.Default.WorkspacePremium, null, Modifier.size(44.dp)); Text("Premium Recovery", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Unlock actual file recovery and saving."); Spacer(Modifier.height(16.dp)); Button({}, Modifier.fillMaxWidth().height(52.dp)) { Text("Upgrade to Premium") } } } }
        item { Text("Free vs Premium", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { PremiumCompareTableV2() }
        item { FeatureV2(Icons.Default.Security, "Recovery Protection", "Protected recovery workflow and duplicate-safe handling.") }
        item { FeatureV2(Icons.Default.Folder, "Original filenames", "Keep original filenames whenever technically recoverable.") }
        item { FeatureV2(Icons.Default.DataObject, "Metadata preservation", "Preserve available original metadata.") }
        item { FeatureV2(Icons.Default.Save, "Save recovered files", "Save recovered files into the RSS Data Recovery folder.") }
    }
}

@Composable private fun PremiumCompareTableV2() {
    val rows = listOf(
        "Quick Recovery scan" to Pair("✓", "✓"),
        "Deep Recovery scan" to Pair("✓", "✓"),
        "Preview recoverable files" to Pair("✓", "✓"),
        "File type / size details" to Pair("✓", "✓"),
        "Recover & save files" to Pair("—", "✓"),
        "Original filenames" to Pair("Preview", "✓"),
        "Metadata preservation" to Pair("Preview", "✓"),
        "Recovery Protection" to Pair("Basic", "Full"),
        "RSS Data Recovery folder" to Pair("—", "✓")
    )
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("FEATURE", Modifier.weight(1.55f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Text("FREE", Modifier.weight(0.7f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Text("PREMIUM", Modifier.weight(0.85f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            rows.forEachIndexed { i, (feature, values) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(feature, Modifier.weight(1.55f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Text(values.first, Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text(values.second, Modifier.weight(0.85f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                if (i != rows.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable private fun FeatureV2(icon: ImageVector, title: String, text: String) { Row(verticalAlignment = Alignment.Top) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column { Text(title, fontWeight = FontWeight.Bold); Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun SettingsV2(dark: Boolean, setDark: (Boolean) -> Unit) { Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) { Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Row(verticalAlignment = Alignment.CenterVertically) { Text("Light | Dark", Modifier.weight(1f)); SegmentedButtonRowV2(dark, setDark) }; Text("Appearance is independent from app color themes.", style = MaterialTheme.typography.bodySmall) } }
@Composable private fun SegmentedButtonRowV2(dark: Boolean, setDark: (Boolean) -> Unit) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(!dark, { setDark(false) }, label = { Text("Light") }); FilterChip(dark, { setDark(true) }, label = { Text("Dark") }) } }
@Composable private fun DrawerV2(text: String, icon: ImageVector, selected: Boolean, click: () -> Unit) { NavigationDrawerItem(label = { Text(text) }, icon = { Icon(icon, null) }, selected = selected, onClick = click, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) }
@Composable private fun ContactV2(icon: ImageVector, text: String, click: () -> Unit) { Row(Modifier.fillMaxWidth().clickable { click() }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall) } }
@Composable private fun BrandV2() { Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary), Alignment.Center) { Text("RSS", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black) } }
private fun dial(context: android.content.Context, number: String) { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
