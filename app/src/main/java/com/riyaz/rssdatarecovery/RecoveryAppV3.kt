package com.riyaz.rssdatarecovery

import android.Manifest
import android.accounts.AccountManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.security.MessageDigest
import java.util.Date

private enum class Page { HOME, FEATURES, SCAN, RESULTS, PREMIUM, SETTINGS, HISTORY }
private enum class Mode { QUICK, DEEP }
private enum class Category { IMAGE, AUDIO, VIDEO, FILES, DOCUMENTS }
private data class FoundFile(val name: String, val size: Long, val uri: Uri, val category: Category, val modified: Long, val isTrashed: Boolean = false)
private val palettes = listOf(listOf(Color(0xFFB7791F), Color(0xFFF6D365)), listOf(Color(0xFF1677FF), Color(0xFF67D5FF)), listOf(Color(0xFF0E9F6E), Color(0xFF65D6A6)), listOf(Color(0xFF8B5CF6), Color(0xFFE0B7FF)))

@Composable
fun RecoveryAppV3(appLocked: Boolean = false, onUnlock: () -> Unit = {}, onPinUnlock: (String) -> Unit = {}, onForgotPin: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("rss_recovery", Context.MODE_PRIVATE) }
    var registered by remember { mutableStateOf(prefs.getBoolean("registered", false)) }
    var welcomed by remember { mutableStateOf(prefs.getBoolean("welcome_done", false)) }
    var featuresDone by remember { mutableStateOf(prefs.getBoolean("features_done", false)) }
    var featuresSkipped by remember { mutableStateOf(true) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    val systemDark = isSystemInDarkTheme()
    var theme by remember { mutableIntStateOf(prefs.getInt("theme", 0).coerceIn(0, 3)) }
    val palette = palettes[theme]
    val scope = rememberCoroutineScope()
    val scheme = if (dark) darkColorScheme(primary = palette[0], secondary = palette[1]) else lightColorScheme(
        primary = palette[0],
        secondary = palette[1],
        background = Color.White,
        surface = Color.White,
        surfaceVariant = Color(0xFFF5F6F8)
    )
    val onboardingScheme = if (systemDark) darkColorScheme(primary = palette[0], secondary = palette[1]) else lightColorScheme(
        primary = palette[0], secondary = palette[1], background = Color.White, surface = Color.White, surfaceVariant = Color(0xFFF5F6F8)
    )
    MaterialTheme(colorScheme = if (!registered || !welcomed || (!featuresDone && !featuresSkipped)) onboardingScheme else scheme) {
        if (appLocked) {
            LockScreen(prefs, systemDark, onUnlock, onPinUnlock, onForgotPin)
            return@MaterialTheme
        }
        AnimatedContent(targetState = Triple(registered, welcomed, featuresDone || featuresSkipped), transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "entry") { state ->
            when {
                !state.first -> RegistrationScreen(systemDark) { name, email -> prefs.edit().putBoolean("registered", true).putString("name", name).putString("email", email).apply(); registered = true; scope.launch { registerRecoveryCustomer(name, email) } }
                !state.second -> WelcomeScreen(prefs.getString("name", "USER") ?: "USER", systemDark) { prefs.edit().putBoolean("welcome_done", true).apply(); welcomed = true }
                !state.third -> AppFeaturesOnboarding(systemDark, { prefs.edit().putBoolean("features_done", true).apply(); featuresDone = true }, { prefs.edit().putBoolean("features_skipped", true).apply(); featuresSkipped = true })
                else -> RecoveryMain(prefs, dark, { dark = it; prefs.edit().putBoolean("dark", it).apply() }, theme, { theme = it; prefs.edit().putInt("theme", it).apply() })
            }
        }
    }
}

@Composable
private fun SoftBlurGlow(modifier: Modifier = Modifier, tint: Color = Color(0xFF2EA7FF)) {
    Box(modifier.background(tint.copy(alpha = 0.06f), androidx.compose.foundation.shape.CircleShape).graphicsLayer {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) renderEffect = BlurEffect(20f, 20f, TileMode.Clamp)
    })
}

@Composable private fun AnimatedBackdrop(dark: Boolean = true) {
    val t = rememberInfiniteTransition(label = "bg")
    val x by t.animateFloat(0f, 1f, infiniteRepeatable(tween(5200), RepeatMode.Reverse), label = "x")
    val y by t.animateFloat(0f, 1f, infiniteRepeatable(tween(6800), RepeatMode.Reverse), label = "y")
    val glow by t.animateFloat(.10f, .22f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "glow")
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(if (dark) listOf(Color(0xFF07111F), Color(0xFF17304A), Color(0xFF090D16)) else listOf(Color.White, Color(0xFFF4F7FB), Color.White), start = androidx.compose.ui.geometry.Offset(x * 1100f, y * 500f), end = androidx.compose.ui.geometry.Offset((1f - x) * 700f, 1500f)))) {
        Box(Modifier.offset(x = (x * 90f - 45f).dp, y = (y * 120f - 60f).dp).size(260.dp).background(Color(0xFF2EA7FF).copy(alpha = if (dark) glow else glow * .55f), androidx.compose.foundation.shape.CircleShape))
        Box(Modifier.align(Alignment.BottomEnd).offset(x = (-x * 70f).dp, y = (-y * 90f).dp).size(220.dp).background(Color(0xFFB7791F).copy(alpha = glow * .48f), androidx.compose.foundation.shape.CircleShape))
    }
}

@Composable private fun LockScreen(
    prefs: SharedPreferences,
    systemDark: Boolean,
    onUnlock: () -> Unit,
    onPinUnlock: (String) -> Unit,
    onForgotPin: () -> Unit
) {
    val surface = if (systemDark) Color(0xFF18212B) else Color.White
    val onSurface = if (systemDark) Color.White else Color(0xFF172033)
    val muted = onSurface.copy(alpha = 0.68f)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedBackdrop(systemDark)
        SoftBlurGlow(Modifier.align(Alignment.Center).size(280.dp), Color(0xFF8B5CF6))
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).align(Alignment.Center),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Default.Lock, null, Modifier.size(54.dp), tint = Color(0xFFFFD166))
                Text("RSS DATA RECOVERY", color = onSurface, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("APP LOCKED", color = onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (prefs.getBoolean("pin_enabled", false)) {
                    var pin by remember { mutableStateOf("") }
                    Text("ENTER YOUR 6-DIGIT PIN", color = muted, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("6-DIGIT PIN") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        textStyle = LocalTextStyle.current.copy(color = onSurface),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                    Button(onClick = { if (pin.length == 6) onPinUnlock(pin) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.LockOpen, null)
                        Spacer(Modifier.width(7.dp))
                        Text("UNLOCK WITH PIN")
                    }
                    TextButton(onClick = onForgotPin) { Text("FORGOT PIN") }
                    if (prefs.getBoolean("biometric_enabled", false)) {
                        Text("BIOMETRIC PROMPT IS ALSO AVAILABLE.", color = muted, style = MaterialTheme.typography.bodySmall)
                    }
                } else if (prefs.getBoolean("biometric_enabled", false)) {
                    Text("BIOMETRIC UNLOCK IS ACTIVE", color = muted)
                    Text("Follow the biometric prompt to unlock.", color = muted, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("APP LOCK IS ENABLED. SET A PIN OR BIOMETRIC IN SETTINGS.", color = muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun RegistrationScreen(systemDark: Boolean, done: (String, String) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val deviceEmails = remember {
        buildList {
            runCatching { AccountManager.get(context).getAccountsByType("com.google").map { it.name } }.getOrNull()?.let { addAll(it) }
            val stored = context.getSharedPreferences("rss_recovery", Context.MODE_PRIVATE).getString("email", "").orEmpty()
            if (stored.isNotBlank()) add(stored)
        }.distinct()
    }
    LaunchedEffect(Unit) { delay(120); visible = true; if (email.isBlank() && deviceEmails.isNotEmpty()) email = deviceEmails.first() }
    Box(Modifier.fillMaxSize()) {
        AnimatedBackdrop(systemDark)
        SoftBlurGlow(Modifier.align(Alignment.Center).size(330.dp), Color(0xFF2EA7FF))
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedVisibility(visible, enter = fadeIn(tween(450)) + scaleIn(initialScale = .94f, animationSpec = tween(500)) + slideInVertically(initialOffsetY = { it / 12 }, animationSpec = tween(500))) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp).shadow(3.dp, RoundedCornerShape(30.dp)), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.rss_data_recovery_logo), "RSS Data Recovery", Modifier.size(104.dp)) }
                        Text("RSS DATA RECOVERY", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        Text("CREATE YOUR PROFILE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("FULL NAME") }, leadingIcon = { Icon(Icons.Default.Person, null, tint = Color(0xFF4F7CFF)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
                        ExposedDropdownMenuBox(expanded = expanded && deviceEmails.size > 1, onExpandedChange = { if (deviceEmails.size > 1) expanded = !expanded }) {
                            OutlinedTextField(email, { if (deviceEmails.size <= 1) email = it }, Modifier.fillMaxWidth().menuAnchor(), label = { Text("EMAIL ADDRESS") }, leadingIcon = { Icon(Icons.Default.Email, null, tint = Color(0xFF18B7A0)) }, trailingIcon = { if (deviceEmails.size > 1) ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, singleLine = true, readOnly = deviceEmails.size > 1, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                            if (deviceEmails.size > 1) ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                deviceEmails.forEach { account -> DropdownMenuItem(text = { Text(account) }, leadingIcon = { Icon(Icons.Default.AccountCircle, null, tint = Color(0xFF4F7CFF)) }, onClick = { email = account; expanded = false }) }
                            }
                        }
                        if (deviceEmails.size > 1) Text("SELECT AN EMAIL ACCOUNT FROM THIS DEVICE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else if (deviceEmails.size == 1) Text("EMAIL AUTOMATICALLY DETECTED FROM THIS DEVICE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { done(name.trim(), email.trim()) }, Modifier.fillMaxWidth(), enabled = name.trim().length > 1 && email.contains("@")) { Text("CONTINUE") }
                    }
                }
            }
        }
    }
}

@Composable private fun WelcomeScreen(name: String, systemDark: Boolean, done: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(120); visible = true }
    val pulse = rememberInfiniteTransition(label = "welcomePulse")
    val alpha by pulse.animateFloat(.72f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "welcomeAlpha")
    Box(Modifier.fillMaxSize()) {
        AnimatedBackdrop(systemDark)
        SoftBlurGlow(Modifier.align(Alignment.Center).size(330.dp), Color(0xFFB7791F))
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedVisibility(visible, enter = fadeIn(tween(500)) + scaleIn(initialScale = .94f, animationSpec = tween(500)) + slideInVertically(initialOffsetY = { it / 12 }, animationSpec = tween(500))) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f)), elevation = CardDefaults.cardElevation(3.dp)) {
                    Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.rss_data_recovery_logo), "RSS Data Recovery", Modifier.size(86.dp)) }
                        Text("CONGRATULATIONS 👏🎉", color = Color(0xFFFFD166).copy(alpha = alpha), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        Text("WELCOME, $name!", color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("YOUR RECOVERY SPACE IS READY.", fontWeight = FontWeight.SemiBold)
                        Button(onClick = done, Modifier.fillMaxWidth()) { Text("GET STARTED") }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable private fun RecoveryMain(
    prefs: SharedPreferences,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    theme: Int,
    onThemeChange: (Int) -> Unit
) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(Page.HOME) }
    var mode by remember { mutableStateOf(Mode.QUICK) }
    var category by remember { mutableStateOf<Category?>(null) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showRecoveryModePicker by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    var files by remember { mutableStateOf(emptyList<FoundFile>()) }
    var scanning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var count by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val name = prefs.getString("name", "USER")?.trim().orEmpty().ifBlank { "USER" }
    val email = prefs.getString("email", "")?.trim().orEmpty()

    fun navigate(target: Page) {
        page = target
        drawerOpen = false
    }

    BackHandler(enabled = drawerOpen || page != Page.HOME) {
        if (drawerOpen) drawerOpen = false else page = Page.HOME
    }

    val drawerState = rememberDrawerState(if (drawerOpen) DrawerValue.Open else DrawerValue.Closed)

    LaunchedEffect(drawerOpen) {
        if (drawerOpen) drawerState.open() else drawerState.close()
    }

    LaunchedEffect(scanning) {
        if (scanning) {
            if (prefs.getBoolean("notifications", true)) {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                scanNotification(context, true)
            }
        } else {
            scanNotification(context, false)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .shadow(6.dp),
                drawerContainerColor = Color.Transparent,
                drawerContentColor = if (dark) Color.White else Color(0xFF172033),
                drawerShape = RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(14.dp)
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.rss_data_recovery_logo),"RSS Data Recovery",Modifier.size(112.dp)) }
                                Spacer(Modifier.height(6.dp))
                                Text(name,fontWeight=FontWeight.ExtraBold,fontSize=16.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                                if(email.isNotBlank()) Text(email,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                            }
                            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal=4.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
                                BlurMenuItem("Home",Icons.Default.Home,Color(0xFF4F7CFF),page==Page.HOME){navigate(Page.HOME)}
                                BlurMenuItem("App Features",Icons.Default.AutoAwesome,Color(0xFFFFB21A),page==Page.FEATURES){navigate(Page.FEATURES)}
                                BlurMenuItem("Quick Recovery",Icons.Default.FlashOn,Color(0xFFFF8A3D),page==Page.SCAN && mode==Mode.QUICK){mode=Mode.QUICK;category=null;navigate(Page.SCAN)}
                                BlurMenuItem("Deep Recovery",Icons.Default.Search,Color(0xFF8E44AD),page==Page.SCAN && mode==Mode.DEEP){mode=Mode.DEEP;category=null;navigate(Page.SCAN)}
                                BlurMenuItem("Recovery by Category",Icons.Default.Category,Color(0xFF18B7A0),showCategoryPicker || (page==Page.SCAN && category!=null)){showCategoryPicker=true;drawerOpen=false}
                                BlurMenuItem("Results",Icons.Default.Folder,Color(0xFF18B7A0),page==Page.RESULTS){navigate(Page.RESULTS)}
                                BlurMenuItem("Premium",Icons.Default.Star,Color(0xFFFFB21A),page==Page.PREMIUM){navigate(Page.PREMIUM)}
                                BlurMenuItem("Recovery History",Icons.Default.History,Color(0xFF9B5CFF),page==Page.HISTORY){navigate(Page.HISTORY)}
                                BlurMenuItem("Settings",Icons.Default.Settings,Color(0xFF4F7CFF),page==Page.SETTINGS){navigate(Page.SETTINGS)}
                            }
                            Column(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally){
                                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.rss_original_logo),"Razeen Secure Solution",Modifier.size(76.dp).clickable{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.rsscctvsolution.eu.cc")))})
                                Spacer(Modifier.height(2.dp))
                                Text("RAZEEN SECURE SOLUTION",fontWeight=FontWeight.ExtraBold,fontSize=13.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                                Text("Mobile & PC Software • CCTV • Networking • System Administration",style=MaterialTheme.typography.labelSmall,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                                Text("077 115 5504  •  070 155 5504",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                                Text("rsscctvsolution@gmail.com",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                                Text("www.rsscctvsolution.eu.cc",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                pageTitle(page, mode),
                                fontWeight = FontWeight.Bold
                            )

                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { drawerOpen = true },
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(46.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(15.dp)
                                )
                        ) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            bottomBar = {
                Box(Modifier.padding(horizontal=12.dp,vertical=8.dp).clip(RoundedCornerShape(24.dp)).shadow(3.dp,RoundedCornerShape(24.dp))){
                    Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface,RoundedCornerShape(24.dp)))
                    NavigationBar(modifier=Modifier.fillMaxWidth(),containerColor=Color.Transparent,tonalElevation=0.dp){
                        NavigationBarItem(selected=page==Page.HOME,onClick={navigate(Page.HOME)},icon={Icon(Icons.Default.Home,null,tint=Color(0xFF4F7CFF))},label={Text("Home")})
                        NavigationBarItem(selected=page==Page.SCAN,onClick={mode=Mode.QUICK;category=null;navigate(Page.SCAN)},icon={Icon(Icons.Default.Restore,null,tint=Color(0xFFFF8A3D))},label={Text("Recover")})
                        NavigationBarItem(selected=page==Page.RESULTS,onClick={navigate(Page.RESULTS)},icon={Icon(Icons.Default.Folder,null,tint=Color(0xFF18B7A0))},label={Text("Results")})
                        NavigationBarItem(selected=page==Page.PREMIUM,onClick={navigate(Page.PREMIUM)},icon={Icon(Icons.Default.Star,null,tint=Color(0xFFFFB21A))},label={Text("Premium")})
                        NavigationBarItem(selected=page==Page.SETTINGS,onClick={navigate(Page.SETTINGS)},icon={Icon(Icons.Default.Settings,null,tint=Color(0xFF9B5CFF))},label={Text("Settings")})
                    }
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .then(
                        if (!dark) {
                            Modifier.background(Color.White)
                        } else {
                            Modifier.background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surface,
                                        palettes[theme][1].copy(alpha = .08f),
                                        palettes[theme][0].copy(alpha = .05f)
                                    )
                                )
                            )
                        }
                    )
            ) {
                when (page) {
                    Page.HOME -> HomeScreen(
                        quick = { mode = Mode.QUICK; category = null; navigate(Page.SCAN) },
                        deep = { mode = Mode.DEEP; category = null; navigate(Page.SCAN) },
                        history = { navigate(Page.HISTORY) },
                        onResults = { navigate(Page.RESULTS) },
                        onCategory = { selected -> category = selected; mode = Mode.QUICK; navigate(Page.SCAN) }
                    )

                    Page.SCAN -> ScanScreen(
                        mode,
                        { mode = it; category = null },
                        category,
                        scanning,
                        progress,
                        count,
                        { progress = it },
                        { scanning = it },
                        { result -> files = result; count = result.size; navigate(Page.RESULTS) },
                        scope,
                        prefs
                    )

                    Page.FEATURES -> FeaturesScreen(
                        onQuick = { mode = Mode.QUICK; category = null; navigate(Page.SCAN) },
                        onDeep = { mode = Mode.DEEP; category = null; navigate(Page.SCAN) },
                        onResults = { navigate(Page.RESULTS) },
                        onPremium = { navigate(Page.PREMIUM) }                    )

                    Page.RESULTS -> ResultsScreen(
                        files,
                        prefs.getBoolean("premium", false),
                        scope
                    ) { navigate(Page.PREMIUM) }

                    Page.PREMIUM -> PremiumScreen(prefs.getBoolean("premium", false)) { navigate(Page.PREMIUM) }
                    Page.HISTORY -> HistoryScreen(prefs)
                    Page.SETTINGS -> SettingsScreen(prefs, dark, onDarkChange, theme, onThemeChange)
                }
            }
        }
    }

    if (showCategoryPicker) {
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text("RECOVERY BY CATEGORY", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Category.values().forEach { item ->
                        val label = item.name.lowercase().replaceFirstChar { it.uppercase() }
                        OutlinedButton(
                            onClick = { category = item; showCategoryPicker = false; showRecoveryModePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(when (item) {
                                Category.IMAGE -> Icons.Default.Image
                                Category.AUDIO -> Icons.Default.Audiotrack
                                Category.VIDEO -> Icons.Default.Videocam
                                Category.FILES -> Icons.Default.InsertDriveFile
                                Category.DOCUMENTS -> Icons.Default.Description
                            }, null)
                            Spacer(Modifier.width(10.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCategoryPicker = false }) { Text("CANCEL") } }
        )
    }
    if (showRecoveryModePicker && category != null) {
        val selectedCategory = category!!
        AlertDialog(
            onDismissRequest = { showRecoveryModePicker = false },
            title = { Text(categoryInfo(selectedCategory).first.uppercase() + " RECOVERY", fontWeight = FontWeight.ExtraBold) },
            text = { Text("Choose how you want RSS Data Recovery to scan this category. Quick Recovery is faster; Deep Recovery performs the broader scan.") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { mode = Mode.QUICK; showRecoveryModePicker = false; navigate(Page.SCAN) }) { Text("QUICK") }
                    Button(onClick = { mode = Mode.DEEP; showRecoveryModePicker = false; navigate(Page.SCAN) }) { Text("DEEP") }
                }
            },
            dismissButton = { TextButton(onClick = { showRecoveryModePicker = false }) { Text("CANCEL") } }
        )
    }
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    radius: androidx.compose.ui.unit.Dp,
    alpha: Float,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .shadow(3.dp, RoundedCornerShape(radius))
            .border(
                1.dp,
                Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(radius)
            ),
        shape = RoundedCornerShape(radius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        content = content
    )
}

@Composable
private fun BlurMenuItem(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (selected) .92f else .72f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(iconColor.copy(alpha = 0.06f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            Modifier.weight(1f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
        if (selected) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}

@Composable
private fun paletteGlass(theme: Int, dark: Boolean, alpha: Float): Color {
    val base = palettes[theme.coerceIn(0, palettes.lastIndex)][0]
    return base.copy(alpha = 1f)
}

@Composable private fun DrawerItem(title: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    NavigationDrawerItem(label = { Text(title) }, selected = false, onClick = onClick, icon = { Icon(icon, null, tint = tint) }, modifier = Modifier.padding(vertical = 2.dp))
}

@Composable private fun ContactItem(text: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(19.dp), tint = tint); Spacer(Modifier.width(9.dp)); Text(text, style = MaterialTheme.typography.bodySmall) }
}

private fun pageTitle(page: Page, mode: Mode): String = when (page) {
    Page.HOME -> "Home"; Page.FEATURES -> "App Features"; Page.SCAN -> if (mode == Mode.QUICK) "Quick Recovery" else "Deep Recovery"; Page.RESULTS -> "Results"; Page.PREMIUM -> "Premium"; Page.SETTINGS -> "Settings"; Page.HISTORY -> "Recovery History"
}

@Composable private fun FeaturesScreen(onQuick:()->Unit,onDeep:()->Unit,onResults:()->Unit,onPremium:()->Unit){
    val features=listOf(Triple("Quick Recovery",Icons.Default.FlashOn,Color(0xFFE67E22)),Triple("Deep Recovery",Icons.Default.Search,Color(0xFF8E44AD)),Triple("Results & Preview",Icons.Default.Folder,Color(0xFF4F7CFF)),Triple("Recovery History",Icons.Default.History,Color(0xFF9B5CFF)),Triple("App Lock & PIN",Icons.Default.Lock,Color(0xFFE74C3C)),Triple("Premium Recovery",Icons.Default.Star,Color(0xFFFFB21A)))
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("APP FEATURES",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold);Text("Everything available in RSS Data Recovery.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};items(features){item->Card(Modifier.fillMaxWidth().clickable{when(item.first){"Quick Recovery"->onQuick();"Deep Recovery"->onDeep();"Results & Preview"->onResults();"Premium Recovery"->onPremium()}}.shadow(2.dp,RoundedCornerShape(17.dp)),shape=RoundedCornerShape(17.dp),elevation=CardDefaults.cardElevation(1.dp)){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(44.dp).background(item.third.copy(alpha=.12f),RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){Icon(item.second,null,tint=item.third,modifier=Modifier.size(23.dp))};Spacer(Modifier.width(12.dp));Text(item.first,Modifier.weight(1f),fontWeight=FontWeight.Bold);Icon(Icons.Default.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)}}}}
}

@Composable private fun HomeScreen(quick:()->Unit,deep:()->Unit,history:()->Unit,onResults:()->Unit,onCategory:(Category)->Unit){
    val context=LocalContext.current;var storage by remember{mutableStateOf(storageUsage(context))};LaunchedEffect(Unit){while(true){storage=storageUsage(context);delay(1500)}}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(11.dp)){
        item{Text("RECOVERY BY CATEGORY",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.ExtraBold)}
        item{LazyVerticalGrid(GridCells.Fixed(2),Modifier.fillMaxWidth().height(250.dp),verticalArrangement=Arrangement.spacedBy(9.dp),horizontalArrangement=Arrangement.spacedBy(9.dp),userScrollEnabled=false){items(Category.values().toList()){cat->val info=categoryInfo(cat);ActionCard(info.first,info.second,info.third,{onCategory(cat)},Modifier.fillMaxWidth())}}}
        item{Card(Modifier.fillMaxWidth().shadow(2.dp,RoundedCornerShape(18.dp)),shape=RoundedCornerShape(18.dp),elevation=CardDefaults.cardElevation(1.dp)){Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Storage,null,tint=Color(0xFF4F7CFF));Spacer(Modifier.width(9.dp));Text("DEVICE STORAGE",fontWeight=FontWeight.Bold)};Text("${storage.first} USED  •  ${storage.second} FREE",style=MaterialTheme.typography.bodySmall);LinearProgressIndicator(progress={storage.third},modifier=Modifier.fillMaxWidth())}}}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){ActionCard("Quick Recovery",Icons.Default.FlashOn,Color(0xFFE67E22),quick,Modifier.weight(1f));ActionCard("Deep Recovery",Icons.Default.Search,Color(0xFF8E44AD),deep,Modifier.weight(1f))}}
        item{Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("RECOVERY TOOLS",fontWeight=FontWeight.ExtraBold);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){ToolPill("Safe preview",Icons.Default.Visibility,Color(0xFF4F7CFF),{onResults()},Modifier.weight(1f));ToolPill("Offline scan",Icons.Default.CloudOff,Color(0xFF18B7A0),{quick()},Modifier.weight(1f))};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){ToolPill("Recovery history",Icons.Default.History,Color(0xFF8E44AD),{history()},Modifier.weight(1f))}}}
        item{OutlinedButton(onClick=history,Modifier.fillMaxWidth()){Icon(Icons.Default.History,null);Spacer(Modifier.width(6.dp));Text("RECOVERY HISTORY")}}
    }
}
@Composable private fun ToolPill(title:String,icon:ImageVector,tint:Color,onClick:()->Unit,modifier:Modifier=Modifier){Row(modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).clickable(onClick=onClick).background(tint.copy(alpha=.08f),RoundedCornerShape(13.dp)).padding(horizontal=10.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,Modifier.size(19.dp),tint);Spacer(Modifier.width(7.dp));Text(title,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold)}}


private fun categoryInfo(category: Category): Triple<String, ImageVector, Color> = when (category) {
    Category.IMAGE -> Triple("Images", Icons.Default.Image, Color(0xFF27AE60)); Category.AUDIO -> Triple("Audio", Icons.Default.MusicNote, Color(0xFF2980B9)); Category.VIDEO -> Triple("Video", Icons.Default.VideoLibrary, Color(0xFFE74C3C)); Category.FILES -> Triple("Files", Icons.Default.InsertDriveFile, Color(0xFFF39C12)); Category.DOCUMENTS -> Triple("Documents", Icons.Default.Description, Color(0xFF8E44AD))
}

@Composable private fun ActionCard(title: String, icon: ImageVector, tint: Color, onClick: () -> Unit, modifier: Modifier) {
    Card(
        modifier = modifier.clickable(onClick = onClick).shadow(2.dp, RoundedCornerShape(17.dp)),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).background(tint.copy(alpha = .12f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, Modifier.size(24.dp), tint = tint) }
            Spacer(Modifier.width(11.dp))
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun ScanScreen(mode: Mode, setMode: (Mode) -> Unit, category: Category?, scanning: Boolean, progress: Float, count: Int, setProgress: (Float) -> Unit, setScanning: (Boolean) -> Unit, done: (List<FoundFile>) -> Unit, scope: kotlinx.coroutines.CoroutineScope, prefs: SharedPreferences) {
    val context = LocalContext.current
    var paused by remember { mutableStateOf(false) }
    var scanJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun launchScanAfterPermission() {
        if (scanning || scanJob?.isActive == true) return
        setScanning(true)
        setProgress(0f)
        scanJob = scope.launch {
            try {
                val result = queryFiles(context, category) { processed, found, total ->
                    withContext(Dispatchers.Main) { setProgress(if (total > 0) found.toFloat() / total.toFloat() else 0f) }
                }
                setProgress(1f)
                prefs.edit().putString("last_scan", DateFormat.getDateTimeInstance().format(Date())).putInt("last_count", result.size).apply()
                done(result)
            } finally {
                scanJob = null
                setScanning(false)
                paused = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        launchScanAfterPermission()
    }

    fun requestScanPermissions() {
        if (Build.VERSION.SDK_INT >= 30 &&
            (mode == Mode.DEEP || category == Category.FILES || category == Category.DOCUMENTS) &&
            !Environment.isExternalStorageManager()
        ) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
            return
        }
        when {
            Build.VERSION.SDK_INT >= 33 -> {
                val permissions = when (category) {
                    Category.IMAGE -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
                    Category.VIDEO -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
                    Category.AUDIO -> arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                    else -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
                }
                permissionLauncher.launch(permissions)
            }
            Build.VERSION.SDK_INT >= 29 -> launchScanAfterPermission()
            else -> permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { Card(Modifier.shadow(3.dp, RoundedCornerShape(18.dp)), elevation = CardDefaults.cardElevation(2.dp)) { Column(Modifier.padding(14.dp)) { Text(if (mode == Mode.QUICK) "QUICK RECOVERY" else "DEEP RECOVERY", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold); category?.let { Text(categoryInfo(it).first.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } } } }
        item {
            Card(Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(20.dp)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("RECOVERY MODE", fontWeight = FontWeight.ExtraBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = mode == Mode.QUICK, onClick = { setMode(Mode.QUICK); setScanning(false); setProgress(0f) }, label = { Text("QUICK RECOVERY") })
                        FilterChip(selected = mode == Mode.DEEP, onClick = { setMode(Mode.DEEP); setScanning(false); setProgress(0f) }, label = { Text("DEEP RECOVERY") })
                    }
                    Text(if (mode == Mode.DEEP) "Broader scan across indexed media and files." else "Faster scan focused on commonly recoverable media.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Card(Modifier.shadow(3.dp, RoundedCornerShape(18.dp)), elevation = CardDefaults.cardElevation(2.dp)) { Column(Modifier.padding(14.dp)) {
            if (scanning) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp)); Text("${count} FILES FOUND • ${if (progress >= 1f) "SCAN COMPLETE" else "SCANNING"}"); Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { OutlinedButton(onClick = { paused = !paused }) { Text(if (paused) "RESUME" else "PAUSE") }; OutlinedButton(onClick = { scanJob?.cancel(); scanJob = null; setScanning(false); paused = false; setProgress(0f) }) { Text("CANCEL") } }
            } else {
                Text("READY TO SCAN", fontWeight = FontWeight.Bold); Spacer(Modifier.height(9.dp)); Button(onClick = { requestScanPermissions() }, modifier = Modifier.fillMaxWidth()) { Text("START SCAN") }
            }
        } } }
    }
}

private suspend fun queryFiles(context: Context, category: Category?, onProgress: suspend (processed: Int, found: Int, total: Int) -> Unit = { _, _, _ -> }): List<FoundFile> = withContext(Dispatchers.IO) {
    val result = mutableListOf<FoundFile>()
    // Content-based duplicate detection; only same-size candidates are hashed.
    val seenHashesBySize = mutableMapOf<Long, MutableSet<String>>()
    val resolver = context.contentResolver
    val uri = MediaStore.Files.getContentUri("external")
    val projection = mutableListOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.DATE_MODIFIED,
        MediaStore.Files.FileColumns.MIME_TYPE
    )
    if (Build.VERSION.SDK_INT >= 30) projection += MediaStore.Files.FileColumns.IS_TRASHED

    val cursor = if (Build.VERSION.SDK_INT >= 30) {
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")
        }
        resolver.query(uri, projection.toTypedArray(), args, null)
    } else {
        resolver.query(uri, projection.toTypedArray(), "${MediaStore.Files.FileColumns.SIZE}>0", null, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")
    }

    cursor?.use {
        val idIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val nameIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val sizeIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
        val dateIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
        val mimeIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
        val trashedIndex = if (Build.VERSION.SDK_INT >= 30) it.getColumnIndex(MediaStore.Files.FileColumns.IS_TRASHED) else -1
        val totalRows = it.count.coerceAtLeast(0)
        var processedRows = 0
        while (it.moveToNext()) {
            processedRows++
            val size = it.getLong(sizeIndex)
            val trashed = trashedIndex >= 0 && it.getInt(trashedIndex) != 0
            if (size <= 0L && !trashed) continue
            val mime = it.getString(mimeIndex).orEmpty()
            val kind = when {
                mime.startsWith("image/") -> Category.IMAGE
                mime.startsWith("audio/") -> Category.AUDIO
                mime.startsWith("video/") -> Category.VIDEO
                mime.contains("pdf") || mime.contains("document") || mime.contains("text") || mime.contains("spreadsheet") || mime.contains("presentation") -> Category.DOCUMENTS
                else -> Category.FILES
            }
            if (category == null || category == kind) {
                val fileUri = Uri.withAppendedPath(uri, it.getLong(idIndex).toString())
                val duplicate = if (size > 0L) {
                    val hash = sha256Content(resolver, fileUri)
                    hash != null && !seenHashesBySize.getOrPut(size) { mutableSetOf() }.add(hash)
                } else false
                if (!duplicate) {
                    result += FoundFile(it.getString(nameIndex) ?: "Unnamed file", size, fileUri, kind, it.getLong(dateIndex), trashed)
                }
            }
            if (processedRows == 1 || processedRows % 10 == 0 || processedRows == totalRows) onProgress(processedRows, result.size, totalRows)
        }
    }
    result
}


private fun sha256Content(resolver: ContentResolver, uri: Uri): String? = runCatching {
    val digest = MessageDigest.getInstance("SHA-256")
    resolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    } ?: return null
    digest.digest().joinToString("") { "%02x".format(it) }
}.getOrNull()

@Composable
private fun ResultsScreen(files: List<FoundFile>, premium: Boolean, scope: kotlinx.coroutines.CoroutineScope, upgrade: () -> Unit) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var sort by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(setOf<Uri>()) }
    var showConfirm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRecovery by remember { mutableStateOf<List<FoundFile>>(emptyList()) }

    val trashWriteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val pending = pendingRecovery
        pendingRecovery = emptyList()
        if (result.resultCode == android.app.Activity.RESULT_OK && pending.isNotEmpty()) {
            scope.launch(Dispatchers.Main) {
                message = recoverSelectedFiles(context, pending, premium)
                selected = emptySet()
            }
        } else if (pending.isNotEmpty()) {
            message = "SYSTEM RECOVERY PERMISSION WAS NOT GRANTED."
        }
    }

    val visible = files.filter { it.name.contains(search, true) }.let { filtered ->
        when (sort) {
            1 -> filtered.sortedByDescending(FoundFile::size)
            2 -> filtered.sortedByDescending(FoundFile::modified)
            else -> filtered.sortedBy { it.name.lowercase() }
        }
    }
    val grouped = Category.entries.associateWith { category ->
        visible.filter { it.category == category }
    }.filterValues { it.isNotEmpty() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            label = { Text("SEARCH RECOVERY RESULTS") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF4F7CFF)) },
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        Card(
            Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = Color(0xFF18B7A0), modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("RECOVERY RESULTS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text("${visible.size} matching files • ${files.size} total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (selected.isNotEmpty()) Text("${selected.size} SELECTED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            FilterChip(selected = sort == 0, onClick = { sort = 0 }, label = { Text("NAME") })
            FilterChip(selected = sort == 1, onClick = { sort = 1 }, label = { Text("SIZE") })
            FilterChip(selected = sort == 2, onClick = { sort = 2 }, label = { Text("DATE") })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { selected = visible.map(FoundFile::uri).toSet() }, enabled = visible.isNotEmpty()) { Text("ALL") }
            TextButton(onClick = { selected = emptySet() }, enabled = selected.isNotEmpty()) { Text("CLEAR") }
        }

        if (message != null) {
            Card(
                Modifier.fillMaxWidth().padding(bottom = 7.dp).shadow(1.dp, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text(message!!, Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold)
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            contentPadding = PaddingValues(bottom = 10.dp)
        ) {
            if (visible.isEmpty()) {
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(top = 8.dp).shadow(2.dp, RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(if (files.isEmpty()) Icons.Default.SearchOff else Icons.Default.FilterAltOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(if (files.isEmpty()) "NO SCAN RESULTS YET" else "NO MATCHING RESULTS", fontWeight = FontWeight.ExtraBold)
                            Text(
                                if (files.isEmpty()) "Run Quick Recovery or Deep Recovery to populate this page." else "Change the search or sorting option.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                grouped.forEach { (category, categoryFiles) ->
                    item(key = "header_${category.name}") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val info = categoryInfo(category)
                            Icon(info.second, null, Modifier.size(22.dp), tint = info.third)
                            Spacer(Modifier.width(8.dp))
                            Text(info.first.uppercase(), fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.width(6.dp))
                            Text("${categoryFiles.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(categoryFiles, key = { it.uri.toString() }) { file ->
                        Card(
                            Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    selected = if (file.uri in selected) selected - file.uri else selected + file.uri
                                }.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {                                Checkbox(checked = file.uri in selected, onCheckedChange = null)
                                val info = categoryInfo(file.category)
                                Icon(info.second, null, Modifier.size(27.dp), tint = info.third)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(file.name, maxLines = 1, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${formatBytes(file.size)} • ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(file.modified * 1000L))}${if (file.isTrashed) " • TRASHED" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!premium) {
            Card(
                Modifier.fillMaxWidth().padding(top = 7.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFB21A), modifier = Modifier.size(25.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("UNLOCK MORE RECOVERY", fontWeight = FontWeight.ExtraBold)
                        Text("Deep recovery and original-source recovery for non-image files require Premium. Image recovery remains free.", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                    TextButton(onClick = upgrade) { Text("UPGRADE") }
                }
            }
        }

        Button(
            onClick = {
                val chosen = files.filter { it.uri in selected }
                if (chosen.isEmpty()) return@Button
                val unsupportedFree = chosen.any { it.category != Category.IMAGE }
                if (!premium && unsupportedFree) {
                    upgrade()
                } else {
                    showConfirm = true
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
            enabled = selected.isNotEmpty()
        ) {
            Icon(Icons.Default.Restore, null)
            Spacer(Modifier.width(7.dp))
            Text(if (selected.isEmpty()) "RECOVER SELECTED" else "RECOVER ${selected.size} SELECTED")
        }
    }

    if (showConfirm) {
        val chosen = files.filter { it.uri in selected }
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("CONFIRM RECOVERY") },
            text = {
                Text(if (premium) {
                    "RECOVER ${chosen.size} SELECTED FILE(S). PREMIUM COPIES THE SOURCE CONTENT, ORIGINAL FILE NAME, AND AVAILABLE SOURCE METADATA WITHOUT RE-ENCODING THE FILE."
                } else {
                    "FREE IMAGE RECOVERY creates a new JPEG in Pictures/RSS Data Recovery using the current date in the filename. The image is re-encoded to reduce quality and normally drops source EXIF metadata; the original source file is not overwritten."
                })
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    val trashed = chosen.filter { it.isTrashed && Build.VERSION.SDK_INT >= 30 }
                    if (trashed.isNotEmpty()) {
                        runCatching {
                            pendingRecovery = chosen
                            val request = MediaStore.createWriteRequest(context.contentResolver, trashed.map { it.uri })
                            trashWriteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                        }.onFailure {
                            message = "COULD NOT REQUEST SYSTEM RECOVERY ACCESS."
                            pendingRecovery = emptyList()
                        }
                    } else {
                        scope.launch(Dispatchers.Main) {
                            message = recoverSelectedFiles(context, chosen, premium)
                            selected = emptySet()
                        }
                    }
                }) { Text("RECOVER") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("CANCEL") } }
        )
    }
}

private suspend fun recoverSelectedFiles(context: Context, files: List<FoundFile>, premium: Boolean): String =
    withContext(Dispatchers.IO) {
        var recovered = 0
        var failed = 0
        var restoredFromTrash = 0
        files.forEachIndexed { index, file ->
            try {
                if (file.isTrashed && Build.VERSION.SDK_INT >= 30) {
                    if (!premium && file.category != Category.IMAGE) {
                        failed++
                        return@forEachIndexed
                    }
                    val values = android.content.ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 0) }
                    if (context.contentResolver.update(file.uri, values, null, null) <= 0) throw IllegalStateException("TRASH RESTORE FAILED")
                    if (premium) {
                        recovered++
                        restoredFromTrash++
                        return@forEachIndexed
                    }
                }
                if (!premium && file.category != Category.IMAGE) {
                    failed++
                    return@forEachIndexed
                }
                val resolver = context.contentResolver
                val mime = resolver.getType(file.uri) ?: when (file.category) {
                    Category.IMAGE -> "image/jpeg"
                    Category.AUDIO -> "audio/*"
                    Category.VIDEO -> "video/*"
                    Category.DOCUMENTS -> "application/octet-stream"
                    Category.FILES -> "application/octet-stream"
                }

                if (!premium && file.category == Category.IMAGE) {
                    val bitmap = android.graphics.BitmapFactory.decodeStream(resolver.openInputStream(file.uri))
                        ?: throw IllegalStateException("IMAGE DECODE FAILED")
                    val values = android.content.ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "RSS_DATA_RECOVERY_" + SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(Date()) + "_" + (index + 1) + ".jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        if (Build.VERSION.SDK_INT >= 29) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RSS Data Recovery")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                    }
                    val outputUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("DESTINATION UNAVAILABLE")
                    try {
                        resolver.openOutputStream(outputUri)?.use { out ->
                            if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)) {
                                throw IllegalStateException("IMAGE EXPORT FAILED")
                            }
                        } ?: throw IllegalStateException("OUTPUT UNAVAILABLE")
                        if (Build.VERSION.SDK_INT >= 29) {
                            values.clear()
                            values.put(MediaStore.Images.Media.IS_PENDING, 0)
                            resolver.update(outputUri, values, null, null)
                        }
                    } catch (e: Exception) {
                        resolver.delete(outputUri, null, null)
                        throw e
                    } finally {
                        bitmap.recycle()
                    }
                } else {
                    val values = android.content.ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, file.name.ifBlank { "RSS_RECOVERED_\${System.currentTimeMillis()}" })
                        put(MediaStore.MediaColumns.MIME_TYPE, mime)
                        if (Build.VERSION.SDK_INT >= 29) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, if (file.category == Category.IMAGE) "Pictures/RSS Data Recovery" else "Download/RSS Data Recovery")
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                    }
                    val collection = if (file.category == Category.IMAGE) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    val outputUri = resolver.insert(collection, values)
                        ?: throw IllegalStateException("DESTINATION UNAVAILABLE")
                    try {
                        resolver.openInputStream(file.uri)?.use { input ->
                            resolver.openOutputStream(outputUri)?.use { output -> input.copyTo(output) }
                                ?: throw IllegalStateException("OUTPUT UNAVAILABLE")
                        } ?: throw IllegalStateException("SOURCE UNAVAILABLE")
                        if (Build.VERSION.SDK_INT >= 29) {
                            values.clear()
                            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                            resolver.update(outputUri, values, null, null)
                        }
                    } catch (e: Exception) {
                        resolver.delete(outputUri, null, null)
                        throw e
                    }
                }
                recovered++
            } catch (_: Exception) {
                failed++
            }
        }
        when {
            recovered > 0 && failed == 0 && restoredFromTrash == recovered -> "RESTORED $recovered TRASHED FILE(S) TO THEIR ORIGINAL LOCATION."
            recovered > 0 && failed == 0 -> "RECOVERED $recovered FILE(S). $restoredFromTrash RESTORED FROM TRASH."
            recovered > 0 -> "RECOVERED $recovered FILE(S). $failed FILE(S) COULD NOT BE RECOVERED."
            else -> "RECOVERY FAILED. PLEASE CHECK STORAGE PERMISSIONS AND TRY AGAIN."
        }
    }

@Composable
private fun PremiumScreen(active: Boolean, onUpgrade: () -> Unit) {
    val rows = listOf(
        Triple("Quick image recovery", true, true),
        Triple("Deep recovery engine", false, true),
        Triple("Audio & video recovery", false, true),
        Triple("Documents & files", false, true),
        Triple("Original file names", false, true),
        Triple("Original metadata", false, true),
        Triple("Original quality", false, true),
        Triple("Large batch recovery", false, true),
        Triple("Priority recovery", false, true),
        Triple("Recovery destination control", true, true),
        Triple("Recovery history", true, true),
        Triple("App lock & biometric", true, true)
    )

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "FEATURE COMPARISON",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "FREE vs PRO recovery capabilities",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Card(
                Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("FEATURE", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("FREE", Modifier.width(55.dp), fontWeight = FontWeight.Bold)
                        Text(
                            "PRO",
                            Modifier.width(55.dp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    rows.forEach { (label, free, pro) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Icon(
                                if (free) Icons.Default.CheckCircle else Icons.Default.Lock,
                                null,
                                Modifier.width(55.dp).size(20.dp),
                                tint = if (free) Color(0xFF27AE60) else Color(0xFF8A94A6)
                            )
                            Icon(
                                if (pro) Icons.Default.CheckCircle else Icons.Default.Lock,
                                null,
                                Modifier.width(55.dp).size(20.dp),
                                tint = Color(0xFFFFB21A)
                            )
                        }
                    }
                }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Star, null, Modifier.size(38.dp), tint = Color(0xFFFFB21A))
                    Text(
                        if (active) "PREMIUM ACTIVE" else "RSS DATA RECOVERY PREMIUM",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        if (active) "All recovery capabilities are unlocked."
                        else "Unlock the complete recovery toolkit.",
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (!active) {
                        Button(onClick = onUpgrade, Modifier.fillMaxWidth()) {
                            Text("UPGRADE NOW")
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun HistoryScreen(prefs: SharedPreferences) {
    val lastScan = prefs.getString("last_scan", null)
    val recoveryHistory = prefs.getString("recovery_history", "").orEmpty()
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("RECOVERY HISTORY", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold) }
        item {
            Card(Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp))) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("RECENT SCAN", fontWeight = FontWeight.Bold)
                    if (lastScan == null) Text("NO RECENT SCANS") else {
                        Text(lastScan)
                        Text("${prefs.getInt("last_count", 0)} FILES")
                    }
                }
            }
        }
        item { Text("RECOVERY ACTIVITY", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (recoveryHistory.isBlank()) {
            item { Text("NO RECOVERY ACTIVITY YET") }
        } else {
            recoveryHistory.lineSequence().filter { it.isNotBlank() }.forEach { entry ->
                item {
                    Card(Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp))) {
                        Text(entry, Modifier.padding(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    prefs: SharedPreferences,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    theme: Int,
    onThemeChange: (Int) -> Unit
) {
    val context = LocalContext.current
    var lock by remember { mutableStateOf(prefs.getBoolean("app_lock", false)) }
    var biometric by remember { mutableStateOf(prefs.getBoolean("biometric_enabled", false)) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var haptics by remember { mutableStateOf(prefs.getBoolean("haptics", true)) }
    var notifications by remember { mutableStateOf(prefs.getBoolean("notifications", true)) }
    var autoScan by remember { mutableStateOf(prefs.getBoolean("auto_scan", false)) }
    var confirmRecovery by remember { mutableStateOf(prefs.getBoolean("confirm_recovery", true)) }
    var previews by remember { mutableStateOf(prefs.getBoolean("previews", true)) }
    var saveHistory by remember { mutableStateOf(prefs.getBoolean("save_history", true)) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(22.dp)), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.rss_data_recovery_logo), "RSS Data Recovery", Modifier.size(64.dp)) }
                    Spacer(Modifier.height(8.dp))
                    Text("RSS DATA RECOVERY", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text("SETTINGS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SettingSwitch("DARK APPEARANCE", dark, onDarkChange) }
        item {
            Card(Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = Color(0xFFE74C3C))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("APP LOCK", fontWeight = FontWeight.Bold)
                        Text(if (lock) "Enabled — security settings are available below." else "Create a verified PIN and optional biometric unlock.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = lock, onCheckedChange = { enabled ->
                        if (enabled) {
                            pin = ""; confirmPin = ""; showPinDialog = true
                        } else {
                            lock = false
                            biometric = false
                            prefs.edit().putBoolean("app_lock", false).putBoolean("pin_enabled", false).putBoolean("biometric_enabled", false).apply()
                        }
                    })
                }
            }
        }
        if (lock) item {
            OutlinedButton(onClick = { pin = ""; confirmPin = ""; showPinDialog = true }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Security, null); Spacer(Modifier.width(6.dp)); Text("SET / CHANGE PIN & BIOMETRIC")
            }
        }
        item { SettingSwitch("HAPTIC FEEDBACK", haptics) { haptics = it; prefs.edit().putBoolean("haptics", it).apply() } }
        item { SettingSwitch("SCAN NOTIFICATIONS", notifications) { notifications = it; prefs.edit().putBoolean("notifications", it).apply() } }
        item { SettingSwitch("AUTO SCAN ON LAUNCH", autoScan) { autoScan = it; prefs.edit().putBoolean("auto_scan", it).apply() } }
        item { SettingSwitch("CONFIRM BEFORE RECOVERY", confirmRecovery) { confirmRecovery = it; prefs.edit().putBoolean("confirm_recovery", it).apply() } }
        item { SettingSwitch("SHOW FILE PREVIEWS", previews) { previews = it; prefs.edit().putBoolean("previews", it).apply() } }
        item { SettingSwitch("SAVE RECOVERY HISTORY", saveHistory) { saveHistory = it; prefs.edit().putBoolean("save_history", it).apply() } }
        item {
            Card(Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteSweep, null, tint = Color(0xFFE74C3C)); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text("CLEAR RECOVERY HISTORY", fontWeight = FontWeight.Bold); Text("Remove saved scan and recovery activity.", style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = { prefs.edit().remove("recovery_history").remove("last_scan").remove("last_count").apply() }) { Text("CLEAR") }
                }
            }
        }
        item { Text("COLOR THEME", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                palettes.forEachIndexed { idx, colors ->
                    Button(onClick = { onThemeChange(idx) }, colors = ButtonDefaults.buttonColors(containerColor = colors[0]), modifier = Modifier.weight(1f)) {
                        Text(if (idx == theme) "✓" else idx.toString())
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("PRIVACY & SAFETY", fontWeight = FontWeight.Bold)
                    Text("SCANNING STAYS ON THE DEVICE AND USES ANDROID STORAGE PERMISSIONS.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            val allFiles = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
            Card(Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderOpen, null, tint = Color(0xFF4F7CFF)); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("ALL FILES ACCESS", fontWeight = FontWeight.Bold)
                            Text(if (allFiles) "ENABLED — Deep and document scans can access shared storage." else "Enable for Deep Recovery and document/file scanning.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 30 && !allFiles) Button(onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))) }
                    }, Modifier.fillMaxWidth()) { Text("OPEN STORAGE ACCESS SETTINGS") }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.rss_original_logo), "Razeen Secure Solution", Modifier.size(82.dp).clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.rsscctvsolution.eu.cc")))
                })
                Spacer(Modifier.height(4.dp)); Text("RAZEEN SECURE SOLUTION", fontWeight = FontWeight.ExtraBold)
                Text("Mobile & PC Software • CCTV Camera Installation • Networking • System Administration", style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("077 115 5504  •  070 155 5504", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text("rsscctvsolution@gmail.com", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text("www.rsscctvsolution.eu.cc", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (showPinDialog) {
        val available = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("APP LOCK SECURITY") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Create and verify a 6-digit PIN. Biometric unlock is optional.")
                    OutlinedTextField(pin, { v -> if (v.length <= 6 && v.all(Char::isDigit)) pin = v }, Modifier.fillMaxWidth(), label = { Text("6-DIGIT PIN") }, leadingIcon = { Icon(Icons.Default.Lock, null) }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    OutlinedTextField(confirmPin, { v -> if (v.length <= 6 && v.all(Char::isDigit)) confirmPin = v }, Modifier.fillMaxWidth(), label = { Text("VERIFY PIN") }, leadingIcon = { Icon(Icons.Default.VerifiedUser, null) }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    if (available) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Fingerprint, null, tint = Color(0xFF4F7CFF)); Spacer(Modifier.width(8.dp)); Text("BIOMETRIC UNLOCK", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Switch(checked = biometric, onCheckedChange = { biometric = it })
                    }
                }
            },
            confirmButton = {
                Button(enabled = pin.length == 6 && pin == confirmPin, onClick = {
                    prefs.edit().putString("pin_hash", hashPin(pin)).putBoolean("pin_enabled", true).putBoolean("app_lock", true).putBoolean("biometric_enabled", biometric && available).apply()
                    lock = true; showPinDialog = false; pin = ""; confirmPin = ""
                }) { Text("VERIFY & SAVE") }
            },
            dismissButton = { TextButton(onClick = { showPinDialog = false }) { Text("CANCEL") } }
        )
    }
}

@Composable
private fun AppFeaturesOnboarding(systemDark: Boolean, done: () -> Unit, skip: () -> Unit) {
    val features = listOf(
        Triple("SMART RECOVERY", "Quick and Deep recovery modes help you scan your device with the mode you choose.", Icons.Default.Restore),
        Triple("RECOVERY RESULTS", "Review scan counts, categories, and matching files from a dedicated results workspace.", Icons.Default.Assessment),
        Triple("PRIVACY & CONTROL", "App lock, biometric unlock, appearance controls, scan preferences, and recovery history stay under your control.", Icons.Default.Security)
    )
    var index by remember { mutableIntStateOf(0) }
    val transition = rememberInfiniteTransition(label = "featureMotion")
    val drift1 by transition.animateFloat(-70f, 70f, infiniteRepeatable(tween(4200), RepeatMode.Reverse), label = "drift1")
    val drift2 by transition.animateFloat(60f, -60f, infiniteRepeatable(tween(5200), RepeatMode.Reverse), label = "drift2")
    val drift3 by transition.animateFloat(-45f, 45f, infiniteRepeatable(tween(3600), RepeatMode.Reverse), label = "drift3")
    val scale by transition.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "featureScale")
    val backgrounds = if (systemDark) listOf(
        listOf(Color(0xFF07111F), Color(0xFF123B5D), Color(0xFF0A1020)),
        listOf(Color(0xFF0A1715), Color(0xFF12483C), Color(0xFF081311)),
        listOf(Color(0xFF17100A), Color(0xFF55351B), Color(0xFF110C09))
    ) else listOf(
        listOf(Color.White, Color(0xFFEAF5FF), Color.White),
        listOf(Color.White, Color(0xFFEAFBF6), Color.White),
        listOf(Color.White, Color(0xFFFFF5E8), Color.White)
    )
    Box(Modifier.fillMaxSize()) {
        androidx.compose.animation.Crossfade(targetState = index, animationSpec = tween(450), label = "featureBackground") { pageIndex ->
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(backgrounds[pageIndex]))) {
                Icon(features[pageIndex].third, null, Modifier.align(Alignment.Center).size(330.dp).graphicsLayer { alpha = if (systemDark) .045f else .035f }, tint = palettes[pageIndex % palettes.size][0])
            }
        }
        Icon(Icons.Default.Cloud, null, Modifier.offset(x = drift1.dp, y = (-150).dp).size(76.dp).graphicsLayer { alpha = .08f }, tint = palettes[1][0])
        Icon(Icons.Default.Folder, null, Modifier.align(Alignment.CenterStart).offset(x = drift2.dp, y = 30.dp).size(64.dp).graphicsLayer { alpha = .08f }, tint = palettes[2][0])
        Icon(Icons.Default.Security, null, Modifier.align(Alignment.BottomEnd).offset(x = drift3.dp, y = (-130).dp).size(70.dp).graphicsLayer { alpha = .08f }, tint = palettes[3][0])
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("RSS DATA RECOVERY", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            Text("APP FEATURES", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(28.dp))
            AnimatedContent(targetState = index, transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(180)) }, label = "featurePage") { pageIndex ->
                val feature = features[pageIndex]
                Column(Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }.padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Icon(feature.third, null, Modifier.size(86.dp), tint = palettes[pageIndex % palettes.size][0])
                    Text(feature.first, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(feature.second, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 24.sp)
                }
            }
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                features.indices.forEach { i -> Box(Modifier.size(if (i == index) 24.dp else 8.dp, 8.dp).clip(RoundedCornerShape(8.dp)).background(if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .25f))) }
            }
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = skip, Modifier.weight(1f)) { Text("SKIP FOR NOW") }
                Button(onClick = { if (index == features.lastIndex) done() else index++ }, Modifier.weight(1f)) { Text(if (index == features.lastIndex) "START RECOVERY" else "NEXT") }
            }
            Spacer(Modifier.height(10.dp))
            Text("${index + 1} / ${features.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun SettingSwitch(title: String, value: Boolean, onChange: (Boolean) -> Unit) { val icon=when{title.contains("DARK")->Icons.Default.DarkMode;title.contains("LOCK")->Icons.Default.Lock;title.contains("HAPTIC")->Icons.Default.Vibration;title.contains("NOTIFICATION")->Icons.Default.Notifications;title.contains("AUTO")->Icons.Default.PlayCircle;title.contains("CONFIRM")->Icons.Default.Verified;title.contains("PREVIEW")->Icons.Default.Visibility;else->Icons.Default.History};val tint=when{title.contains("DARK")->Color(0xFF8E6CFF);title.contains("LOCK")->Color(0xFFE74C3C);title.contains("HAPTIC")->Color(0xFF18B7A0);title.contains("NOTIFICATION")->Color(0xFFFFB21A);title.contains("AUTO")->Color(0xFF4F7CFF);title.contains("CONFIRM")->Color(0xFF27AE60);title.contains("PREVIEW")->Color(0xFF9B5CFF);else->Color(0xFF2980B9)};Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,Modifier.size(24.dp),tint=tint);Spacer(Modifier.width(10.dp));Text(title,Modifier.weight(1f),fontWeight=FontWeight.Medium);Switch(checked=value,onCheckedChange=onChange)}}

private fun hashPin(pin: String): String = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray()).joinToString("") { it.toString(16).padStart(2, '0') }

private fun scanNotification(context: Context, active: Boolean) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("rss_scan", "Recovery Scan", NotificationManager.IMPORTANCE_LOW))
    if (active) { val builder = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(context, "rss_scan") else android.app.Notification.Builder(context); builder.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("SCAN IN PROGRESS").setContentText("RSS Data Recovery is scanning").setOngoing(true); manager.notify(991, builder.build()) } else manager.cancel(991)
}
private fun storageUsage(context: Context): Triple<String, String, Float> {
    val root = android.os.Environment.getExternalStorageDirectory()
    val stat = android.os.StatFs(root.absolutePath)
    val total = stat.totalBytes.coerceAtLeast(1L)
    val free = stat.availableBytes.coerceIn(0L, total)
    val used = total - free
    return Triple(formatBytes(used), formatBytes(free), used.toFloat() / total.toFloat())
}

private fun formatBytes(value: Long): String = when { value < 1024 -> "$value B"; value < 1048576 -> "${value / 1024} KB"; value < 1073741824 -> "${value / 1048576} MB"; else -> "${value / 1073741824} GB" }

private suspend fun registerRecoveryCustomer(name: String, email: String) {
    runCatching {
        RssCoreClient.register(name, email)
    }
}