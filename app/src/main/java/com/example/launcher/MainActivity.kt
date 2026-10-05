package com.example.launcher

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class AppInfo(
    val label: String,
    val packageName: String,
    val launchIntent: Intent
)

// Inter — closest free approximation of iOS SF Pro
private val AppFont = FontFamily(Font(R.font.inter))

private val AppTypography = Typography().let { base ->
    base.copy(
        bodyLarge = base.bodyLarge.copy(fontFamily = AppFont, letterSpacing = 0.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = AppFont, letterSpacing = 0.sp),
        bodySmall = base.bodySmall.copy(fontFamily = AppFont, letterSpacing = 0.sp),
        labelLarge = base.labelLarge.copy(fontFamily = AppFont, letterSpacing = 0.sp),
        labelMedium = base.labelMedium.copy(fontFamily = AppFont, letterSpacing = 0.sp),
        labelSmall = base.labelSmall.copy(fontFamily = AppFont, letterSpacing = 0.sp),
        titleLarge = base.titleLarge.copy(fontFamily = AppFont, letterSpacing = 0.sp),
        titleMedium = base.titleMedium.copy(fontFamily = AppFont, letterSpacing = 0.sp),
        titleSmall = base.titleSmall.copy(fontFamily = AppFont, letterSpacing = 0.sp),
        headlineLarge = base.headlineLarge.copy(fontFamily = AppFont, letterSpacing = 0.sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = AppFont, letterSpacing = 0.sp),
        headlineSmall = base.headlineSmall.copy(fontFamily = AppFont, letterSpacing = 0.sp)
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(),
                typography = AppTypography
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                    LauncherApp()
                }
            }
        }
    }
}

@Composable
fun LauncherApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var hiddenApps by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loaded by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showHiddenSheet by remember { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<AppInfo?>(null) }

    LaunchedEffect(Unit) {
        val loadedApps = withContext(Dispatchers.Default) { loadLauncherApps(context) }
        val loadedHidden = withContext(Dispatchers.IO) { loadHiddenApps(context) }
        allApps = loadedApps
        hiddenApps = loadedHidden
        loaded = true
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { ShizukuHelper.ping() } }
    }

    DisposableEffect(Unit) {
        ShizukuHelper.addListeners()
        onDispose { ShizukuHelper.removeListeners() }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                if (intent.action == Intent.ACTION_PACKAGE_REMOVED &&
                    intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                ) return

                scope.launch {
                    delay(350)
                    val newApps = withContext(Dispatchers.Default) { loadLauncherApps(context) }
                    allApps = newApps
                    // Clean stale hidden entries
                    val valid = newApps.mapTo(HashSet()) { it.packageName }
                    val cleanedHidden = hiddenApps.filter { it in valid }.toSet()
                    if (cleanedHidden != hiddenApps) {
                        hiddenApps = cleanedHidden
                        saveHiddenApps(context, cleanedHidden)
                    }
                    val target = menuTarget
                    if (target != null && newApps.none { it.packageName == target.packageName }) {
                        menuTarget = null
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    BackHandler(enabled = true) {
        when {
            showSettings -> showSettings = false
            showHiddenSheet -> showHiddenSheet = false
            menuTarget != null -> menuTarget = null
            showSearch -> showSearch = false
        }
    }

    val visibleApps = remember(allApps, hiddenApps) {
        allApps.filter { it.packageName !in hiddenApps }
    }

    val updateHidden: (Set<String>) -> Unit = remember(context) {
        { newHidden ->
            hiddenApps = newHidden
            saveHiddenApps(context, newHidden)
        }
    }

    val launchHandler: (AppInfo) -> Unit = remember(context) {
        { app -> launchApp(context, app) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.22f))
    ) {
        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            AnimatedContent(
                targetState = showSearch,
                transitionSpec = {
                    fadeIn(tween(200, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(tween(160, easing = FastOutSlowInEasing))
                },
                label = "screen",
                modifier = Modifier.fillMaxSize()
            ) { searching ->
                if (searching) {
                    SearchScreen(
                        allApps = visibleApps,
                        onAppClick = launchHandler,
                        onAppLongClick = { menuTarget = it },
                        onClose = { showSearch = false }
                    )
                } else {
                    HomeScreen(
                        allApps = visibleApps,
                        onAppClick = launchHandler,
                        onAppLongClick = { menuTarget = it },
                        onSearchClick = { showSearch = true },
                        onSettingsClick = { showSettings = true },
                        onBlankLongPress = { showHiddenSheet = true }
                    )
                }
            }
        }
    }

    menuTarget?.let { app ->
        AppContextMenu(
            app = app,
            onHide = {
                updateHidden(hiddenApps + app.packageName)
                menuTarget = null
            },
            onUninstall = {
                uninstallApp(context, app)
                menuTarget = null
            },
            onAppInfo = {
                openAppInfo(context, app)
                menuTarget = null
            },
            onDismiss = { menuTarget = null }
        )
    }

    if (showSettings) {
        ShizukuSettingsDialog(onDismiss = { showSettings = false })
    }

    if (showHiddenSheet) {
        HiddenAppsDialog(
            allApps = allApps,
            hiddenApps = hiddenApps,
            onSave = { newHidden ->
                updateHidden(newHidden)
                showHiddenSheet = false
            },
            onDismiss = { showHiddenSheet = false }
        )
    }
}

// ---------- Home ----------

@Composable
fun HomeScreen(
    allApps: List<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBlankLongPress: () -> Unit
) {
    val sortedApps = remember(allApps) {
        allApps.sortedBy { it.label.lowercase() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onBlankLongPress() })
            }
    ) {
        AppList(
            apps = sortedApps,
            onAppClick = onAppClick,
            onAppLongClick = onAppLongClick
        )

        EdgeScrim(top = true, modifier = Modifier.align(Alignment.TopCenter))
        EdgeScrim(top = false, modifier = Modifier.align(Alignment.BottomCenter))

        TopPill(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 14.dp)
        ) {
            Text("Apps", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.width(8.dp))
            Text(
                "· ${sortedApps.size}",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.55f)
            )
            Spacer(Modifier.width(14.dp))
            IconButtonCircle(
                icon = {
                    Icon(Icons.Default.Search, "Search", tint = Color.White, modifier = Modifier.size(18.dp))
                },
                onClick = onSearchClick
            )
            Spacer(Modifier.width(8.dp))
            IconButtonCircle(
                icon = {
                    Icon(Icons.Default.Settings, "Settings", tint = Color.White, modifier = Modifier.size(18.dp))
                },
                onClick = onSettingsClick
            )
        }
    }
}

// ---------- Search ----------

@Composable
fun SearchScreen(
    allApps: List<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    val filtered by remember(query, allApps) {
        derivedStateOf {
            if (query.isBlank()) emptyList()
            else allApps
                .filter { it.label.contains(query, ignoreCase = true) }
                .sortedBy { it.label.lowercase() }
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f))
    ) {
        AppList(
            apps = filtered,
            onAppClick = onAppClick,
            onAppLongClick = onAppLongClick
        )

        EdgeScrim(top = true, modifier = Modifier.align(Alignment.TopCenter))
        EdgeScrim(top = false, modifier = Modifier.align(Alignment.BottomCenter))

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 14.dp)
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.14f))
                .padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 15.sp,
                    fontFamily = AppFont
                ),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text("Search apps", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp)
                    }
                    inner()
                }
            )
            Spacer(Modifier.width(10.dp))
            IconButtonCircle(
                icon = {
                    Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                },
                onClick = onClose
            )
        }
    }
}

// ---------- Shared app list ----------

@Composable
private fun AppList(
    apps: List<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 140.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(
            items = apps,
            key = { it.packageName },
            contentType = { "app" }
        ) { app ->
            AppRow(
                label = app.label,
                onClick = { onAppClick(app) },
                onLongClick = { onAppLongClick(app) }
            )
        }
    }
}

// ---------- Modals ----------

@Composable
fun AppContextMenu(
    app: AppInfo,
    onHide: () -> Unit,
    onUninstall: () -> Unit,
    onAppInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                app.label,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column {
                DialogMenuRow("Hide app") { onHide() }
                DialogMenuRow("App info") { onAppInfo() }
                DialogMenuRow("Uninstall") { onUninstall() }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

@Composable
fun HiddenAppsDialog(
    allApps: List<AppInfo>,
    hiddenApps: Set<String>,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val hiddenList = remember(allApps, hiddenApps) {
        allApps.filter { it.packageName in hiddenApps }
            .sortedBy { it.label.lowercase() }
    }
    var selected by remember { mutableStateOf(hiddenApps) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Hidden apps",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                if (hiddenList.isEmpty()) {
                    Text(
                        "No apps are hidden.\nLong-press any app and tap Hide.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        "Uncheck to show an app again",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.height(360.dp)) {
                        items(hiddenList, key = { it.packageName }) { app ->
                            val checked = app.packageName in selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selected = if (checked) selected - app.packageName
                                        else selected + app.packageName
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { c ->
                                        selected = if (c) selected + app.packageName
                                        else selected - app.packageName
                                    }
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    app.label,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (hiddenList.isNotEmpty()) {
                TextButton(onClick = { onSave(selected) }) {
                    Text("Save", color = Color.White)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("OK", color = Color.White)
                }
            }
        },
        dismissButton = {
            if (hiddenList.isNotEmpty()) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    )
}

@Composable
fun DialogMenuRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 15.sp,
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp)
    )
}

// ---------- Visual components ----------

@Composable
fun EdgeScrim(top: Boolean, modifier: Modifier = Modifier) {
    val colors = remember {
        listOf(
            Color.Black.copy(alpha = 0.48f),
            Color.Black.copy(alpha = 0.28f),
            Color.Black.copy(alpha = 0.10f),
            Color.Transparent
        )
    }
    val brush = remember(top) {
        val ordered = if (top) colors else colors.reversed()
        Brush.verticalGradient(colors = ordered)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(brush)
    )
}

@Composable
fun TopPill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(50)
    val fillBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.30f),
                Color.White.copy(alpha = 0.12f)
            )
        )
    }
    val borderBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.50f),
                Color.White.copy(alpha = 0.06f)
            )
        )
    }
    Row(
        modifier = modifier
            .shadow(
                elevation = 18.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.55f),
                spotColor = Color.Black.copy(alpha = 0.55f)
            )
            .clip(shape)
            .background(fillBrush)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
fun IconButtonCircle(icon: @Composable () -> Unit, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(label: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            // Very subtle background — pill barely visible
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.05f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

// ---------- Shizuku Settings ----------

@Composable
fun ShizukuSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(ShizukuHelper.currentState(context)) }
    var busy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        state = withContext(Dispatchers.IO) { ShizukuHelper.currentState(context) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Gesture navigation", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column {
                val statusText = when (state) {
                    ShizukuHelper.State.NotInstalled -> "Shizuku not installed"
                    ShizukuHelper.State.NotRunning -> "Shizuku not running"
                    ShizukuHelper.State.PermissionRequired -> "Permission required"
                    ShizukuHelper.State.Ready -> "Shizuku ready"
                }
                val statusColor = when (state) {
                    ShizukuHelper.State.Ready -> Color(0xFF3DDC84)
                    ShizukuHelper.State.NotInstalled -> Color(0xFFFF6B6B)
                    else -> Color(0xFFFFC107)
                }
                Text(statusText, color = statusColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(12.dp))

                val hint = when (state) {
                    ShizukuHelper.State.NotInstalled -> "Install Shizuku, then reopen this dialog."
                    ShizukuHelper.State.NotRunning -> "Open Shizuku and start the service. Then reopen."
                    ShizukuHelper.State.PermissionRequired -> "Grant this launcher Shizuku permission."
                    ShizukuHelper.State.Ready -> "Enable gesture navigation and hide the Android nav bar."
                }
                Text(hint, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)

                if (log.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Last run:", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    log.forEach { line ->
                        Text(line, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(vertical = 1.dp))
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                ShizukuHelper.State.PermissionRequired -> TextButton(onClick = {
                    ShizukuHelper.requestPermission()
                    scope.launch {
                        delay(500)
                        state = withContext(Dispatchers.IO) { ShizukuHelper.currentState(context) }
                    }
                }) { Text("Grant permission", color = Color.White) }

                ShizukuHelper.State.Ready -> TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            log = ShizukuHelper.enableGestures()
                            busy = false
                            delay(400)
                            onDismiss()
                        }
                    }
                ) { Text(if (busy) "Applying…" else "Enable gestures", color = Color.White) }

                else -> TextButton(onClick = onDismiss) { Text("OK", color = Color.White) }
            }
        },
        dismissButton = {
            when (state) {
                ShizukuHelper.State.Ready -> TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            log = ShizukuHelper.disableGestures()
                            busy = false
                            state = withContext(Dispatchers.IO) { ShizukuHelper.currentState(context) }
                        }
                    }
                ) { Text("Disable", color = Color.White.copy(alpha = 0.7f)) }

                ShizukuHelper.State.NotInstalled -> {}

                else -> TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(alpha = 0.7f)) }
            }
        }
    )
}

// ---------- Utility ----------

fun launchApp(context: Context, app: AppInfo) {
    val intent = app.launchIntent
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    context.startActivity(intent)
}

fun uninstallApp(context: Context, app: AppInfo) {
    val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

fun openAppInfo(context: Context, app: AppInfo) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${app.packageName}")
    )
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

@Suppress("DEPRECATION")
fun loadLauncherApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    val result = ArrayList<AppInfo>(96)
    val seen = HashSet<String>(96)

    pm.queryIntentActivities(mainIntent, 0).forEach { resolveInfo ->
        val activityInfo = resolveInfo.activityInfo ?: return@forEach
        val appInfo = activityInfo.applicationInfo ?: return@forEach
        val packageName = appInfo.packageName
        if (!seen.add(packageName)) return@forEach

        val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: Intent().apply {
            component = ComponentName(packageName, activityInfo.name)
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        result += AppInfo(
            label = pm.getApplicationLabel(appInfo).toString(),
            packageName = packageName,
            launchIntent = launchIntent
        )
    }

    result.sortBy { it.label.lowercase() }
    return result
}

// ---------- Hidden apps persistence ----------

private fun loadHiddenApps(context: Context): Set<String> {
    val prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
    return prefs.getStringSet("hidden_apps", emptySet()) ?: emptySet()
}

private fun saveHiddenApps(context: Context, hidden: Set<String>) {
    context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        .edit().putStringSet("hidden_apps", hidden).apply()
}
