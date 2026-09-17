package com.riyaz.rssdatarecovery

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.ui.res.painterResource
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
    var showWelcome by remember { mutableStateOf(false) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    var theme by remember { mutableIntStateOf(prefs.getInt("theme", 0)) }
    val primary = listOf(Color(0xFFD4A62A), Color(0xFF8E7CFF), Color(0xFF27A69A), Color(0xFFE06A8A))[theme.coerceIn(0, 3)]
    val scheme = if (dark) darkColorScheme(primary = primary, secondary = primary) else lightColorScheme(primary = primary, secondary = primary, surface = Color(0xFFFFFBF4))
    MaterialTheme(colorScheme = scheme) {
        when {
            !registered -> RegistrationV2 { name, email ->
                prefs.edit().putBoolean("registered", true).putString("name", name).putString("email", email).apply()
                registered = true
                showWelcome = true
            }
            showWelcome -> WelcomeCustomerV2(prefs.getString("name", "Customer") ?: "Customer") { showWelcome = false }
            else -> RecoveryShellV2(dark, { dark = it; prefs.edit().putBoolean("dark", it).apply() }, theme, { theme = it; prefs.edit().putInt("theme", it).apply() })
        }
    }
}

@Composable
fun BrandV2() {
    Box(Modifier.size(68.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), Alignment.Center) {
        Icon(painterResource(com.riyaz.rssdatarecovery.R.drawable.rss_splash_icon), contentDescription = "RSS Data Recovery", modifier = Modifier.size(52.dp))
    }
}

@Composable
private fun RegistrationV2(done: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Color(0xFF070707)), Alignment.Center) {
        Card(Modifier.fillMaxWidth().padding(24.dp), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .08f))) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BrandV2(); Spacer(Modifier.height(16.dp))
                Text("Welcome to RSS Data Recovery", color = Color(0xFFFFD76A), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Create your recovery profile", color = Color.White.copy(alpha = .7f))
                Spacer(Modifier.height(22.dp))
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Full name") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email address") }, singleLine = true)
                Spacer(Modifier.height(18.dp))
                Button({ done(name.trim(), email.trim()) }, Modifier.fillMaxWidth().height(52.dp), enabled = name.trim().length > 1 && email.contains("@")) { Text("Continue") }
                Spacer(Modifier.height(12.dp))
                Text("Your email becomes your RSS customer ID.", color = Color.White.copy(alpha = .48f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun WelcomeCustomerV2(name: String, continueApp: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF070707)), Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(100.dp).clip(CircleShape).background(Color(0xFF211A08)), Alignment.Center) { Icon(Icons.Default.CheckCircle, null, Modifier.size(62.dp), tint = Color(0xFFFFD76A)) }
            Spacer(Modifier.height(24.dp)); Text("Congratulations! 🎉", color = Color(0xFFFFD76A), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp)); Text("Welcome, $name", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp)); Text("Your RSS Data Recovery profile is ready.", color = Color.White.copy(alpha = .72f))
            Spacer(Modifier.height(26.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .08f))) {
                Column(Modifier.padding(20.dp)) { Text("Your account is ready", color = Color.White, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text("Welcome to the RSS Data Recovery family.", color = Color.White.copy(alpha = .62f)); Spacer(Modifier.height(18.dp)); Button(continueApp, Modifier.fillMaxWidth().height(52.dp)) { Text("Start Recovering") } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecoveryShellV2(dark: Boolean, setDark: (Boolean) -> Unit, theme: Int, setTheme: (Int) -> Unit) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(RScreen.HOME) }
    var history by remember { mutableStateOf(listOf(RScreen.HOME)) }
    var drawerOpen by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(RMode.QUICK) }
    var category by remember { mutableStateOf<RCategory?>(null) }
    fun go(to: RScreen) { if (to != screen) { history += to; screen = to } }
    fun home() { history = listOf(RScreen.HOME); screen = RScreen.HOME }
    fun back() { if (drawerOpen) drawerOpen = false else if (history.size > 1) { history = history.dropLast(1); screen = history.last() } }
    BackHandler(drawerOpen || history.size > 1) { back() }
    val drawer = rememberDrawerState(if (drawerOpen) DrawerValue.Open else DrawerValue.Closed)
    LaunchedEffect(drawerOpen) { if (drawerOpen) drawer.open() else drawer.close() }
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet(Modifier.width(315.dp)) {
            Spacer(Modifier.height(20.dp)); Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { BrandV2(); Spacer(Modifier.width(12.dp)); Text("RSS Data Recovery", fontWeight = FontWeight.Bold) }
            DrawerItem("Home", Icons.Default.Home, screen == RScreen.HOME) { home(); drawerOpen = false }
            DrawerItem("Quick Recovery", Icons.Default.FlashOn, false) { mode = RMode.QUICK; category = null; go(RScreen.SCAN); drawerOpen = false }
            DrawerItem("Deep Recovery", Icons.Default.Search, false) { mode = RMode.DEEP; category = null; go(RScreen.SCAN); drawerOpen = false }
            DrawerItem("Recovery Results", Icons.Default.Folder, screen == RScreen.RESULTS) { go(RScreen.RESULTS); drawerOpen = false }
            DrawerItem("Premium Upgrade", Icons.Default.WorkspacePremium, screen == RScreen.PREMIUM) { go(RScreen.PREMIUM); drawerOpen = false }
            DrawerItem("Settings", Icons.Default.Settings, screen == RScreen.SETTINGS) { go(RScreen.SETTINGS); drawerOpen = false }
            Spacer(Modifier.weight(1f)); HorizontalDivider(); Column(Modifier.padding(18.dp)) {
                Text("Razeen Secure Solution", fontWeight = FontWeight.Bold); Text("RSS Data Recovery", style = MaterialTheme.typography.labelSmall); Spacer(Modifier.height(10.dp))
                ContactItem(Icons.Default.Phone, "+94 77 115 5504") { dial(context, "+94771155504") }
                ContactItem(Icons.Default.Email, "rsscctvsolution@gmail.com") { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:rsscctvsolution@gmail.com"))) }
                ContactItem(Icons.Default.Language, "www.rsscctvsolution.eu.cc") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.rsscctvsolution.eu.cc"))) }
            }
        }
    }) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(screen.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) }, navigationIcon = { if (screen == RScreen.HOME) IconButton({ drawerOpen = true }) { Icon(Icons.Default.Menu, "Menu") } }, actions = { if (screen != RScreen.SETTINGS) IconButton({ go(RScreen.SETTINGS) }) { Icon(Icons.Default.Settings, "Settings") } }) },
            bottomBar = { NavigationBar { NavigationBarItem(screen == RScreen.HOME, { home() }, { Icon(Icons.Default.Home, null) }, label = { Text("Home") }); NavigationBarItem(screen == RScreen.SCAN, { mode = RMode.QUICK; category = null; go(RScreen.SCAN) }, { Icon(Icons.Default.FlashOn, null) }, label = { Text("Recover") }); NavigationBarItem(screen == RScreen.RESULTS, { go(RScreen.RESULTS) }, { Icon(Icons.Default.Folder, null) }, label = { Text("Results") }); NavigationBarItem(screen == RScreen.PREMIUM, { go(RScreen.PREMIUM) }, { Icon(Icons.Default.WorkspacePremium, null) }, label = { Text("Premium") }); NavigationBarItem(screen == RScreen.SETTINGS, { go(RScreen.SETTINGS) }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") }) } }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (screen) {
                    RScreen.HOME -> HomeV2({ mode = RMode.QUICK; category = null; go(RScreen.SCAN) }, { mode = RMode.DEEP; category = null; go(RScreen.SCAN) }, { category = it; mode = RMode.QUICK; go(RScreen.SCAN) }, { go(RScreen.PREMIUM) })
                    RScreen.SCAN -> ScanV2(mode, category) { go(RScreen.RESULTS) }
                    RScreen.RESULTS -> ResultsV2 { go(RScreen.PREMIUM) }
                    RScreen.PREMIUM -> PremiumV2()
                    RScreen.SETTINGS -> SettingsV2(dark, setDark, theme, setTheme)
                }
            }
        }
    }
}

@Composable private fun DrawerItem(title: String, icon: ImageVector, selected: Boolean, click: () -> Unit) { NavigationDrawerItem(label = { Text(title) }, selected = selected, onClick = click, icon = { Icon(icon, null) }, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) }
@Composable private fun ContactItem(icon: ImageVector, text: String, click: () -> Unit) { Row(Modifier.fillMaxWidth().clickable { click() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text(text, style = MaterialTheme.typography.bodySmall) } }

@Composable private fun HomeV2(quick: () -> Unit, deep: () -> Unit, category: (RCategory) -> Unit, premium: () -> Unit) {
    val slides = listOf("Premium Recovery" to "Free scan and preview. Premium unlocks saving.", "Deep Recovery" to "Full storage scan for harder-to-find files.", "RSS CLOUD SYNC" to "RSS cloud synchronization project.", "RSS Ai Assistant" to "RSS Android AI assistant project.", "RSS INVOICE MAKER" to "Sales representative order workflow.", "RSS MONEY MANAGER" to "Personal finance and wallet project.", "RSS LAUNCHER" to "Custom Android launcher project.", "RSS DEVICE GUARDIAN" to "Device protection and optimization project.", "RSS CLIPBOARD" to "Lightweight clipboard and cloud backup project.")
    var index by remember { mutableIntStateOf(0) }; LaunchedEffect(Unit) { while (true) { delay(3000); index = (index + 1) % slides.size } }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Data Recovery", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Choose how you want to recover your files.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Card(Modifier.fillMaxWidth().clickable { premium() }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(20.dp)) { Text(slides[index].first, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(slides[index].second); Spacer(Modifier.height(6.dp)); Text("RSS PROJECTS • rotating feature", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { ModeCardV2(RMode.QUICK, Icons.Default.FlashOn, quick, Modifier.weight(1f)); ModeCardV2(RMode.DEEP, Icons.Default.Search, deep, Modifier.weight(1f)) } }
        item { Text("Recover by category", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(RCategory.values().toList()) { c -> Card(Modifier.fillMaxWidth().clickable { category(c) }, shape = RoundedCornerShape(20.dp)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(c.icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp)); Text(c.title, Modifier.weight(1f), fontWeight = FontWeight.Bold); Icon(Icons.Default.ChevronRight, null) } } }
        item { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.WorkspacePremium, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Unlock Premium", fontWeight = FontWeight.Bold); Text("Original filename, metadata and best available quality", style = MaterialTheme.typography.bodySmall) }; Button(premium) { Text("Upgrade") } } } }
    }
}

@Composable private fun ModeCardV2(mode: RMode, icon: ImageVector, click: () -> Unit, modifier: Modifier) { Card(modifier.clickable { click() }, shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(17.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(9.dp)); Text(mode.title, fontWeight = FontWeight.Bold); Text(mode.subtitle, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall); Text(if (mode == RMode.QUICK) "Fast normal scan" else "Full storage scan", style = MaterialTheme.typography.bodySmall) } } }

@Composable private fun ScanV2(mode: RMode, category: RCategory?, results: () -> Unit) { Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(22.dp)) { Text(mode.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(mode.subtitle); Spacer(Modifier.height(10.dp)); Text(if (mode == RMode.QUICK) "Normal scan" else "Full storage scan"); category?.let { Text("Category: ${it.title}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } } }; Text("Free users can scan and preview recoverable files. Premium is required to save recovered files.", style = MaterialTheme.typography.bodySmall); Spacer(Modifier.weight(1f)); Button(results, Modifier.fillMaxWidth().height(54.dp)) { Text("Start ${mode.title}") } } }

@Composable private fun ResultsV2(upgrade: () -> Unit) { LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("Recoverable files", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Preview is free. Tap Recover to see the Premium offer.") }; items(listOf("recovered_file_001.jpg", "recovered_file_002.mp4", "recovered_file_003.pdf")) { name -> var showOffer by remember { mutableStateOf(false) }; Card(shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.InsertDriveFile, null); Spacer(Modifier.width(10.dp)); Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); Button({ showOffer = true }) { Text("Recover") } }; if (showOffer) { Spacer(Modifier.height(12.dp)); PremiumOfferV2(upgrade) { showOffer = false } } } } } } }

@Composable private fun PremiumOfferV2(upgrade: () -> Unit, continueFree: () -> Unit) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(16.dp)) { Text("Premium Recovery", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text("Premium keeps the original filename and available metadata and uses the best available image quality. It also unlocks full Deep Recovery saving.", style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(12.dp)); Button(upgrade, Modifier.fillMaxWidth()) { Text("Upgrade to Premium") }; TextButton(continueFree, Modifier.align(Alignment.CenterHorizontally)) { Text("Continue free") } } } }

@Composable private fun PremiumV2() { LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(24.dp)) { Icon(Icons.Default.WorkspacePremium, null, Modifier.size(44.dp)); Text("Premium Recovery", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Unlock full recovery and saving."); Spacer(Modifier.height(14.dp)); Button({}, Modifier.fillMaxWidth()) { Text("Upgrade to Premium") } } } }; item { FeatureRow("Quick Recovery", "Scan and preview on Free; saving unlocked by Premium."); FeatureRow("Deep Recovery", "Full scan and Premium saving."); FeatureRow("Original filename", "Preserved when technically available."); FeatureRow("Metadata", "Available original metadata preserved by Premium."); FeatureRow("Image quality", "Best available quality with Premium."); FeatureRow("Recovery folder", "Saved files use the RSS Data Recovery folder.") } } }
@Composable private fun FeatureRow(title: String, text: String) { Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column { Text(title, fontWeight = FontWeight.Bold); Text(text, style = MaterialTheme.typography.bodySmall) } } } }

@Composable private fun SettingsV2(dark: Boolean, setDark: (Boolean) -> Unit, theme: Int, setTheme: (Int) -> Unit) { LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(dark, { setDark(true) }, label = { Text("Dark") }); FilterChip(!dark, { setDark(false) }, label = { Text("Light") }) } }; item { Text("App color theme", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (0..3).forEach { i -> FilterChip(theme == i, { setTheme(i) }, label = { Text(listOf("Gold", "Violet", "Teal", "Rose")[i]) }) } } }; item { Text("Contact", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("rsscctvsolution@gmail.com"); Text("www.rsscctvsolution.eu.cc") } } }

fun dial(context: android.content.Context, number: String) { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
