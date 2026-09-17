package com.riyaz.rssdatarecovery

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Page { HOME, SCAN, RESULTS, PREMIUM, SETTINGS, HISTORY }
private enum class Mode { QUICK, DEEP }
private data class FoundFile(val name: String, val size: Long, val uri: Uri)

private val themeColors = listOf(Color(0xFF9A6B08), Color(0xFF1769AA), Color(0xFF147D64), Color(0xFF7046A8))

@Composable
fun RecoveryAppV3() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("rss_recovery", 0) }
    var registered by remember { mutableStateOf(prefs.getBoolean("registered", false)) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    var theme by remember { mutableIntStateOf(prefs.getInt("theme", 0).coerceIn(0, 3)) }
    val primary = themeColors[theme]
    MaterialTheme(if (dark) darkColorScheme(primary = primary) else lightColorScheme(primary = primary)) {
        if (!registered) {
            RegistrationScreen { name, email ->
                prefs.edit().putBoolean("registered", true).putString("name", name).putString("email", email).apply()
                registered = true
            }
        } else {
            RecoveryMain(prefs, dark, { dark = it; prefs.edit().putBoolean("dark", it).apply() }, theme, { theme = it; prefs.edit().putInt("theme", it).apply() })
        }
    }
}

@Composable
private fun RegistrationScreen(done: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Card(Modifier.fillMaxWidth().padding(24.dp), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Security, null, Modifier.size(58.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("RSS Data Recovery", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Create your profile", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Full name") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email address") }, singleLine = true)
                Spacer(Modifier.height(20.dp))
                Button({ done(name.trim(), email.trim()) }, Modifier.fillMaxWidth().height(52.dp), enabled = name.trim().length > 1 && email.contains("@")) { Text("Continue") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecoveryMain(prefs: android.content.SharedPreferences, dark: Boolean, setDark: (Boolean) -> Unit, theme: Int, setTheme: (Int) -> Unit) {
    var page by remember { mutableStateOf(Page.HOME) }
    var drawer by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(Mode.QUICK) }
    var files by remember { mutableStateOf(emptyList<FoundFile>()) }
    var scanning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun back() { if (drawer) drawer = false else page = Page.HOME }
    BackHandler(page != Page.HOME || drawer) { back() }
    val drawerState = rememberDrawerState(if (drawer) DrawerValue.Open else DrawerValue.Closed)
    LaunchedEffect(drawer) { if (drawer) drawerState.open() else drawerState.close() }
    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
        ModalDrawerSheet {
            Spacer(Modifier.height(18.dp))
            Text("RSS Data Recovery", Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            DrawerItem("Home", Icons.Default.Home) { page = Page.HOME; drawer = false }
            DrawerItem("Recovery", Icons.Default.Restore) { page = Page.SCAN; drawer = false }
            DrawerItem("Results", Icons.Default.Folder) { page = Page.RESULTS; drawer = false }
            DrawerItem("Recovery history", Icons.Default.History) { page = Page.HISTORY; drawer = false }
            DrawerItem("Premium", Icons.Default.Star) { page = Page.PREMIUM; drawer = false }
            DrawerItem("Settings", Icons.Default.Settings) { page = Page.SETTINGS; drawer = false }
            Spacer(Modifier.weight(1f)); HorizontalDivider()
            ContactItem("077 115 5504", Icons.Default.Phone) { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+94771155504"))) }
            ContactItem("rsscctvsolution@gmail.com", Icons.Default.Email) { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:rsscctvsolution@gmail.com"))) }
            ContactItem("www.rsscctvsolution.eu.cc", Icons.Default.Language) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.rsscctvsolution.eu.cc"))) }
            Spacer(Modifier.height(16.dp))
        }
    }) {
        Scaffold(topBar = {
            TopAppBar(title = { Text(pageTitle(page, mode)) }, navigationIcon = {
                IconButton({ if (page == Page.HOME) drawer = true else back() }) { Icon(if (page == Page.HOME) Icons.Default.Menu else Icons.Default.ArrowBack, null) }
            })
        }, bottomBar = {
            NavigationBar {
                NavigationBarItem(page == Page.HOME, { page = Page.HOME }, { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                NavigationBarItem(page == Page.SCAN, { page = Page.SCAN }, { Icon(Icons.Default.Restore, null) }, label = { Text("Recover") })
                NavigationBarItem(page == Page.RESULTS, { page = Page.RESULTS }, { Icon(Icons.Default.Folder, null) }, label = { Text("Results") })
                NavigationBarItem(page == Page.SETTINGS, { page = Page.SETTINGS }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
            }
        }) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (page) {
                    Page.HOME -> HomeScreen({ mode = Mode.QUICK; page = Page.SCAN }, { mode = Mode.DEEP; page = Page.SCAN }, { page = Page.HISTORY })
                    Page.SCAN -> ScanScreen(mode, scanning, progress, { progress = it }, { scanning = it }, { result -> files = result; page = Page.RESULTS }, scope)
                    Page.RESULTS -> ResultsScreen(files) { page = Page.PREMIUM }
                    Page.PREMIUM -> PremiumScreen()
                    Page.HISTORY -> HistoryScreen(prefs)
                    Page.SETTINGS -> SettingsScreen(dark, setDark, theme, setTheme)
                }
            }
        }
    }
}

private fun pageTitle(page: Page, mode: Mode) = when (page) { Page.HOME -> "Home"; Page.SCAN -> if (mode == Mode.QUICK) "Quick Recovery" else "Deep Recovery"; Page.RESULTS -> "Results"; Page.PREMIUM -> "Premium"; Page.SETTINGS -> "Settings"; Page.HISTORY -> "Recovery history" }

@Composable private fun DrawerItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit) { NavigationDrawerItem({ Text(text) }, false, click, icon = { Icon(icon, null) }, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) }
@Composable private fun ContactItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit) { Row(Modifier.fillMaxWidth().clickable { click() }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text(text, style = MaterialTheme.typography.bodySmall) } }

@Composable private fun HomeScreen(quick: () -> Unit, deep: () -> Unit, history: () -> Unit) { LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { Text("Recover files", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Scan accessible device storage and preview files before recovery.", color = MaterialTheme.colorScheme.onSurfaceVariant) }; item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text("Storage", fontWeight = FontWeight.Bold); Text("Internal storage ready", style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(10.dp)); LinearProgressIndicator({ 0.62f }, Modifier.fillMaxWidth()) } } }; item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { ActionCard("Quick Recovery", Icons.Default.FlashOn, quick, Modifier.weight(1f)); ActionCard("Deep Recovery", Icons.Default.Search, deep, Modifier.weight(1f)) } }; item { OutlinedButton(history, Modifier.fillMaxWidth()) { Icon(Icons.Default.History, null); Spacer(Modifier.width(6.dp)); Text("Recovery history") } }; item { Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Lock, null); Spacer(Modifier.width(10.dp)); Column { Text("Free scan", fontWeight = FontWeight.Bold); Text("Scanning and preview are free. Recovery requires Premium.", style = MaterialTheme.typography.bodySmall) } } } } } }
@Composable private fun ActionCard(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit, modifier: Modifier) { Card(modifier.clickable { click() }) { Column(Modifier.padding(18.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, Modifier.size(30.dp)); Spacer(Modifier.height(10.dp)); Text(text, fontWeight = FontWeight.Bold) } } }

@Composable private fun ScanScreen(mode: Mode, scanning: Boolean, progress: Float, setProgress: (Float) -> Unit, setScanning: (Boolean) -> Unit, done: (List<FoundFile>) -> Unit, scope: kotlinx.coroutines.CoroutineScope) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text(if (mode == Mode.QUICK) "Quick Recovery" else "Deep Recovery", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Accessible files only. Deleted-file recovery is subject to Android storage access.", style = MaterialTheme.typography.bodySmall) } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { if (scanning) { LinearProgressIndicator({ progress }, Modifier.fillMaxWidth()); Spacer(Modifier.height(10.dp)); Text("Scanning ${(progress * 100).toInt()}%") } else { Text("Ready to scan", fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Button({ setScanning(true); setProgress(0f); scope.launch { val result = withContext(Dispatchers.IO) { queryMedia(context) }; for (i in 1..20) { kotlinx.coroutines.delay(if (mode == Mode.QUICK) 25 else 45); setProgress(i / 20f) }; setScanning(false); done(result) } }, Modifier.fillMaxWidth()) { Text("Start scan") } } } } }
        item { Text("Deep Recovery lets free users scan and preview candidates. Saving recovered files is Premium.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private suspend fun queryMedia(context: android.content.Context): List<FoundFile> = withContext(Dispatchers.IO) {
    val list = mutableListOf<FoundFile>()
    val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.SIZE)
    context.contentResolver.query(MediaStore.Files.getContentUri("external"), projection, null, null, MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC")?.use { c ->
        val id = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID); val name = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME); val size = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
        while (c.moveToNext() && list.size < 200) { val uri = Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), c.getLong(id).toString()); list += FoundFile(c.getString(name) ?: "Unnamed file", c.getLong(size).coerceAtLeast(0), uri) }
    }
    list
}

@Composable private fun ResultsScreen(files: List<FoundFile>, premium: () -> Unit) { LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { item { Text("${files.size} files found", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Preview only. Recovery is Premium.", style = MaterialTheme.typography.bodySmall) }; if (files.isEmpty()) item { Text("No accessible files found. Try another scan.") }; items(files) { file -> Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.InsertDriveFile, null); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(file.name, fontWeight = FontWeight.Medium); Text(formatBytes(file.size), style = MaterialTheme.typography.bodySmall) }; TextButton(premium) { Text("Recover") } } } } } }
@Composable private fun PremiumScreen() { Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Star, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(16.dp)); Text("Premium Recovery", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Text("Unlock saving recovered files, advanced recovery tools and protection features.", style = MaterialTheme.typography.bodyLarge); Spacer(Modifier.height(24.dp)); Button({}, Modifier.fillMaxWidth()) { Text("Upgrade to Premium") } } }
@Composable private fun HistoryScreen(prefs: android.content.SharedPreferences) { Column(Modifier.fillMaxSize().padding(24.dp)) { Text("Recovery history", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Text("Your scan history will appear here after scans.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun SettingsScreen(dark: Boolean, setDark: (Boolean) -> Unit, theme: Int, setTheme: (Int) -> Unit) { LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }; item { Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Text("Dark mode", Modifier.weight(1f)); Switch(dark, setDark) } } }; item { Text("App color theme", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }; item { themeColors.forEachIndexed { index, color -> ListItem(headlineContent = { Text(listOf("Royal Gold", "Ocean", "Emerald", "Plum")[index]) }, leadingContent = { Box(Modifier.size(28.dp).background(color, RoundedCornerShape(8.dp))) }, trailingContent = { RadioButton(theme == index) { setTheme(index) } }, modifier = Modifier.clickable { setTheme(index) }) } } } }
private fun formatBytes(bytes: Long): String = when { bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1048576.0); bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0); else -> "$bytes B" }
