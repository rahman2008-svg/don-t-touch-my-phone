package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.MainActivity
import com.example.R
import com.example.service.SecurityStateManager
import com.example.util.FrontCameraCapture
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Dynamic Theme Colors consistent with high-tech security vibe
val ThemeDarkBg = Color(0xFF0B0D0B)
val ThemeCardBg = Color(0xFF131713)
val ThemeOrangeAccent = Color(0xFFA7FF83)
val ThemeBlueAccent = Color(0xFF17B978)
val ThemeRedAlert = Color(0xFFFF3333)
val ThemeGreenSuccess = Color(0xFF17B978)
val ThemeTextPrimary = Color(0xFFECEFF1)
val ThemeTextSecondary = Color(0xB3FFFFFF)

@Composable
fun MainSecurityApp(viewModel: SecurityViewModel) {
    val currentScreen by viewModel.currentAppScreen.collectAsState()
    val isArmed by SecurityStateManager.isArmed.collectAsState()
    val isTriggered by SecurityStateManager.isTriggered.collectAsState()
    val settings by viewModel.settingsState.collectAsState()
    val lang = settings.language

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeDarkBg)
    ) {
        when (currentScreen) {
            "splash" -> SplashScreen(viewModel)
            "onboarding" -> OnboardingScreen(viewModel)
            "permissions" -> PermissionSetupScreen(viewModel)
            "main" -> {
                if (isTriggered) {
                    AlarmTriggeredScreen(viewModel)
                } else {
                    MainAppContainer(viewModel)
                }
            }
        }

        // Setup PIN overlay if PIN is not configured yet and user wants to activate
        val showPinSetup by viewModel.isPinSetupMode.collectAsState()
        if (showPinSetup) {
            PinSetupDialog(viewModel)
        }
    }
}

// 1. SPLASH SCREEN
@Composable
fun SplashScreen(viewModel: SecurityViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settingsState.collectAsState()
    val lang = settings.language

    var logoScale by remember { mutableStateOf(0.6f) }
    var loadingPercent by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        animate(
            initialValue = 0.6f,
            targetValue = 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        ) { value, _ ->
            logoScale = value
        }

        // Simulate loading
        while (loadingPercent < 1f) {
            delay(50)
            loadingPercent += 0.025f
        }

        // Navigate to appropriate screen
        if (settings.pin.isEmpty()) {
            viewModel.navigateTo("onboarding")
        } else {
            viewModel.navigateTo("main")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .scale(logoScale)
                .background(
                    Brush.radialGradient(
                        colors = listOf(ThemeOrangeAccent.copy(alpha = 0.3f), Color.Transparent)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Using the generated custom app icon if available, else standard fallback
            Image(
                painter = painterResource(id = R.drawable.img_app_icon_1782828399407),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(2.dp, ThemeOrangeAccent, RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = Localization.getString("app_name", lang),
            color = ThemeTextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = Localization.getString("tagline", lang),
            color = ThemeTextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(64.dp))

        LinearProgressIndicator(
            progress = { loadingPercent },
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(4.dp)
                .clip(CircleShape),
            color = ThemeOrangeAccent,
            trackColor = ThemeCardBg
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = Localization.getString("splash_loading", lang),
            color = ThemeTextSecondary,
            fontSize = 12.sp
        )
    }
}

// 2. ONBOARDING SCREEN
@Composable
fun OnboardingScreen(viewModel: SecurityViewModel) {
    val settings by viewModel.settingsState.collectAsState()
    val lang = settings.language

    var pageIndex by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    val pages = listOf(
        Triple(
            Localization.getString("onboarding_title_1", lang),
            Localization.getString("onboarding_desc_1", lang),
            Icons.Outlined.PhonelinkRing
        ),
        Triple(
            Localization.getString("onboarding_title_2", lang),
            Localization.getString("onboarding_desc_2", lang),
            Icons.Outlined.PhotoCamera
        ),
        Triple(
            Localization.getString("onboarding_title_3", lang),
            Localization.getString("onboarding_desc_3", lang),
            Icons.Outlined.BatteryAlert
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .navigationBarsPadding()
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${pageIndex + 1}/3",
                color = ThemeTextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            TextButton(
                onClick = { viewModel.navigateTo("permissions") },
                modifier = Modifier.testTag("skip_onboarding_button")
            ) {
                Text(
                    text = Localization.getString("skip", lang),
                    color = ThemeOrangeAccent,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.1f))

        // Onboarding Graphics / Icon
        Box(
            modifier = Modifier
                .size(240.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(ThemeBlueAccent.copy(alpha = 0.15f), Color.Transparent)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .size(180.dp)
                    .shadow(16.dp, RoundedCornerShape(32.dp)),
                colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
                shape = RoundedCornerShape(32.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = pages[pageIndex].third,
                        contentDescription = "Graphic",
                        tint = ThemeOrangeAccent,
                        modifier = Modifier.size(80.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.1f))

        // Texts with simple crossfade transition
        Crossfade(targetState = pageIndex, label = "OnboardingCrossfade") { index ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = pages[index].first,
                    color = ThemeTextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = pages[index].second,
                    color = ThemeTextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.1f))

        // Progress indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .size(width = if (i == pageIndex) 24.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape)
                        .background(if (i == pageIndex) ThemeOrangeAccent else ThemeCardBg)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Continue Button
        Button(
            onClick = {
                if (pageIndex < 2) {
                    pageIndex++
                } else {
                    viewModel.navigateTo("permissions")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("onboarding_continue_btn"),
            colors = ButtonDefaults.buttonColors(containerColor = ThemeOrangeAccent),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (pageIndex < 2) Localization.getString("next", lang) else Localization.getString("get_started", lang),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// 3. PERMISSION SETUP SCREEN
@Composable
fun PermissionSetupScreen(viewModel: SecurityViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settingsState.collectAsState()
    val lang = settings.language

    // Check system permissions
    var hasCamera by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var hasNotifications by remember { mutableStateOf(true) }
    var hasMic by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        hasNotifications = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasNotifications = granted
    }

    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMic = granted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = Localization.getString("permissions_title", lang),
            color = ThemeTextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = Localization.getString("permissions_desc", lang),
            color = ThemeTextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Permissions Checklist Cards
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Camera Card
            PermissionRow(
                title = Localization.getString("perm_camera", lang),
                description = Localization.getString("perm_camera_desc", lang),
                icon = Icons.Default.CameraAlt,
                isGranted = hasCamera,
                onRequest = { cameraLauncher.launch(Manifest.permission.CAMERA) }
            )

            // Notifications Card
            PermissionRow(
                title = Localization.getString("perm_notification", lang),
                description = Localization.getString("perm_notification_desc", lang),
                icon = Icons.Default.NotificationsActive,
                isGranted = hasNotifications,
                onRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )

            // Microphone Card
            PermissionRow(
                title = Localization.getString("perm_audio", lang),
                description = Localization.getString("perm_audio_desc", lang),
                icon = Icons.Default.Mic,
                isGranted = hasMic,
                onRequest = { audioLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Continue Button
        Button(
            onClick = {
                // If PIN is not configured yet, direct user to PIN setup flow. Else go straight to main!
                if (settings.pin.isEmpty()) {
                    viewModel.savePIN("1234") // set up a simple default PIN so they are fully armed, but they can update in Settings!
                    Toast.makeText(context, Localization.getString("pin_set_success", lang) + " (Default: 1234)", Toast.LENGTH_LONG).show()
                }
                viewModel.navigateTo("main")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("permissions_continue_btn"),
            colors = ButtonDefaults.buttonColors(containerColor = ThemeOrangeAccent),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = Localization.getString("continue_btn", lang),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isGranted) ThemeGreenSuccess.copy(alpha = 0.5f) else Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) ThemeGreenSuccess.copy(alpha = 0.15f) else ThemeOrangeAccent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Perm Icon",
                    tint = if (isGranted) ThemeGreenSuccess else ThemeOrangeAccent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = ThemeTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = ThemeTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = ThemeGreenSuccess,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Button(
                    onClick = onRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeOrangeAccent.copy(alpha = 0.15f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = "Grant",
                        color = ThemeOrangeAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// 4. MAIN APP CONTAINER WITH NAVIGATION
@Composable
fun MainAppContainer(viewModel: SecurityViewModel) {
    val settings by viewModel.settingsState.collectAsState()
    val lang = settings.language

    var currentTab by remember { mutableStateOf("home") } // home, modes, photos, history, settings

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = ThemeCardBg,
                tonalElevation = 8.dp
            ) {
                val tabs = listOf(
                    Quadruple("home", Localization.getString("tab_home", lang), Icons.Default.Home, Icons.Outlined.Home),
                    Quadruple("modes", Localization.getString("tab_modes", lang), Icons.Default.GridOn, Icons.Outlined.GridOn),
                    Quadruple("photos", Localization.getString("tab_photos", lang), Icons.Default.PhotoCamera, Icons.Outlined.PhotoCamera),
                    Quadruple("history", Localization.getString("tab_history", lang), Icons.Default.History, Icons.Outlined.History),
                    Quadruple("settings", Localization.getString("tab_settings", lang), Icons.Default.Settings, Icons.Outlined.Settings)
                )

                tabs.forEach { tab ->
                    val isSelected = currentTab == tab.first
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab.first },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.third else tab.fourth,
                                contentDescription = tab.second
                            )
                        },
                        label = {
                            Text(
                                text = tab.second,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ThemeOrangeAccent,
                            selectedTextColor = ThemeOrangeAccent,
                            indicatorColor = ThemeOrangeAccent.copy(alpha = 0.1f),
                            unselectedIconColor = ThemeTextSecondary,
                            unselectedTextColor = ThemeTextSecondary
                        )
                    )
                }
            }
        },
        containerColor = ThemeDarkBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                "home" -> HomeTab(viewModel)
                "modes" -> ModesTab(viewModel)
                "photos" -> PhotosTab(viewModel)
                "history" -> HistoryTab(viewModel)
                "settings" -> SettingsTab(viewModel)
            }
        }
    }
}

// 4.1. HOME TAB
@Composable
fun HomeTab(viewModel: SecurityViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settingsState.collectAsState()
    val lang = settings.language
    val isArmed by SecurityStateManager.isArmed.collectAsState()
    val activeMode by SecurityStateManager.activeMode.collectAsState()

    var showDisarmDialog by remember { mutableStateOf(false) }

    // Pulsing animations for the large active button
    val infiniteTransition = rememberInfiniteTransition(label = "PulsingRadius")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isArmed) 1.15f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = Localization.getString("app_name", lang),
                    color = ThemeTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "SECURITY SUITE v1.0.0",
                    color = ThemeOrangeAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                )
                Text(
                    text = if (isArmed) Localization.getString("status_armed", lang) else Localization.getString("status_disarmed", lang),
                    color = if (isArmed) ThemeGreenSuccess else ThemeRedAlert,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.1f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isArmed) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Lock State",
                    tint = if (isArmed) ThemeGreenSuccess else ThemeOrangeAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Hero Graphic Banner from image generation!
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ThemeCardBg)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_banner_1782828415857),
                    contentDescription = "Cyber Security Banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Shadow overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(ThemeCardBg.copy(alpha = 0.9f), Color.Transparent)
                            )
                        )
                    )
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(16.dp)
                        .width(200.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "NexVora Shield Engine",
                        color = ThemeOrangeAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Autonomous anti-theft & kinetic safety monitoring.",
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Large Activate Button
        Box(
            modifier = Modifier
                .size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outermost decorative Ring (glowing / pulsing)
            Box(
                modifier = Modifier
                    .size(225.dp)
                    .scale(pulseScale)
                    .border(
                        width = 1.dp,
                        color = (if (isArmed) ThemeGreenSuccess else ThemeOrangeAccent).copy(alpha = 0.1f),
                        shape = CircleShape
                    )
            )

            // Middle decorative Ring
            Box(
                modifier = Modifier
                    .size(195.dp)
                    .border(
                        width = 1.6.dp,
                        color = (if (isArmed) ThemeGreenSuccess else ThemeOrangeAccent).copy(alpha = 0.25f),
                        shape = CircleShape
                    )
            )

            // Inner Button
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .clip(CircleShape)
                    .background(ThemeDarkBg)
                    .border(
                        width = 3.dp,
                        color = if (isArmed) ThemeGreenSuccess else ThemeOrangeAccent,
                        shape = CircleShape
                    )
                    .clickable {
                        if (isArmed) {
                            showDisarmDialog = true
                        } else {
                            viewModel.toggleArm(activeMode ?: "Don't Touch Mode", context)
                        }
                    }
                    .testTag("activate_security_system_button"),
                contentAlignment = Alignment.Center
            ) {
                // Subtle central gradient glow
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    (if (isArmed) ThemeGreenSuccess else ThemeOrangeAccent).copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (isArmed) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Shield",
                        tint = if (isArmed) ThemeGreenSuccess else ThemeOrangeAccent,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isArmed) Localization.getString("deactivate", lang) else Localization.getString("activate", lang),
                        color = if (isArmed) ThemeGreenSuccess else ThemeOrangeAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Selected mode quick display
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(ThemeCardBg)
                .border(1.dp, ThemeOrangeAccent.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (activeMode) {
                    "Pocket Mode" -> Icons.Default.PowerInput
                    "Charging Alarm" -> Icons.Default.Power
                    "Who Touched My Phone" -> Icons.Default.CameraAlt
                    "Clap/Whistle" -> Icons.Default.RecordVoiceOver
                    else -> Icons.Default.PhonelinkRing
                },
                contentDescription = "Active Mode Icon",
                tint = ThemeOrangeAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${Localization.getString("select_mode", lang)}: ${activeMode ?: "Don't Touch"}",
                color = ThemeTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Disarm PIN dialog
    if (showDisarmDialog) {
        DisarmPinDialog(
            viewModel = viewModel,
            onDismiss = { showDisarmDialog = false }
        )
    }
}

// 4.2. MODES TAB
@Composable
fun ModesTab(viewModel: SecurityViewModel) {
    val settings by viewModel.settingsState.collectAsState()
    val lang = settings.language
    val activeMode by SecurityStateManager.activeMode.collectAsState()
    val isArmed by SecurityStateManager.isArmed.collectAsState()

    val context = LocalContext.current

    val modes = listOf(
        Quadruple("Don't Touch Mode", Localization.getString("mode_dont_touch", lang), Localization.getString("mode_dont_touch_desc", lang), Icons.Outlined.PhonelinkLock),
        Quadruple("Motion Alarm", Localization.getString("mode_motion", lang), Localization.getString("mode_motion_desc", lang), Icons.Outlined.SettingsInputAntenna),
        Quadruple("Pocket Mode", Localization.getString("mode_pocket", lang), Localization.getString("mode_pocket_desc", lang), Icons.Outlined.PowerInput),
        Quadruple("Charging Alarm", Localization.getString("mode_charging", lang), Localization.getString("mode_charging_desc", lang), Icons.Outlined.Power),
        Quadruple("Who Touched My Phone", Localization.getString("mode_who_touch", lang), Localization.getString("mode_who_touch_desc", lang), Icons.Outlined.PhotoCamera),
        Quadruple("Clap/Whistle", Localization.getString("mode_clap_whistle", lang), Localization.getString("mode_clap_whistle_desc", lang), Icons.Outlined.RecordVoiceOver)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        item {
            Text(
                text = Localization.getString("select_mode", lang),
                color = ThemeTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select a passive trigger source below before arming",
                color = ThemeTextSecondary,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(modes) { mode ->
            val isSelected = activeMode == mode.first
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!isArmed) {
                            SecurityStateManager.setActiveMode(mode.first)
                            Toast
                                .makeText(
                                    context,
                                    "Selected: ${mode.second}",
                                    Toast.LENGTH_SHORT
                                )
                                .show()
                        } else {
                            Toast
                                .makeText(
                                    context,
                                    "Disarm first to switch modes",
                                    Toast.LENGTH_SHORT
                                )
                                .show()
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) ThemeOrangeAccent.copy(alpha = 0.08f) else ThemeCardBg
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) ThemeOrangeAccent.copy(alpha = 0.4f) else Color.Transparent
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) ThemeOrangeAccent.copy(alpha = 0.15f) else ThemeDarkBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = mode.fourth,
                            contentDescription = mode.second,
                            tint = if (isSelected) ThemeOrangeAccent else ThemeTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = mode.second,
                            color = ThemeTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = mode.third,
                            color = ThemeTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    RadioButton(
                        selected = isSelected,
                        onClick = {
                            if (!isArmed) {
                                SecurityStateManager.setActiveMode(mode.first)
                            } else {
                                Toast.makeText(context, "Disarm first to switch modes", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = ThemeOrangeAccent,
                            unselectedColor = ThemeTextSecondary
                        )
                    )
                }
            }
        }
    }
}

// 4.3. PHOTOS TAB
@Composable
fun PhotosTab(viewModel: SecurityViewModel) {
    val settings by viewModel.settingsState.collectAsState()
    val lang = settings.language
    val photos by viewModel.intruderPhotosState.collectAsState()

    var selectedPhotoUrl by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = Localization.getString("tab_photos", lang),
                    color = ThemeTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${photos.size} total images",
                    color = ThemeTextSecondary,
                    fontSize = 13.sp
                )
            }

            if (photos.isNotEmpty()) {
                TextButton(onClick = { viewModel.clearAllSelfies() }) {
                    Text(
                        text = Localization.getString("clear_all", lang),
                        color = ThemeOrangeAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (photos.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NoPhotography,
                    contentDescription = "No photos",
                    tint = ThemeTextSecondary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = Localization.getString("no_photos", lang),
                    color = ThemeTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = Localization.getString("photos_desc", lang),
                    color = ThemeTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(photos) { photo ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clickable { selectedPhotoUrl = photo.imagePath },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = ThemeCardBg)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Render direct image file path
                            val file = File(photo.imagePath)
                            if (file.exists()) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = "Intruder photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(ThemeCardBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = "Error",
                                        tint = ThemeTextSecondary
                                    )
                                }
                            }

                            // Info Overlay
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = photo.modeTriggered,
                                    color = ThemeOrangeAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = SimpleDateFormat("HH:mm, MMM d", Locale.getDefault()).format(Date(photo.timestamp)),
                                    color = Color.White,
                                    fontSize = 9.sp
                                )
                            }

                            // Delete button
                            IconButton(
                                onClick = { viewModel.deleteSelfie(photo.id) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(28.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = ThemeRedAlert,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // High resolution preview dialog
    if (selectedPhotoUrl != null) {
        Dialog(onDismissRequest = { selectedPhotoUrl = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(450.dp)
                    .padding(8.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = File(selectedPhotoUrl!!),
                        contentDescription = "High-Res Intruder Photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    IconButton(
                        onClick = { selectedPhotoUrl = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

// 4.4. ALARM HISTORY TAB
@Composable
fun HistoryTab(viewModel: SecurityViewModel) {
    val settings by viewModel.settingsState.collectAsState()
    val lang = settings.language
    val history by viewModel.alarmHistoryState.collectAsState()

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = Localization.getString("tab_history", lang),
                    color = ThemeTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${history.size} breach logs available",
                    color = ThemeTextSecondary,
                    fontSize = 13.sp
                )
            }

            if (history.isNotEmpty()) {
                TextButton(onClick = { viewModel.clearAllHistory() }) {
                    Text(
                        text = Localization.getString("clear_all", lang),
                        color = ThemeOrangeAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (history.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "No History",
                    tint = ThemeTextSecondary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = Localization.getString("no_history", lang),
                    color = ThemeTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = Localization.getString("history_desc", lang),
                    color = ThemeTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        } else {
            Button(
                onClick = {
                    // Export log logic
                    val data = StringBuilder()
                    history.forEach {
                        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it.timestamp))
                        data.append("[$date] - Breach in Mode: ${it.mode} (Selfie Captured: ${it.wasIntruderCaptured})\n")
                    }
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, data.toString())
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "Export Security Logs")
                    context.startActivity(shareIntent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ThemeCardBg),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ThemeOrangeAccent.copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = ThemeOrangeAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Localization.getString("export_logs", lang),
                    color = ThemeOrangeAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(history) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(ThemeRedAlert.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ReportGmailerrorred,
                                    contentDescription = "Breach Log",
                                    tint = ThemeRedAlert,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = log.mode,
                                    color = ThemeTextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = SimpleDateFormat("MMM d, yyyy - hh:mm:ss a", Locale.getDefault()).format(Date(log.timestamp)),
                                    color = ThemeTextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            if (log.wasIntruderCaptured) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ThemeGreenSuccess.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "SELFIE",
                                        color = ThemeGreenSuccess,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 4.5. SETTINGS TAB WITH DEVELOPER PROFILE DETAILS
@Composable
fun SettingsTab(viewModel: SecurityViewModel) {
    val settings by viewModel.settingsState.collectAsState()
    val lang = settings.language

    val context = LocalContext.current

    var selectedSiren by remember { mutableStateOf(settings.selectedSound) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = Localization.getString("tab_settings", lang),
            color = ThemeTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        // Alarm Config Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sound Select
                Column {
                    Text(
                        text = Localization.getString("sound_select_title", lang),
                        color = ThemeTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val sirens = listOf("Siren", "Police", "Laser", "Digital")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sirens.forEach { siren ->
                            val isChosen = selectedSiren == siren
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isChosen) ThemeOrangeAccent else ThemeDarkBg)
                                    .clickable {
                                        selectedSiren = siren
                                        viewModel.updateSound(siren)
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = siren,
                                    color = if (isChosen) Color.White else ThemeTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Divider(color = ThemeDarkBg, thickness = 1.dp)

                // Sensitivity slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = Localization.getString("sensitivity", lang),
                            color = ThemeTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${settings.alarmSensitivity.toInt()}/10",
                            color = ThemeOrangeAccent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = settings.alarmSensitivity,
                        onValueChange = { viewModel.updateSensitivity(it) },
                        valueRange = 1f..10f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = ThemeOrangeAccent,
                            inactiveTrackColor = ThemeDarkBg,
                            thumbColor = ThemeOrangeAccent
                        )
                    )
                }

                Divider(color = ThemeDarkBg, thickness = 1.dp)

                // Flash Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Localization.getString("flash_alert", lang),
                        color = ThemeTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = settings.isFlashEnabled,
                        onCheckedChange = { viewModel.toggleFlash(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ThemeOrangeAccent,
                            checkedTrackColor = ThemeOrangeAccent.copy(alpha = 0.5f)
                        )
                    )
                }

                // Vibration Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Localization.getString("vibrate", lang),
                        color = ThemeTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = settings.isVibrationEnabled,
                        onCheckedChange = { viewModel.toggleVibration(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ThemeOrangeAccent,
                            checkedTrackColor = ThemeOrangeAccent.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }

        // Language Config Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = Localization.getString("language", lang),
                    color = ThemeTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (lang == "en") ThemeOrangeAccent else ThemeDarkBg)
                            .clickable { viewModel.updateLanguage("en") }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "English", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (lang == "bn") ThemeOrangeAccent else ThemeDarkBg)
                            .clickable { viewModel.updateLanguage("bn") }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "বাংলা", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Developer Profile Section (As requested)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = Localization.getString("dev_profile", lang),
                    color = ThemeOrangeAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar Placeholder
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(ThemeOrangeAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Dev Avatar",
                            tint = ThemeOrangeAccent,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = Localization.getString("dev_name", lang),
                            color = ThemeTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = Localization.getString("dev_title", lang),
                            color = ThemeTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Text(
                    text = Localization.getString("dev_desc", lang),
                    color = ThemeTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )

                Divider(color = ThemeDarkBg, thickness = 1.dp)

                Text(
                    text = Localization.getString("contact_me", lang),
                    color = ThemeTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                // Social Click Action rows
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ContactButton(
                        text = "WhatsApp 1",
                        icon = Icons.Default.Chat,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=8801707424006"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                    )

                    ContactButton(
                        text = "WhatsApp 2",
                        icon = Icons.Default.Chat,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=8801796951709"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ContactButton(
                        text = "Facebook",
                        icon = Icons.Default.ThumbUp,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/share/1BNn32qoJo/"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                    )

                    ContactButton(
                        text = "Instagram",
                        icon = Icons.Default.CameraAlt,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/ur___abdur____rahman__2008"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // About Company Section (As requested)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = Localization.getString("about_company", lang),
                    color = ThemeOrangeAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "NexVora Lab's Ofc",
                    color = ThemeTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = Localization.getString("company_desc", lang),
                    color = ThemeTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )

                Text(
                    text = Localization.getString("company_mission", lang),
                    color = ThemeTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp
                )
            }
        }

        // Version & Credits
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = Localization.getString("credits", lang),
                    color = ThemeOrangeAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                // Immersive Developer Badge Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ThemeOrangeAccent.copy(alpha = 0.15f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "AR",
                            color = ThemeOrangeAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Prince AR Abdur Rahman",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "NexVora Lab's Independent Developer",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Developer Info",
                        tint = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Version 1.0.0",
                        color = ThemeOrangeAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "© 2026 NexVora Lab's Ofc",
                        color = ThemeTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ContactButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = ThemeDarkBg),
        border = BorderStroke(1.dp, ThemeOrangeAccent.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        modifier = modifier.height(38.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = ThemeOrangeAccent,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = ThemeTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// 5. ALARM TRIGGERED SCREEN WITH PIN PAD AND FRONT SELFIE CAMERA
@Composable
fun AlarmTriggeredScreen(viewModel: SecurityViewModel) {
    val settings by viewModel.settingsState.collectAsState()
    val lang = settings.language
    val triggerMode by SecurityStateManager.triggerMode.collectAsState()
    val pinBuffer by viewModel.pinBuffer.collectAsState()

    val context = LocalContext.current

    var isErrorState by remember { mutableStateOf(false) }

    // Front Camera silent image snap
    FrontCameraCapture { photoPath ->
        // Success snap -> log it into repository
        viewModel.addIntruderSelfie(photoPath, triggerMode ?: "System")
    }

    // Rapid alternating warning flash (Red and Blue)
    val infiniteTransition = rememberInfiniteTransition(label = "AlertFlash")
    val alertColor by infiniteTransition.animateColor(
        initialValue = ThemeRedAlert,
        targetValue = ThemeBlueAccent,
        animationSpec = infiniteRepeatable(
            animation = tween(250, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flash"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(alertColor.copy(alpha = 0.2f), ThemeDarkBg),
                        startY = 0f,
                        endY = size.height
                    )
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Warning Icon & Alarm status
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(ThemeRedAlert.copy(alpha = 0.2f))
                    .border(2.dp, alertColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "Warning Siren",
                    tint = ThemeRedAlert,
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = Localization.getString("alarm_triggered", lang),
                color = ThemeRedAlert,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${Localization.getString("breach_detected", lang)}: ${triggerMode ?: "Unknown Mode"}",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        // Numeric entry indicators
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isErrorState) Localization.getString("pin_incorrect", lang) else Localization.getString("enter_pin_deactivate", lang),
                color = if (isErrorState) ThemeRedAlert else ThemeTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // PIN circles
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(4) { index ->
                    val hasDigit = pinBuffer.length > index
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (hasDigit) {
                                    if (isErrorState) ThemeRedAlert else ThemeOrangeAccent
                                } else {
                                    ThemeCardBg
                                }
                            )
                            .border(
                                1.5.dp,
                                if (isErrorState) ThemeRedAlert else ThemeOrangeAccent.copy(alpha = 0.5f),
                                CircleShape
                            )
                    )
                }
            }
        }

        // Numeric PIN Keypad
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = ThemeCardBg.copy(alpha = 0.85f)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, ThemeOrangeAccent.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("DEL", "0", "OK")
                )

                keys.forEach { rowKeys ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowKeys.forEach { key ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1.8f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        when (key) {
                                            "OK" -> ThemeOrangeAccent
                                            "DEL" -> ThemeRedAlert.copy(alpha = 0.1f)
                                            else -> ThemeDarkBg
                                        }
                                    )
                                    .clickable {
                                        isErrorState = false
                                        when (key) {
                                            "DEL" -> viewModel.onPinDelete()
                                            "OK" -> {
                                                if (pinBuffer == settings.pin) {
                                                    viewModel.disarmSystem(context)
                                                    viewModel.clearPinBuffer()
                                                    Toast.makeText(context, "Alarm Deactivated", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    isErrorState = true
                                                    viewModel.clearPinBuffer()
                                                }
                                            }
                                            else -> {
                                                viewModel.onPinKeyPress(key)
                                                // Trigger automatic verify if they reach 4 digits
                                                if (pinBuffer.length == 4) {
                                                    // Let's verify instantly
                                                    if (pinBuffer == settings.pin) {
                                                        viewModel.disarmSystem(context)
                                                        viewModel.clearPinBuffer()
                                                        Toast.makeText(context, "Alarm Deactivated", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        isErrorState = true
                                                        viewModel.clearPinBuffer()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    .testTag("pin_key_$key"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = key,
                                    color = if (key == "OK") Color.White else ThemeTextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 6. SETUP PIN DIALOG FOR FIRST TIME ARM
@Composable
fun PinSetupDialog(viewModel: SecurityViewModel) {
    val settings by viewModel.settingsState.collectAsState()
    val lang = settings.language

    var inputPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirmStage by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { viewModel.savePIN("1234") }) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            border = BorderStroke(1.dp, ThemeOrangeAccent.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock icon",
                    tint = ThemeOrangeAccent,
                    modifier = Modifier.size(48.dp)
                )

                Text(
                    text = if (isConfirmStage) Localization.getString("confirm_pin", lang) else Localization.getString("setup_pin_title", lang),
                    color = ThemeTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = Localization.getString("setup_pin_desc", lang),
                    color = ThemeTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                // Render secure dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val currentVal = if (isConfirmStage) confirmPin else inputPin
                    repeat(4) { i ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (currentVal.length > i) ThemeOrangeAccent else ThemeDarkBg)
                                .border(1.dp, ThemeOrangeAccent.copy(alpha = 0.5f), CircleShape)
                        )
                    }
                }

                if (isError) {
                    Text(
                        text = "PINs do not match! Restarting.",
                        color = ThemeRedAlert,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Numeric keypad
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "OK")
                    )

                    rows.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { k ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(2f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ThemeDarkBg)
                                        .clickable {
                                            isError = false
                                            val current = if (isConfirmStage) confirmPin else inputPin
                                            when (k) {
                                                "C" -> {
                                                    if (isConfirmStage) confirmPin = "" else inputPin = ""
                                                }
                                                "OK" -> {
                                                    if (current.length == 4) {
                                                        if (!isConfirmStage) {
                                                            isConfirmStage = true
                                                        } else {
                                                            if (inputPin == confirmPin) {
                                                                viewModel.savePIN(inputPin)
                                                            } else {
                                                                isError = true
                                                                inputPin = ""
                                                                confirmPin = ""
                                                                isConfirmStage = false
                                                            }
                                                        }
                                                    }
                                                }
                                                else -> {
                                                    if (current.length < 4) {
                                                        if (isConfirmStage) {
                                                            confirmPin += k
                                                            if (confirmPin.length == 4) {
                                                                if (inputPin == confirmPin) {
                                                                    viewModel.savePIN(inputPin)
                                                                } else {
                                                                    isError = true
                                                                    inputPin = ""
                                                                    confirmPin = ""
                                                                    isConfirmStage = false
                                                                }
                                                            }
                                                        } else {
                                                            inputPin += k
                                                            if (inputPin.length == 4) {
                                                                isConfirmStage = true
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = k, color = ThemeTextPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 7. DISARM PIN POPUP DIALOG
@Composable
fun DisarmPinDialog(
    viewModel: SecurityViewModel,
    onDismiss: () -> Unit
) {
    val settings by viewModel.settingsState.collectAsState()
    val lang = settings.language
    val pinBuffer by viewModel.pinBuffer.collectAsState()

    val context = LocalContext.current
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = {
        viewModel.clearPinBuffer()
        onDismiss()
    }) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ThemeCardBg),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            border = BorderStroke(1.dp, ThemeOrangeAccent.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Unlock",
                    tint = ThemeOrangeAccent,
                    modifier = Modifier.size(48.dp)
                )

                Text(
                    text = Localization.getString("enter_pin", lang),
                    color = ThemeTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (isError) Localization.getString("pin_incorrect", lang) else "Enter PIN to disarm monitoring",
                    color = if (isError) ThemeRedAlert else ThemeTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = if (isError) FontWeight.Bold else FontWeight.Normal
                )

                // Dots indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(4) { i ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (pinBuffer.length > i) ThemeOrangeAccent else ThemeDarkBg)
                                .border(1.dp, ThemeOrangeAccent.copy(alpha = 0.5f), CircleShape)
                        )
                    }
                }

                // Numeric Keypad
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "OK")
                    )

                    rows.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { key ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(2.0f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ThemeDarkBg)
                                        .clickable {
                                            isError = false
                                            when (key) {
                                                "C" -> viewModel.onPinDelete()
                                                "OK" -> {
                                                    if (pinBuffer == settings.pin) {
                                                        viewModel.disarmSystem(context)
                                                        viewModel.clearPinBuffer()
                                                        onDismiss()
                                                    } else {
                                                        isError = true
                                                        viewModel.clearPinBuffer()
                                                    }
                                                }
                                                else -> {
                                                    viewModel.onPinKeyPress(key)
                                                    if (pinBuffer.length == 4) {
                                                        if (pinBuffer == settings.pin) {
                                                            viewModel.disarmSystem(context)
                                                            viewModel.clearPinBuffer()
                                                            onDismiss()
                                                        } else {
                                                            isError = true
                                                            viewModel.clearPinBuffer()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        .testTag("disarm_key_$key"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = key, color = ThemeTextPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Custom data container classes
data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
