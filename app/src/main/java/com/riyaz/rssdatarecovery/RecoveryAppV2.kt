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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private enum class RScreen { HOME, SCAN, RESULTS, PREMIUM, SETTINGS }
private enum class RMode(val title: String) { QUICK("Quick Recovery"), DEEP("Deep Recovery") }
private enum class RCategory(val title: String, val icon: ImageVector, val color: Color) {
    PHOTOS("Photos", Icons.Default.Image, Color(0xFFE85D75)),
    VIDEOS("Videos", Icons.Default.VideoLibrary, Color(0xFF4B82E8)),
    AUDIOS("Audio", Icons.Default.AudioFile, Color(0xFF8A63D2)),
    DOCUMENTS("Documents", Icons.Default.Description, Color(0xFFE09A32))
}
private data class AppTheme(val name: String, val primary: Color, val secondary: Color, val tertiary: Color, val surface: Color)
private val appThemes = listOf(
    AppTheme("Royal Gold", Color(0xFF9A6B08), Color(0xFF52627A), Color(0xFF9B4D65), Color(0xFFFFFBF4)),
    AppTheme("Ocean", Color(0xFF1769AA), Color(0xFF00695C), Color(0xFF7B4FA3), Color(0xFFF7FBFF)),
    AppTheme("Emerald", Color(0xFF147D64), Color(0xFF5C5A18), Color(0xFF8B4C69), Color(0xFFF7FCF9)),
    AppTheme("Plum", Color(0xFF7046A8), Color(0xFF276A8B), Color(0xFFB05A72), Color(0xFFFCF8FF))
)
private val cardShape = RoundedCornerShape(20.dp)
private fun Modifier.cardShadow() = shadow(3.dp, cardShape, clip = false)

@Composable
fun RecoveryAppV2() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("rss_recovery", 0) }
    var registered by remember { mutableStateOf(prefs.getBoolean("registered", false)) }
    var welcome by remember { mutableStateOf(false) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    var theme by remember { mutableIntStateOf(prefs.getInt("theme", 0).coerceIn(0, appThemes.lastIndex)) }
    var appLock by remember { mutableStateOf(prefs.getBoolean("app_lock", false) && prefs.getString("app_pin", null) != null) }
    var unlocked by remember { mutableStateOf(!appLock) }
    val t = appThemes[theme]
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = t.primary, secondary = t.secondary, tertiary = t.tertiary) else lightColorScheme(primary = t.primary, secondary = t.secondary, tertiary = t.tertiary, surface = t.surface)) {
        when {
            !registered -> RegistrationV2 { name, email ->
                prefs.edit().putBoolean("registered", true).putString("name", name).putString("email", email).apply()
                registered = true
                welcome = true
            }
            welcome -> WelcomeV2(prefs.getString("name", "Customer") ?: "Customer") { welcome = false }
            !unlocked -> LockScreen { pin -> if (pin == prefs.getString("app_pin", "")) unlocked = true }
            else -> RecoveryShellV2(dark, { dark = it; prefs.edit().putBoolean("dark", it).apply() }, theme, { theme = it; prefs.edit().putInt("theme", it).apply() }, appLock) { enabled ->
                appLock = enabled
                unlocked = true
                prefs.edit().putBoolean("app_lock", enabled).apply()
            }
        }
    }
}

@Composable private fun Brand() {
    Box(Modifier.size(62.dp).clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.primaryContainer), Alignment.Center) {
        Icon(painterResource(R.drawable.rss_splash_icon), "RSS Data Recovery", Modifier.size(48.dp))
    }
}

@Composable private fun RegistrationV2(done: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    val valid = name.trim().length > 1 && email.contains("@") && email.contains(".")
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Card(Modifier.fillMaxWidth().padding(22.dp).cardShadow(), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(25.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Brand()
                Spacer(Modifier.height(16.dp))
                Text("Create your profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(22.dp))
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Full name") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant, focusedLabelColor = MaterialTheme.colorScheme.primary))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email address") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant, focusedLabelColor = MaterialTheme.colorScheme.primary))
                Spacer(Modifier.height(18.dp))
                Button({ done(name.trim(), email.trim()) }, Modifier.fillMaxWidth().height(52.dp), enabled = valid) { Text("Continue") }
            }
        }
    }
}

@Composable private fun WelcomeV2(name: String, go: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CheckCircle, null, Modifier.size(78.dp), tint = Color(0xFF20A66A))
            Spacer(Modifier.height(20.dp))
            Text("Welcome, $name", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(25.dp))
            Card(Modifier.fillMaxWidth().cardShadow(), shape = cardShape) {
                Button(go, Modifier.fillMaxWidth().padding(20.dp).height(50.dp)) { Text("Start") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun RecoveryShellV2(
    dark: Boolean, setDark: (Boolean) -> Unit, theme: Int, setTheme: (Int) -> Unit,
    appLock: Boolean, setAppLock: (Boolean) -> Unit
) {
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
        ModalDrawerSheet(Modifier.width(310.dp)) {
            Spacer(Modifier.height(18.dp))
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Brand(); Spacer(Modifier.width(12.dp)); Text("RSS Data Recovery", fontWeight = FontWeight.Bold) }
            DrawerItem("Home", Icons.Default.Home, screen == RScreen.HOME) { home(); drawerOpen = false }
            DrawerItem("Recovery", Icons.Default.Restore, screen == RScreen.SCAN) { mode = RMode.QUICK; category = null; go(RScreen.SCAN); drawerOpen = false }
            DrawerItem("Results", Icons.Default.Folder, screen == RScreen.RESULTS) { go(RScreen.RESULTS); drawerOpen = false }
            DrawerItem("Premium", Icons.Default.WorkspacePremium, screen == RScreen.PREMIUM) { go(RScreen.PREMIUM); drawerOpen = false }
            DrawerItem("Settings", Icons.Default.Settings, screen == RScreen.SETTINGS) { go(RScreen.SETTINGS); drawerOpen = false }
            Spacer(Modifier.weight(1f))
            HorizontalDivider()
            Column(Modifier.padding(18.dp)) {
                ContactItem(Icons.Default.Phone, "+94 77 115 5504") { dial(context, "+94771155504") }
                ContactItem(Icons.Default.Email, "rsscctvsolution@gmail.com") { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:rsscctvsolution@gmail.com"))) }
                ContactItem(Icons.Default.Language, "www.rsscctvsolution.eu.cc") { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.rsscctvsolution.eu.cc"))) }
            }
        }
    }) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(when (screen) { RScreen.HOME -> "Home"; RScreen.SCAN -> mode.title; RScreen.RESULTS -> "Results"; RScreen.PREMIUM -> "Premium"; RScreen.SETTINGS -> "Settings" }) },
                    navigationIcon = { if (screen == RScreen.HOME) IconButton({ drawerOpen = true }) { Icon(Icons.Default.Menu, "Menu") } else IconButton({ back() }) { Icon(Icons.Default.ArrowBack, "Back") } },
                    actions = { if (screen != RScreen.SETTINGS) IconButton({ go(RScreen.SETTINGS) }) { Icon(Icons.Default.Settings, "Settings") } }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(screen == RScreen.HOME, { home() }, { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                    NavigationBarItem(screen == RScreen.SCAN, { mode = RMode.QUICK; category = null; go(RScreen.SCAN) }, { Icon(Icons.Default.Restore, null) }, label = { Text("Recover") })
                    NavigationBarItem(screen == RScreen.RESULTS, { go(RScreen.RESULTS) }, { Icon(Icons.Default.Folder, null) }, label = { Text("Results") })
                    NavigationBarItem(screen == RScreen.SETTINGS, { go(RScreen.SETTINGS) }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (screen) {
                    RScreen.HOME -> HomeV2({ mode = RMode.QUICK; category = null; go(RScreen.SCAN) }, { mode = RMode.DEEP; category = null; go(RScreen.SCAN) }, { category = it; mode = RMode.QUICK; go(RScreen.SCAN) })
                    RScreen.SCAN -> ScanV2(mode, category) { go(RScreen.RESULTS) }
                    RScreen.RESULTS -> ResultsV2 { go(RScreen.PREMIUM) }
                    RScreen.PREMIUM -> PremiumV2()
                    RScreen.SETTINGS -> SettingsV2(dark, setDark, theme, setTheme, appLock, setAppLock)
                }
            }
        }
    }
}

@Composable private fun DrawerItem(title: String, icon: ImageVector, selected: Boolean, click: () -> Unit) { NavigationDrawerItem(label = { Text(title) }, selected = selected, onClick = click, icon = { Icon(icon, null) }, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) }
@Composable private fun ContactItem(icon: ImageVector, text: String, click: () -> Unit) { Row(Modifier.fillMaxWidth().clickable { click() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) } }

@Composable private fun HomeV2(quick: () -> Unit, deep: () -> Unit, category: (RCategory) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item { Text("Recover files", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Choose a scan method or file type.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { ModeCard(RMode.QUICK, Icons.Default.FlashOn, Color(0xFFE4A11B), quick, Modifier.weight(1f)); ModeCard(RMode.DEEP, Icons.Default.Search, Color(0xFF4779E8), deep, Modifier.weight(1f)) } }
        item { Text("File types", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { RCategory.values().take(2).forEach { CategoryCard(it, category, Modifier.weight(1f)) } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { RCategory.values().drop(2).forEach { CategoryCard(it, category, Modifier.weight(1f)) } } }
        item { Card(Modifier.fillMaxWidth().cardShadow(), shape = cardShape) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Lock, null, tint = Color(0xFF7B57B5)); Spacer(Modifier.width(12.dp)); Column { Text("Free scan", fontWeight = FontWeight.Bold); Text("Preview files before recovery.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
    }
}

@Composable private fun ModeCard(mode: RMode, icon: ImageVector, color: Color, click: () -> Unit, modifier: Modifier) { Card(modifier.clickable { click() }.cardShadow(), shape = cardShape) { Column(Modifier.padding(17.dp)) { Box(Modifier.size(46.dp).clip(CircleShape).background(color.copy(alpha = .14f)), Alignment.Center) { Icon(icon, null, tint = color) }; Spacer(Modifier.height(10.dp)); Text(mode.title, fontWeight = FontWeight.Bold); Text(if (mode == RMode.QUICK) "Fast scan" else "Full scan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun CategoryCard(c: RCategory, click: (RCategory) -> Unit, modifier: Modifier) { Card(modifier.fillMaxWidth().height(120.dp).clickable { click(c) }.cardShadow(), shape = cardShape) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.Center) { Icon(c.icon, null, tint = c.color, Modifier.size(30.dp)); Spacer(Modifier.height(8.dp)); Text(c.title, fontWeight = FontWeight.SemiBold) } } }

@Composable private fun ScanV2(mode: RMode, category: RCategory?, results: () -> Unit) { Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Card(Modifier.fillMaxWidth().cardShadow(), shape = cardShape) { Column(Modifier.padding(22.dp)) { category?.let { Text(it.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = it.color) }; Text(if (mode == RMode.QUICK) "Scans recently deleted files." else "Scans storage for harder-to-find files.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }; Spacer(Modifier.weight(1f)); Button(results, Modifier.fillMaxWidth().height(54.dp)) { Text("Start") } } }

@Composable private fun ResultsV2(upgrade: () -> Unit) { LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("Found files", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Preview is available before recovery.", color = MaterialTheme.colorScheme.onSurfaceVariant) }; items(listOf("recovered_photo.jpg", "recovered_video.mp4", "recovered_document.pdf")) { name -> Card(Modifier.fillMaxWidth().cardShadow(), shape = cardShape) { Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.InsertDriveFile, null, tint = Color(0xFF4779E8)); Spacer(Modifier.width(10.dp)); Text(name, Modifier.weight(1f)); TextButton(upgrade) { Text("Recover") } } } } } }

@Composable private fun PremiumV2() { LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { Card(Modifier.fillMaxWidth().cardShadow(), shape = RoundedCornerShape(25.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(24.dp)) { Icon(Icons.Default.WorkspacePremium, null, Modifier.size(46.dp), tint = Color(0xFFE0A31B)); Text("Unlock recovery", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Save recovered files with Premium."); Spacer(Modifier.height(14.dp)); Button({}, Modifier.fillMaxWidth()) { Text("Upgrade") } } } }; item { listOf("Deep Recovery saving", "Original filename when available", "Metadata preservation", "Best available image quality", "Dedicated recovery folder").forEach { FeatureRow(it) } } } }
@Composable private fun FeatureRow(title: String) { Card(Modifier.fillMaxWidth().cardShadow(), shape = cardShape) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF20A66A)); Spacer(Modifier.width(12.dp)); Text(title, fontWeight = FontWeight.SemiBold) } } }

@Composable private fun SettingsV2(dark: Boolean, setDark: (Boolean) -> Unit, theme: Int, setTheme: (Int) -> Unit, appLock: Boolean, setAppLock: (Boolean) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("rss_recovery", 0) }
    var showPin by remember { mutableStateOf(false) }
    if (showPin) PinSetupDialog({ pin -> prefs.edit().putString("app_pin", pin).putBoolean("app_lock", true).apply(); setAppLock(true); showPin = false }, { showPin = false })
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SettingsSection("Appearance") { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(!dark, { setDark(false) }, label = { Text("Light") }); FilterChip(dark, { setDark(true) }, label = { Text("Dark") }) } } }
        item { SettingsSection("Color theme") { appThemes.forEachIndexed { i, t -> Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { setTheme(i) }, shape = cardShape, colors = CardDefaults.cardColors(containerColor = t.surface)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(28.dp).clip(CircleShape).background(t.primary)); Spacer(Modifier.width(12.dp)); Text(t.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); if (theme == i) Icon(Icons.Default.Check, null, tint = t.primary) } } } } }
        item { SettingsSection("Security") { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Lock, null, tint = Color(0xFF7B57B5)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("App Lock", fontWeight = FontWeight.Bold); Text(if (appLock) "PIN protection enabled" else "Protect the app with a PIN", style = MaterialTheme.typography.bodySmall) }; Switch(appLock, { if (it) showPin = true else { prefs.edit().remove("app_pin").putBoolean("app_lock", false).apply(); setAppLock(false) } }) } } }
        item { SettingsSection("Recovery") { SettingRow(Icons.Default.Storage, "Scan mode", "Quick and Deep"); SettingRow(Icons.Default.Folder, "Save location", "RSS Data Recovery folder") } }
        item { SettingsSection("Experience") { SettingRow(Icons.Default.Vibration, "Haptic feedback", "System touch feedback"); SettingRow(Icons.Default.Notifications, "Notifications", "Recovery progress") } }
        item { SettingsSection("Support") { SettingRow(Icons.Default.Email, "Support", "rsscctvsolution@gmail.com"); SettingRow(Icons.Default.Language, "Website", "www.rsscctvsolution.eu.cc") } }
    }
}

@Composable private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) { Column { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); content() } }
@Composable private fun SettingRow(icon: ImageVector, title: String, subtitle: String) { Card(Modifier.fillMaxWidth().cardShadow(), shape = cardShape) { Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }

@Composable private fun PinSetupDialog(done: (String) -> Unit, cancel: () -> Unit) { var pin by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = cancel, title = { Text("Set PIN") }, text = { OutlinedTextField(pin, { if (it.length <= 6 && it.all(Char::isDigit)) pin = it }, label = { Text("4–6 digits") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)) }, confirmButton = { TextButton({ done(pin) }, enabled = pin.length in 4..6) { Text("Save") } }, dismissButton = { TextButton(cancel) { Text("Cancel") } }) }
@Composable private fun LockScreen(unlock: (String) -> Unit) { var pin by remember { mutableStateOf("") }; Box(Modifier.fillMaxSize(), Alignment.Center) { Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Lock, null, Modifier.size(58.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(16.dp)); Text("Enter PIN", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(20.dp)); OutlinedTextField(pin, { if (it.length <= 6 && it.all(Char::isDigit)) pin = it }, label = { Text("PIN") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)); Spacer(Modifier.height(12.dp)); Button({ unlock(pin) }, enabled = pin.length >= 4, Modifier.fillMaxWidth().height(50.dp)) { Text("Unlock") } } } }
fun dial(context: android.content.Context, number: String) { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
