package com.riyaz.rssdatarecovery

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private enum class Page { HOME, RECOVERY, RESULTS, PREMIUM, HISTORY, SETTINGS }
private enum class Mode { QUICK, DEEP }
private enum class Category { IMAGE, AUDIO, VIDEO, FILES, DOCUMENTS }
private data class FoundFile(val name: String, val size: Long, val uri: Uri, val category: Category, val modified: Long)

private val palettes = listOf(
    listOf(Color(0xFFB7791F), Color(0xFFF6D365)),
    listOf(Color(0xFF1677FF), Color(0xFF67D5FF)),
    listOf(Color(0xFF0E9F6E), Color(0xFF65D6A6)),
    listOf(Color(0xFF8B5CF6), Color(0xFFE0B7FF))
)

@Composable
fun RecoveryAppV3() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("rss_recovery", Context.MODE_PRIVATE) }
    var registered by remember { mutableStateOf(prefs.getBoolean("registered", false)) }
    var welcomed by remember { mutableStateOf(prefs.getBoolean("welcome_done", false)) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    var themeIndex by remember { mutableIntStateOf(prefs.getInt("theme", 0).coerceIn(0, 3)) }
    val palette = palettes[themeIndex]
    val scheme = if (dark) darkColorScheme(primary = palette[0], secondary = palette[1]) else lightColorScheme(primary = palette[0], secondary = palette[1])
    MaterialTheme(colorScheme = scheme) {
        when {
            !registered -> RegistrationScreen { name, email ->
                prefs.edit().putBoolean("registered", true).putString("name", name).putString("email", email).apply()
                registered = true
            }
            !welcomed -> WelcomeScreen(prefs.getString("name", "USER") ?: "USER") {
                prefs.edit().putBoolean("welcome_done", true).apply()
                welcomed = true
            }
            else -> RecoveryMain(prefs, dark, { dark = it; prefs.edit().putBoolean("dark", it).apply() }, themeIndex, { themeIndex = it; prefs.edit().putInt("theme", it).apply() })
        }
    }
}

@Composable private fun AnimatedBackdrop() { Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF07111F), Color(0xFF17304A), Color(0xFF090D16))))) }

@Composable private fun RegistrationScreen(done: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize()) {
        AnimatedBackdrop()
        Card(Modifier.fillMaxWidth().padding(22.dp).align(Alignment.Center), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(Color.White.copy(alpha = .13f)), elevation = CardDefaults.cardElevation(12.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(painterResource(R.drawable.rss_data_recovery_logo), "RSS Data Recovery", Modifier.size(86.dp).align(Alignment.CenterHorizontally))
                Text("RSS DATA RECOVERY", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("CREATE YOUR PROFILE", color = Color.White, fontWeight = FontWeight.Bold)
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("FULL NAME") }, singleLine = true)
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("EMAIL ADDRESS") }, singleLine = true)
                Button({ done(name.trim(), email.trim()) }, Modifier.fillMaxWidth(), enabled = name.trim().length > 1 && email.contains("@")) { Text("CONTINUE") }
            }
        }
    }
}

@Composable private fun WelcomeScreen(name: String, done: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AnimatedBackdrop()
        Card(Modifier.fillMaxWidth().padding(22.dp).align(Alignment.Center), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(Color.White.copy(alpha = .13f)), elevation = CardDefaults.cardElevation(12.dp)) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(painterResource(R.drawable.rss_data_recovery_logo), "RSS Data Recovery", Modifier.size(100.dp))
                Text("CONGRATULATIONS 👏🎉", color = Color(0xFFFFD166), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("WELCOME, ${name.uppercase()}!", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("YOUR RECOVERY SPACE IS READY.", color = Color.White, fontWeight = FontWeight.SemiBold)
                Button(done, Modifier.fillMaxWidth()) { Text("GET STARTED") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun RecoveryMain(prefs: SharedPreferences, dark: Boolean, setDark: (Boolean) -> Unit, theme: Int, setTheme: (Int) -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(Page.HOME) }
    var mode by remember { mutableStateOf(Mode.QUICK) }
    var category by remember { mutableStateOf<Category?>(null) }
    var drawerOpen by remember { mutableStateOf(false) }
    var files by remember { mutableStateOf(emptyList<FoundFile>()) }
    var scanning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var count by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val name = prefs.getString("name", "USER") ?: "USER"
    val email = prefs.getString("email", "") ?: ""
    val drawerState = rememberDrawerState(if (drawerOpen) DrawerValue.Open else DrawerValue.Closed)
    fun go(target: Page) { page = target; drawerOpen = false }
    BackHandler(enabled = drawerOpen) { drawerOpen = false }
    LaunchedEffect(drawerOpen) { if (drawerOpen) drawerState.open() else drawerState.close() }
    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
        ModalDrawerSheet {
            Column(Modifier.fillMaxHeight().padding(14.dp)) {
                Image(painterResource(R.drawable.rss_data_recovery_logo), "RSS Data Recovery", Modifier.size(70.dp).align(Alignment.CenterHorizontally))
                Text("WELCOME, ${name.uppercase()}", Modifier.padding(top = 6.dp), fontWeight = FontWeight.ExtraBold)
                Text(email, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                DrawerItem("Premium Upgrade", Icons.Default.Star, Color(0xFFFFB703)) { go(Page.PREMIUM) }
                DrawerItem("Home", Icons.Default.Home, Color(0xFF2E86DE)) { go(Page.HOME) }
                DrawerItem("Recovery", Icons.Default.Restore, Color(0xFFE67E22)) { mode = Mode.QUICK; category = null; go(Page.RECOVERY) }
                DrawerItem("Results", Icons.Default.Folder, Color(0xFF16A085)) { go(Page.RESULTS) }
                DrawerItem("History", Icons.Default.History, Color(0xFF8E44AD)) { go(Page.HISTORY) }
                DrawerItem("Settings", Icons.Default.Settings, Color(0xFF5C677D)) { go(Page.SETTINGS) }
                Spacer(Modifier.weight(1f))
                HorizontalDivider()
                Text("RAZEEN SECURE SOLUTION", Modifier.padding(top = 10.dp), fontWeight = FontWeight.Bold)
                ContactItem("077 115 5504", Icons.Default.Phone, Color(0xFF27AE60)) { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+94771155504"))) }
                ContactItem("rsscctvsolution@gmail.com", Icons.Default.Email, Color(0xFF2980B9)) { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:rsscctvsolution@gmail.com"))) }
                ContactItem("www.rsscctvsolution.eu.cc", Icons.Default.Language, Color(0xFF8E44AD)) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.rsscctvsolution.eu.cc"))) }
            }
        }
    }) {
        Scaffold(topBar = { TopAppBar(title = { Text(pageTitle(page, mode)) }, navigationIcon = { IconButton({ drawerOpen = true }) { Icon(Icons.Default.Menu, "Menu") } }) }, bottomBar = { NavigationBar(Modifier.shadow(5.dp)) {
            NavItem(Page.HOME, "Home", Icons.Default.Home, Color(0xFF2E86DE), page) { go(Page.HOME) }
            NavItem(Page.RECOVERY, "Recover", Icons.Default.Restore, Color(0xFFE67E22), page) { mode = Mode.QUICK; go(Page.RECOVERY) }
            NavItem(Page.RESULTS, "Results", Icons.Default.Folder, Color(0xFF16A085), page) { go(Page.RESULTS) }
            NavItem(Page.SETTINGS, "Settings", Icons.Default.Settings, Color(0xFF8E44AD), page) { go(Page.SETTINGS) }
        } }) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (page) {
                    Page.HOME -> HomeScreen({ mode = Mode.QUICK; category = null; go(Page.RECOVERY) }, { mode = Mode.DEEP; category = null; go(Page.RECOVERY) }, { go(Page.HISTORY) }) { category = it; mode = Mode.QUICK; go(Page.RECOVERY) }
                    Page.RECOVERY -> RecoveryScreen(mode, category, scanning, progress, count, { progress = it }, { scanning = it }, { files = it; count = it.size; go(Page.RESULTS) }, scope, prefs, notificationPermission)
                    Page.RESULTS -> ResultsScreen(files, prefs.getBoolean("premium", false)) { go(Page.PREMIUM) }
                    Page.PREMIUM -> PremiumScreen(prefs.getBoolean("premium", false))
                    Page.HISTORY -> HistoryScreen(prefs)
                    Page.SETTINGS -> SettingsScreen(prefs, dark, setDark, theme, setTheme)
                }
            }
        }
    }
}

@Composable private fun DrawerItem(text: String, icon: ImageVector, color: Color, onClick: () -> Unit) { NavigationDrawerItem({ Text(text) }, false, onClick, icon = { Icon(icon, null, tint = color) }, modifier = Modifier.padding(vertical = 2.dp)) }
@Composable private fun ContactItem(text: String, icon: ImageVector, color: Color, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(19.dp), tint = color); Spacer(Modifier.width(8.dp)); Text(text, style = MaterialTheme.typography.bodySmall) } }
@Composable private fun NavItem(page: Page, text: String, icon: ImageVector, color: Color, current: Page, onClick: () -> Unit) { NavigationBarItem(current == page, onClick, icon = { Icon(icon, null, tint = color) }, label = { Text(text) }) }
private fun pageTitle(page: Page, mode: Mode) = when (page) { Page.HOME -> "Home"; Page.RECOVERY -> if (mode == Mode.QUICK) "Quick Recovery" else "Deep Recovery"; Page.RESULTS -> "Results"; Page.PREMIUM -> "Premium Upgrade"; Page.HISTORY -> "History"; Page.SETTINGS -> "Settings" }

@Composable private fun HomeScreen(quick: () -> Unit, deep: () -> Unit, history: () -> Unit, category: (Category) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("RECOVER YOUR FILES", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold); Text("SCAN, PREVIEW AND RECOVER SAFELY", style = MaterialTheme.typography.bodySmall) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { ActionCard("Quick Recovery", Icons.Default.FlashOn, Color(0xFFE67E22), quick, Modifier.weight(1f)); ActionCard("Deep Recovery", Icons.Default.Search, Color(0xFF8E44AD), deep, Modifier.weight(1f)) } }
        item { Text("RECOVERY BY CATEGORY", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold) }
        item { LazyVerticalGrid(GridCells.Fixed(2), Modifier.height(310.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), userScrollEnabled = false) { items(Category.values().toList()) { c -> val info = categoryInfo(c); ActionCard(info.first, info.second, info.third, { category(c) }, Modifier.fillMaxWidth()) } } }
        item { OutlinedButton(history, Modifier.fillMaxWidth()) { Icon(Icons.Default.History, null); Spacer(Modifier.width(6.dp)); Text("RECOVERY HISTORY") } }
    }
}

private fun categoryInfo(c: Category) = when (c) { Category.IMAGE -> Triple("Images", Icons.Default.Image, Color(0xFF27AE60)); Category.AUDIO -> Triple("Audio", Icons.Default.MusicNote, Color(0xFF2980B9)); Category.VIDEO -> Triple("Video", Icons.Default.VideoLibrary, Color(0xFFE74C3C)); Category.FILES -> Triple("Files", Icons.Default.InsertDriveFile, Color(0xFFF39C12)); Category.DOCUMENTS -> Triple("Documents", Icons.Default.Description, Color(0xFF8E44AD)) }
@Composable private fun ActionCard(text: String, icon: ImageVector, color: Color, onClick: () -> Unit, modifier: Modifier) { Card(modifier.shadow(3.dp, RoundedCornerShape(17.dp)).clickable(onClick = onClick), shape = RoundedCornerShape(17.dp), elevation = CardDefaults.cardElevation(2.dp)) { Column(Modifier.padding(13.dp)) { Icon(icon, null, Modifier.size(30.dp), tint = color); Spacer(Modifier.height(6.dp)); Text(text, fontWeight = FontWeight.Bold) } } }

@Composable private fun RecoveryScreen(mode: Mode, category: Category?, scanning: Boolean, progress: Float, count: Int, setProgress: (Float) -> Unit, setScanning: (Boolean) -> Unit, done: (List<FoundFile>) -> Unit, scope: kotlinx.coroutines.CoroutineScope, prefs: SharedPreferences, notificationPermission: androidx.activity.result.ActivityResultLauncher<String>) {
    val context = LocalContext.current
    var paused by remember { mutableStateOf(false) }
    val readPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Card(Modifier.shadow(3.dp, RoundedCornerShape(18.dp))) { Column(Modifier.padding(16.dp)) { Text(if (mode == Mode.QUICK) "QUICK RECOVERY" else "DEEP RECOVERY", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold); category?.let { Text(categoryInfo(it).first.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } } } }
        item { Card(Modifier.shadow(3.dp, RoundedCornerShape(18.dp))) { Column(Modifier.padding(16.dp)) {
            if (scanning) { LinearProgressIndicator({ progress }, Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp)); Text("${(progress * 100).toInt()}% • $count FILES"); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ paused = !paused }) { Text(if (paused) "RESUME" else "PAUSE") }; OutlinedButton({ paused = false; setScanning(false) }) { Text("CANCEL") } } }
            else { Text("READY TO SCAN", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Button({
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                if (Build.VERSION.SDK_INT >= 33) readPermission.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)) else readPermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                setScanning(true); setProgress(0f); scope.launch { val found = queryFiles(context, category); for (step in 1..24) { while (paused) delay(100); delay(if (mode == Mode.DEEP) 55 else 30); setProgress(step / 24f) }; prefs.edit().putString("last_scan", DateFormat.getDateTimeInstance().format(Date())).putInt("last_count", found.size).apply(); setScanning(false); done(found) }
            }, Modifier.fillMaxWidth()) { Text("START SCAN") } }
        } } }
        item { Text(if (mode == Mode.DEEP) "FREE USERS CAN SCAN AND PREVIEW FILES. RECOVERY REQUIRES PREMIUM." else "FREE USERS CAN RECOVER IMAGES ONLY WITH LIMITED QUALITY AND NO ORIGINAL METADATA.", style = MaterialTheme.typography.bodySmall) }
    }
}

private suspend fun queryFiles(context: Context, category: Category?): List<FoundFile> = withContext(Dispatchers.IO) {
    val result = mutableListOf<FoundFile>(); val collection = MediaStore.Files.getContentUri("external")
    val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.DATE_MODIFIED, MediaStore.Files.FileColumns.MIME_TYPE)
    context.contentResolver.query(collection, projection, "${MediaStore.Files.FileColumns.SIZE}>0", null, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { cursor ->
        val id = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID); val name = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME); val size = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE); val modified = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED); val mime = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
        while (cursor.moveToNext() && result.size < 500) { val type = cursor.getString(mime) ?: ""; val kind = when { type.startsWith("image/") -> Category.IMAGE; type.startsWith("audio/") -> Category.AUDIO; type.startsWith("video/") -> Category.VIDEO; type.contains("pdf") || type.contains("document") || type.contains("text") -> Category.DOCUMENTS; else -> Category.FILES }; if (category == null || category == kind) result += FoundFile(cursor.getString(name) ?: "Unknown", cursor.getLong(size), Uri.withAppendedPath(collection, cursor.getLong(id).toString()), kind, cursor.getLong(modified)) }
    }; result
}

@Composable private fun ResultsScreen(files: List<FoundFile>, premium: Boolean, upgrade: () -> Unit) { var query by remember { mutableStateOf("") }; val visible = files.filter { it.name.contains(query, true) }; Column(Modifier.fillMaxSize().padding(16.dp)) { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("SEARCH FILES") }, singleLine = true); Spacer(Modifier.height(10.dp)); if (visible.isEmpty()) Text("NO FILES FOUND", fontWeight = FontWeight.Bold) else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(visible) { file -> Card(Modifier.fillMaxWidth().shadow(2.dp)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(categoryInfo(file.category).second, null, tint = categoryInfo(file.category).third); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(file.name, fontWeight = FontWeight.Bold); Text("${file.size / 1024} KB", style = MaterialTheme.typography.bodySmall) }; Button({ if (!premium) upgrade() }, enabled = true) { Text(if (premium) "RECOVER" else "PREMIUM") } } } } } } }

@Composable private fun PremiumScreen(premium: Boolean) { LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("PREMIUM UPGRADE", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold) }; item { Text(if (premium) "PREMIUM IS ACTIVE." else "UNLOCK FULL RECOVERY AND PREMIUM PROTECTION.") }; item { listOf("DEEP RECOVERY", "FULL QUALITY RECOVERY", "ORIGINAL FILE NAMES", "METADATA PRESERVATION", "MULTI-FILE RECOVERY", "ADVANCED FILTERS", "APP LOCK WITH BIOMETRIC SUPPORT").forEach { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF27AE60)); Spacer(Modifier.width(8.dp)); Text(it) } } } }
@Composable private fun HistoryScreen(prefs: SharedPreferences) { Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("RECOVERY HISTORY", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold); Text("LAST SCAN: ${prefs.getString("last_scan", "NO SCAN YET")}"); Text("FILES FOUND: ${prefs.getInt("last_count", 0)}") } }
@Composable private fun SettingsScreen(prefs: SharedPreferences, dark: Boolean, setDark: (Boolean) -> Unit, theme: Int, setTheme: (Int) -> Unit) { LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("SETTINGS", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold) }; item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("APPEARANCE: DARK", Modifier.weight(1f)); Switch(dark, setDark) } }; item { Text("COLOR THEME", fontWeight = FontWeight.Bold) }; item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { palettes.indices.forEach { index -> FilterChip(selected = theme == index, onClick = { setTheme(index) }, label = { Text("THEME ${index + 1}") }) } } }; item { Text("HAPTIC FEEDBACK • NOTIFICATIONS • SCAN OPTIONS • STORAGE STATUS • PRIVACY & SAFETY") } } }
