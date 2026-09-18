package com.riyaz.rssdatarecovery

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private enum class Page { HOME, SCAN, RESULTS, PREMIUM, SETTINGS, HISTORY }
private enum class Mode { QUICK, DEEP }
private enum class Category { IMAGE, AUDIO, VIDEO, FILES, DOCUMENTS }
private data class FoundFile(val name: String, val size: Long, val uri: Uri, val category: Category, val modified: Long)
private val palettes = listOf(listOf(Color(0xFFB7791F), Color(0xFFF6D365)), listOf(Color(0xFF1677FF), Color(0xFF67D5FF)), listOf(Color(0xFF0E9F6E), Color(0xFF65D6A6)), listOf(Color(0xFF8B5CF6), Color(0xFFE0B7FF)))

@Composable
fun RecoveryAppV3(appLocked: Boolean = false, onUnlock: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("rss_recovery", Context.MODE_PRIVATE) }
    var registered by remember { mutableStateOf(prefs.getBoolean("registered", false)) }
    var welcomed by remember { mutableStateOf(prefs.getBoolean("welcome_done", false)) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    var theme by remember { mutableIntStateOf(prefs.getInt("theme", 0).coerceIn(0, 3)) }
    val palette = palettes[theme]
    val scheme = if (dark) darkColorScheme(primary = palette[0], secondary = palette[1]) else lightColorScheme(primary = palette[0], secondary = palette[1])
    MaterialTheme(colorScheme = scheme) {
        if (appLocked) {
            LockScreen(onUnlock)
            return@MaterialTheme
        }
        AnimatedContent(targetState = registered to welcomed, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "entry") { state ->
            when {
                !state.first -> RegistrationScreen { name, email -> prefs.edit().putBoolean("registered", true).putString("name", name).putString("email", email).apply(); registered = true }
                !state.second -> WelcomeScreen(prefs.getString("name", "USER") ?: "USER") { prefs.edit().putBoolean("welcome_done", true).apply(); welcomed = true }
                else -> RecoveryMain(prefs, dark, { dark = it; prefs.edit().putBoolean("dark", it).apply() }, theme, { theme = it; prefs.edit().putInt("theme", it).apply() })
            }
        }
    }
}

@Composable private fun AnimatedBackdrop() {
    val t = rememberInfiniteTransition(label = "bg")
    val x by t.animateFloat(0f, 1f, infiniteRepeatable(tween(6500), RepeatMode.Reverse), label = "x")
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF07111F), Color(0xFF17304A), Color(0xFF090D16)), start = androidx.compose.ui.geometry.Offset(x * 900f, 0f), end = androidx.compose.ui.geometry.Offset(0f, 1500f))))
}

@Composable private fun LockScreen(onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AnimatedBackdrop()
        Card(Modifier.fillMaxWidth().padding(24.dp).align(Alignment.Center), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.14f)), elevation = CardDefaults.cardElevation(12.dp)) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Default.Lock, null, Modifier.size(58.dp), tint = Color(0xFFFFD166))
                Text("RSS DATA RECOVERY", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("APP LOCKED", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("USE YOUR BIOMETRIC TO CONTINUE", color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Medium)
                Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Fingerprint, null)
                    Spacer(Modifier.width(8.dp))
                    Text("UNLOCK")
                }
            }
        }
    }
}

@Composable private fun RegistrationScreen(done: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize()) {
        AnimatedBackdrop()
        Card(Modifier.fillMaxWidth().padding(22.dp).align(Alignment.Center), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .13f)), elevation = CardDefaults.cardElevation(12.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Default.Security, null, Modifier.size(52.dp).align(Alignment.CenterHorizontally), tint = Color(0xFFFFD166))
                Text("RSS DATA RECOVERY", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.rss_data_recovery_logo),
                    contentDescription = "RSS Data Recovery",
                    modifier = Modifier.size(78.dp)
                )
                Text("CREATE YOUR PROFILE", color = Color.White, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("FULL NAME", color = Color.White) }, singleLine = true, textStyle = LocalTextStyle.current.copy(color = Color.White), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
                OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("EMAIL ADDRESS", color = Color.White) }, singleLine = true, textStyle = LocalTextStyle.current.copy(color = Color.White), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                Button(onClick = { done(name.trim(), email.trim()) }, modifier = Modifier.fillMaxWidth(), enabled = name.trim().length > 1 && email.contains("@")) { Text("CONTINUE") }
            }
        }
    }
}

@Composable private fun WelcomeScreen(name: String, done: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AnimatedBackdrop()
        Card(Modifier.fillMaxWidth().padding(22.dp).align(Alignment.Center), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .13f)), elevation = CardDefaults.cardElevation(12.dp)) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.rss_data_recovery_logo),
                    contentDescription = "RSS Data Recovery",
                    modifier = Modifier.size(86.dp)
                )
                Text("CONGRATULATIONS 👏🎉", color = Color(0xFFFFD166), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("WELCOME, ${name.uppercase()}!", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("YOUR RECOVERY SPACE IS READY.", color = Color.White, fontWeight = FontWeight.SemiBold)
                Button(onClick = done, modifier = Modifier.fillMaxWidth()) { Text("GET STARTED") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun RecoveryMain(prefs: SharedPreferences, dark: Boolean, onDarkChange: (Boolean) -> Unit, theme: Int, onThemeChange: (Int) -> Unit) {
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
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val name = prefs.getString("name", "USER") ?: "USER"
    val email = prefs.getString("email", "") ?: ""
    fun navigate(target: Page) { page = target; drawerOpen = false }
    BackHandler {
        when {
            drawerOpen -> drawerOpen = false
            page != Page.HOME -> page = Page.HOME
        }
    }
    val drawerState = rememberDrawerState(if (drawerOpen) DrawerValue.Open else DrawerValue.Closed)
    LaunchedEffect(drawerOpen) { if (drawerOpen) drawerState.open() else drawerState.close() }
    LaunchedEffect(scanning) {
        if (scanning) {
            if (prefs.getBoolean("notifications", true)) {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                scanNotification(context, true)
            }
        } else scanNotification(context, false)
    }
    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
        ModalDrawerSheet(
            modifier = Modifier.widthIn(max = 340.dp),
            drawerContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
        ) {
            Column(Modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.rss_data_recovery_logo),
                        contentDescription = "RSS Data Recovery",
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("WELCOME, ${name.uppercase()}", fontWeight = FontWeight.ExtraBold)
                        Text(email, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("RSS DATA RECOVERY", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                DrawerItem("Premium Upgrade", Icons.Default.Star, Color(0xFFFFB703)) { navigate(Page.PREMIUM) }
                DrawerItem("Home", Icons.Default.Home, Color(0xFF2E86DE)) { navigate(Page.HOME) }
                DrawerItem("Recovery", Icons.Default.Restore, Color(0xFFE67E22)) { mode = Mode.QUICK; category = null; navigate(Page.SCAN) }
                DrawerItem("Results", Icons.Default.Folder, Color(0xFF16A085)) { navigate(Page.RESULTS) }
                DrawerItem("Recovery History", Icons.Default.History, Color(0xFF8E44AD)) { navigate(Page.HISTORY) }
                DrawerItem("Settings", Icons.Default.Settings, Color(0xFF5C677D)) { navigate(Page.SETTINGS) }
                Spacer(Modifier.weight(1f)); HorizontalDivider(); Spacer(Modifier.height(8.dp)); Text("RAZEEN SECURE SOLUTION", fontWeight = FontWeight.Bold)
                ContactItem("077 115 5504", Icons.Default.Phone, Color(0xFF27AE60)) { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+94771155504"))) }
                ContactItem("rsscctvsolution@gmail.com", Icons.Default.Email, Color(0xFF2980B9)) { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:rsscctvsolution@gmail.com"))) }
                ContactItem("www.rsscctvsolution.eu.cc", Icons.Default.Language, Color(0xFF8E44AD)) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.rsscctvsolution.eu.cc"))) }
            }
        }
    }) {
        Scaffold(topBar = { TopAppBar(title = { Text(pageTitle(page, mode)) }, navigationIcon = { IconButton(onClick = { drawerOpen = true }) { Icon(Icons.Default.Menu, "Menu") } }) }, bottomBar = {
            NavigationBar(modifier = Modifier.shadow(5.dp), tonalElevation = 3.dp) {
                NavigationBarItem(selected = page == Page.HOME, onClick = { navigate(Page.HOME) }, icon = { Icon(Icons.Default.Home, null, tint = Color(0xFF2E86DE)) }, label = { Text("Home") })
                NavigationBarItem(selected = page == Page.SCAN, onClick = { mode = Mode.QUICK; navigate(Page.SCAN) }, icon = { Icon(Icons.Default.Restore, null, tint = Color(0xFFE67E22)) }, label = { Text("Recover") })
                NavigationBarItem(selected = page == Page.RESULTS, onClick = { navigate(Page.RESULTS) }, icon = { Icon(Icons.Default.Folder, null, tint = Color(0xFF16A085)) }, label = { Text("Results") })
                NavigationBarItem(selected = page == Page.SETTINGS, onClick = { navigate(Page.SETTINGS) }, icon = { Icon(Icons.Default.Settings, null, tint = Color(0xFF8E44AD)) }, label = { Text("Settings") })
            }
        }) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (page) {
                    Page.HOME -> HomeScreen({ mode = Mode.QUICK; category = null; navigate(Page.SCAN) }, { mode = Mode.DEEP; category = null; navigate(Page.SCAN) }, { navigate(Page.HISTORY) }) { category = it; mode = Mode.QUICK; navigate(Page.SCAN) }
                    Page.SCAN -> ScanScreen(mode, category, scanning, progress, count, { progress = it }, { scanning = it }, { result -> files = result; count = result.size; navigate(Page.RESULTS) }, scope, prefs)
                    Page.RESULTS -> ResultsScreen(files, prefs.getBoolean("premium", false)) { navigate(Page.PREMIUM) }
                    Page.PREMIUM -> PremiumScreen(prefs.getBoolean("premium", false))
                    Page.HISTORY -> HistoryScreen(prefs)
                    Page.SETTINGS -> SettingsScreen(prefs, dark, onDarkChange, theme, onThemeChange)
                }
            }
        }
    }
}

@Composable private fun DrawerItem(title: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    NavigationDrawerItem(label = { Text(title) }, selected = false, onClick = onClick, icon = { Icon(icon, null, tint = tint) }, modifier = Modifier.padding(vertical = 2.dp))
}

@Composable private fun ContactItem(text: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(19.dp), tint = tint); Spacer(Modifier.width(9.dp)); Text(text, style = MaterialTheme.typography.bodySmall) }
}

private fun pageTitle(page: Page, mode: Mode): String = when (page) {
    Page.HOME -> "Home"; Page.SCAN -> if (mode == Mode.QUICK) "Quick Recovery" else "Deep Recovery"; Page.RESULTS -> "Results"; Page.PREMIUM -> "Premium"; Page.SETTINGS -> "Settings"; Page.HISTORY -> "Recovery History"
}

@Composable private fun HomeScreen(quick: () -> Unit, deep: () -> Unit, history: () -> Unit, onCategory: (Category) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { Text("RECOVER YOUR FILES", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold); Text("SCAN, PREVIEW AND RECOVER SAFELY", style = MaterialTheme.typography.bodySmall) }
        item { Card(Modifier.shadow(3.dp, RoundedCornerShape(18.dp)), elevation = CardDefaults.cardElevation(2.dp)) { Column(Modifier.padding(14.dp)) { Text("STORAGE", fontWeight = FontWeight.Bold); Text("READY TO SCAN", style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(7.dp)); LinearProgressIndicator(progress = { .62f }, modifier = Modifier.fillMaxWidth()) } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) { ActionCard("Quick Recovery", Icons.Default.FlashOn, Color(0xFFE67E22), quick, Modifier.weight(1f)); ActionCard("Deep Recovery", Icons.Default.Search, Color(0xFF8E44AD), deep, Modifier.weight(1f)) } }
        item { Text("RECOVERY BY CATEGORY", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold) }
        item { LazyVerticalGrid(GridCells.Fixed(2), Modifier.height(295.dp), verticalArrangement = Arrangement.spacedBy(9.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), userScrollEnabled = false) { items(Category.values().toList()) { item -> val info = categoryInfo(item); ActionCard(info.first, info.second, info.third, { onCategory(item) }, Modifier.fillMaxWidth()) } } }
        item { OutlinedButton(onClick = history, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.History, null); Spacer(Modifier.width(6.dp)); Text("RECOVERY HISTORY") } }
    }
}

private fun categoryInfo(category: Category): Triple<String, ImageVector, Color> = when (category) {
    Category.IMAGE -> Triple("Images", Icons.Default.Image, Color(0xFF27AE60)); Category.AUDIO -> Triple("Audio", Icons.Default.MusicNote, Color(0xFF2980B9)); Category.VIDEO -> Triple("Video", Icons.Default.VideoLibrary, Color(0xFFE74C3C)); Category.FILES -> Triple("Files", Icons.Default.InsertDriveFile, Color(0xFFF39C12)); Category.DOCUMENTS -> Triple("Documents", Icons.Default.Description, Color(0xFF8E44AD))
}

@Composable private fun ActionCard(title: String, icon: ImageVector, tint: Color, onClick: () -> Unit, modifier: Modifier) {
    Card(modifier.clickable(onClick = onClick).shadow(3.dp, RoundedCornerShape(17.dp)), shape = RoundedCornerShape(17.dp), elevation = CardDefaults.cardElevation(2.dp)) { Column(Modifier.padding(13.dp)) { Icon(icon, null, Modifier.size(30.dp), tint = tint); Spacer(Modifier.height(6.dp)); Text(title, fontWeight = FontWeight.Bold) } }
}

@Composable private fun ScanScreen(mode: Mode, category: Category?, scanning: Boolean, progress: Float, count: Int, setProgress: (Float) -> Unit, setScanning: (Boolean) -> Unit, done: (List<FoundFile>) -> Unit, scope: kotlinx.coroutines.CoroutineScope, prefs: SharedPreferences) {
    val context = LocalContext.current
    var paused by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { Card(Modifier.shadow(3.dp, RoundedCornerShape(18.dp)), elevation = CardDefaults.cardElevation(2.dp)) { Column(Modifier.padding(14.dp)) { Text(if (mode == Mode.QUICK) "QUICK RECOVERY" else "DEEP RECOVERY", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold); category?.let { Text(categoryInfo(it).first.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } } } }
        item { Card(Modifier.shadow(3.dp, RoundedCornerShape(18.dp)), elevation = CardDefaults.cardElevation(2.dp)) { Column(Modifier.padding(14.dp)) {
            if (scanning) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp)); Text("${(progress * 100).toInt()}% • $count FILES"); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { OutlinedButton(onClick = { paused = !paused }) { Text(if (paused) "RESUME" else "PAUSE") }; OutlinedButton(onClick = { setScanning(false); paused = false }) { Text("CANCEL") } }
            } else {
                Text("READY TO SCAN", fontWeight = FontWeight.Bold); Spacer(Modifier.height(9.dp)); Button(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO))
                    setScanning(true); setProgress(0f)
                    scope.launch {
                        val result = queryFiles(context, category)
                        for (i in 1..24) { while (paused) delay(100); delay(if (mode == Mode.DEEP) 55 else 30); setProgress(i / 24f) }
                        prefs.edit().putString("last_scan", DateFormat.getDateTimeInstance().format(Date())).putInt("last_count", result.size).apply(); setScanning(false); done(result)
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("START SCAN") }
            }
        } } }
        item { Text(if (mode == Mode.DEEP) "FREE USERS CAN SCAN AND VIEW FILES. RECOVERY REQUIRES PREMIUM." else "FREE USERS CAN RECOVER IMAGES ONLY WITH A NEW NAME AND REDUCED QUALITY.", style = MaterialTheme.typography.bodySmall) }
    }
}

private suspend fun queryFiles(context: Context, category: Category?): List<FoundFile> = withContext(Dispatchers.IO) {
    val result = mutableListOf<FoundFile>(); val uri = MediaStore.Files.getContentUri("external")
    val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.DATE_MODIFIED, MediaStore.Files.FileColumns.MIME_TYPE)
    context.contentResolver.query(uri, projection, "${MediaStore.Files.FileColumns.SIZE}>0", null, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID); val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME); val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE); val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED); val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
        while (cursor.moveToNext() && result.size < 500) {
            val mime = cursor.getString(mimeIndex) ?: ""
            val kind = when { mime.startsWith("image/") -> Category.IMAGE; mime.startsWith("audio/") -> Category.AUDIO; mime.startsWith("video/") -> Category.VIDEO; mime.contains("pdf") || mime.contains("document") || mime.contains("text") -> Category.DOCUMENTS; else -> Category.FILES }
            if (category == null || category == kind) result += FoundFile(cursor.getString(nameIndex) ?: continue, cursor.getLong(sizeIndex), Uri.withAppendedPath(uri, cursor.getLong(idIndex).toString()), kind, cursor.getLong(dateIndex))
        }
    }
    result
}

@Composable private fun ResultsScreen(files: List<FoundFile>, premium: Boolean, upgrade: () -> Unit) {
    var search by remember { mutableStateOf("") }; var sort by remember { mutableIntStateOf(0) }; var selected by remember { mutableStateOf(setOf<Uri>()) }
    val visible = files.filter { it.name.contains(search, true) }.let { when (sort) { 1 -> it.sortedByDescending(FoundFile::size); 2 -> it.sortedByDescending(FoundFile::modified); else -> it.sortedBy { file -> file.name.lowercase() } } }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("${visible.size} FILES FOUND", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold); OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("SEARCH FILES") }, singleLine = true) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { FilterChip(selected = sort == 0, onClick = { sort = 0 }, label = { Text("NAME") }); FilterChip(selected = sort == 1, onClick = { sort = 1 }, label = { Text("SIZE") }); FilterChip(selected = sort == 2, onClick = { sort = 2 }, label = { Text("DATE") }) }; Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { TextButton(onClick = { selected = visible.map(FoundFile::uri).toSet() }) { Text("SELECT ALL") }; TextButton(onClick = { selected = emptySet() }) { Text("CLEAR") } } }
        items(visible) { file -> Card(Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp)), elevation = CardDefaults.cardElevation(1.dp)) { Row(Modifier.clickable { selected = if (file.uri in selected) selected - file.uri else selected + file.uri }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = file.uri in selected, onCheckedChange = null); val info = categoryInfo(file.category); Icon(info.second, null, Modifier.size(27.dp), tint = info.third); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(file.name, maxLines = 1); Text(formatBytes(file.size), style = MaterialTheme.typography.bodySmall) }; Text("PREVIEW", style = MaterialTheme.typography.labelSmall) } } }
        item { Button(onClick = { if (selected.isNotEmpty()) upgrade() }, modifier = Modifier.fillMaxWidth(), enabled = selected.isNotEmpty()) { Text(if (premium) "RECOVER SELECTED" else "RECOVER / UPGRADE") } }
    }
}

@Composable private fun PremiumScreen(active: Boolean) {
    val rows = listOf("Deep recovery", "Audio / video / files", "Original file name", "Original metadata", "Original quality", "Recovery destination")
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { item { Text("PREMIUM", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold); Text(if (active) "PREMIUM IS ACTIVE" else "UNLOCK FULL RECOVERY") }; item { Card(Modifier.shadow(3.dp, RoundedCornerShape(18.dp)), elevation = CardDefaults.cardElevation(2.dp)) { Column(Modifier.padding(12.dp)) { Row { Text("FEATURE", Modifier.weight(1f), fontWeight = FontWeight.Bold); Text("FREE", Modifier.width(52.dp), fontWeight = FontWeight.Bold); Text("PREMIUM", Modifier.width(72.dp), fontWeight = FontWeight.Bold) }; rows.forEachIndexed { index, row -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (index == 0) Icons.Default.Search else Icons.Default.CheckCircle, null, Modifier.size(20.dp), tint = if (index == 0) Color(0xFFE67E22) else Color(0xFF27AE60)); Spacer(Modifier.width(6.dp)); Text(row, Modifier.weight(1f)); Text(if (index == 0) "✓" else "—", Modifier.width(52.dp)); Text("✓", Modifier.width(72.dp), color = Color(0xFF27AE60)) } } } } }
}

@Composable private fun HistoryScreen(prefs: SharedPreferences) {
    val lastScan = prefs.getString("last_scan", null)
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("RECOVERY HISTORY", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold); if (lastScan == null) Text("NO RECENT SCANS") else Card(Modifier.shadow(2.dp, RoundedCornerShape(16.dp))) { Column(Modifier.padding(15.dp)) { Text("RECENT SCAN", fontWeight = FontWeight.Bold); Text(lastScan); Text("${prefs.getInt("last_count", 0)} FILES") } } }
}

@Composable private fun SettingsScreen(prefs: SharedPreferences, dark: Boolean, onDarkChange: (Boolean) -> Unit, theme: Int, onThemeChange: (Int) -> Unit) {
    var lock by remember { mutableStateOf(prefs.getBoolean("app_lock", false)) }; var haptics by remember { mutableStateOf(prefs.getBoolean("haptics", true)) }; var notifications by remember { mutableStateOf(prefs.getBoolean("notifications", true)) }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { item { Text("SETTINGS", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold) }; item { SettingSwitch("DARK APPEARANCE", dark, onDarkChange) }; item { SettingSwitch("APP LOCK / BIOMETRIC", lock) { lock = it; prefs.edit().putBoolean("app_lock", it).apply() } }; item { SettingSwitch("HAPTIC FEEDBACK", haptics) { haptics = it; prefs.edit().putBoolean("haptics", it).apply() } }; item { SettingSwitch("SCAN NOTIFICATIONS", notifications) { notifications = it; prefs.edit().putBoolean("notifications", it).apply() } }; item { Text("COLOR THEME", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }; item { Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { palettes.forEachIndexed { index, colors -> Button(onClick = { onThemeChange(index) }, colors = ButtonDefaults.buttonColors(containerColor = colors[0])) { Text(if (index == theme) "✓" else "${index + 1}") } } } }; item { Card(Modifier.shadow(2.dp, RoundedCornerShape(16.dp))) { Column(Modifier.padding(14.dp)) { Text("PRIVACY & SAFETY", fontWeight = FontWeight.Bold); Text("SCANNING STAYS ON THE DEVICE AND USES ANDROID STORAGE PERMISSIONS.", style = MaterialTheme.typography.bodySmall) } } } }
}

@Composable private fun SettingSwitch(title: String, value: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), fontWeight = FontWeight.Medium); Switch(checked = value, onCheckedChange = onChange) } }

private fun scanNotification(context: Context, active: Boolean) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("rss_scan", "Recovery Scan", NotificationManager.IMPORTANCE_LOW))
    if (active) { val builder = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(context, "rss_scan") else android.app.Notification.Builder(context); builder.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("SCAN IN PROGRESS").setContentText("RSS Data Recovery is scanning").setOngoing(true); manager.notify(991, builder.build()) } else manager.cancel(991)
}
private fun formatBytes(value: Long): String = when { value < 1024 -> "$value B"; value < 1048576 -> "${value / 1024} KB"; value < 1073741824 -> "${value / 1048576} MB"; else -> "${value / 1073741824} GB" }
