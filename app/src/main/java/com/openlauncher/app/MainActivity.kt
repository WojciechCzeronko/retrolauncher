package com.openlauncher.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.here.sdk.core.engine.AuthenticationMode
import com.here.sdk.core.engine.SDKNativeEngine
import com.here.sdk.core.engine.SDKOptions
import com.here.sdk.core.errors.InstantiationErrorException
import com.openlauncher.app.data.GradientDirection
import com.openlauncher.app.model.NavDestination
import com.openlauncher.app.service.MediaReturnOverlayService
import com.openlauncher.app.service.NavigationForegroundService
import com.openlauncher.app.ui.screen.AppLibraryScreen
import com.openlauncher.app.ui.screen.HomeScreen
import com.openlauncher.app.ui.screen.OnboardingScreen
import com.openlauncher.app.ui.screen.SettingsScreen
import com.openlauncher.app.ui.screen.aw11.Aw11Shell
import com.openlauncher.app.ui.theme.Aw11Background
import com.openlauncher.app.ui.theme.OpenLauncherTheme
import com.openlauncher.app.viewmodel.LauncherViewModel
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private enum class CarPowerState {
        ACTIVE,
        GRACE_PERIOD,
        PARKED
    }

    private var carPowerState: CarPowerState? = null
    private var carPowerGraceJob: Job? = null

    private companion object {
        const val CAR_POWER_GRACE_PERIOD_MS =
            45_000L

        const val CAR_POWER_TAG =
            "CarPower"
    }
    private val vm: LauncherViewModel by viewModels()
    private var pendingMediaPackage: String? = null
    private var keepLocationInBackground = false

    private var lastExternalPower: Boolean? = null

    private val carPowerReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context,
                intent: Intent
            ) {
                if (
                    intent.action !=
                    Intent.ACTION_BATTERY_CHANGED
                ) {
                    return
                }

                val plugged =
                    intent.getIntExtra(
                        BatteryManager.EXTRA_PLUGGED,
                        0
                    )

                val externalPower =
                    plugged != 0

                if (
                    lastExternalPower ==
                    externalPower
                ) {
                    return
                }

                lastExternalPower =
                    externalPower

                handleExternalPowerChanged(
                    externalPower
                )
            }
        }

    private val overlayPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            val mediaPackage = pendingMediaPackage
            pendingMediaPackage = null

            if (!mediaPackage.isNullOrBlank()) {
                launchMediaWithReturnOverlay(mediaPackage)
            }
        }
    private val locationPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            startLocationWithPermissionCheck()
        }
    }

    private fun startLocationWithPermissionCheck() {
        val hasFine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            vm.startLocationUpdates()
        } else {
            // Request permissions if missing
            locationPermissions.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        registerReceiver(
            carPowerReceiver,
            IntentFilter(
                Intent.ACTION_BATTERY_CHANGED
            )
        )

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        initializeHereSdk()

        hideSystemBars()

        setContent {
            val settingsLoaded by vm.settingsLoaded.collectAsStateWithLifecycle()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val nav by vm.nav.collectAsStateWithLifecycle()
            var searchOpenRequestId by remember {
                mutableIntStateOf(0)
            }
            val apps by vm.apps.collectAsStateWithLifecycle()
            val appsLoading by vm.appsLoading.collectAsStateWithLifecycle()
            val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
            val location by vm.location.collectAsStateWithLifecycle()
            val tripData by vm.tripData.collectAsStateWithLifecycle()
            val isDayMode = false
            val pickerSlot by vm.shortcutPickerSlot.collectAsStateWithLifecycle()
            val appPickerTarget by vm.appPickerTarget.collectAsStateWithLifecycle()

            val accent = Color(settings.accentColor)
            val bg = if (settings.useCustomBackgroundColor) {
                Color(settings.backgroundColor)
            } else {
                if (isDayMode) Color(0xFFEEEEEE) else Aw11Background
            }
            val textColor = if (isDayMode) Color(0xFF111111) else Color(settings.fontColor)
            val bgGradientEnd = Color(settings.gradientEndColor)
            val bgBrush = if (settings.useCustomBackgroundColor && settings.useGradient) {
                val colors = listOf(bg, bgGradientEnd)
                when (settings.gradientDirection) {
                    GradientDirection.TOP_TO_BOTTOM -> androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors
                    )

                    GradientDirection.LEFT_TO_RIGHT -> androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors
                    )

                    GradientDirection.DIAGONAL -> androidx.compose.ui.graphics.Brush.linearGradient(
                        colors
                    )

                    GradientDirection.RADIAL -> androidx.compose.ui.graphics.Brush.radialGradient(
                        colors
                    )
                }
            } else null

            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = baseDensity.density,
                    fontScale = baseDensity.fontScale
                )
            ) {
                if (!settingsLoaded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    )
                } else OpenLauncherTheme(
                    accent = accent,
                    background = bg,
                    textColor = textColor,
                    fontBold = settings.fontBold,
                    textScale = settings.textScale,
                    appFont = settings.appFont,
                    isDayMode = isDayMode,
                    useCustomBg = settings.useCustomBackgroundColor
                ) {
                    if (!settings.onboardingCompleted) {
                        OnboardingScreen(
                            accent = accent,
                            onComplete = {
                                vm.updateSettings { copy(onboardingCompleted = true) }
                                // Start location updates immediately upon completion
                                startLocationWithPermissionCheck()
                            }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().let { m ->
                            if (bgBrush != null) m.background(bgBrush) else m.background(bg)
                        }) {
                            // Optional wallpaper layer
                            if (settings.wallpaperUri.isNotEmpty()) {
                                AsyncImage(
                                    model = android.net.Uri.parse(settings.wallpaperUri),
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = settings.wallpaperDim))
                                )
                            }

                            val mainPane: @Composable (Modifier) -> Unit = { paneModifier ->
                                Box(
                                    modifier = paneModifier
                                ) {
                                    // HOME stays alive at all times.
                                    // This preserves the HERE MapView and the active guidance session.
                                    HomeScreen(
                                        settings = settings,
                                        nowPlaying = nowPlaying,
                                        location = location,
                                        tripData = tripData,
                                        onResetTrip = vm::resetTrip,
                                        onPlayPause = vm::playPause,
                                        onNext = vm::skipNext,
                                        onPrev = vm::skipPrev,
                                        onOpenMedia = {
                                            val packageName =
                                                nowPlaying
                                                    ?.controller
                                                    ?.packageName

                                            if (!packageName.isNullOrBlank()) {
                                                openMediaWithReturnOverlay(
                                                    packageName
                                                )
                                            }
                                        },
                                        openSearchRequestId = searchOpenRequestId,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Prevent touches from reaching the HERE MapView
                                    // while another internal screen is displayed.
                                    if (nav != NavDestination.HOME) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .pointerInput(Unit) {
                                                    awaitPointerEventScope {
                                                        while (true) {
                                                            val event =
                                                                awaitPointerEvent()

                                                            event.changes.forEach {
                                                                it.consume()
                                                            }
                                                        }
                                                    }
                                                }
                                        )
                                    }

                                    when (nav) {
                                        NavDestination.HOME -> Unit

                                        NavDestination.APP_LIBRARY -> {
                                            AppLibraryScreen(
                                                apps = apps,
                                                isLoading = appsLoading,
                                                isPickerMode = pickerSlot != null,
                                                pickerSlot = pickerSlot,
                                                isCarPlayPickerMode =
                                                    appPickerTarget != null,
                                                carPlayPickerLabel =
                                                    when (appPickerTarget) {
                                                        LauncherViewModel.AppPickerTarget.ANDROID_AUTO ->
                                                            "CHOOSE ANDROID AUTO APP"

                                                        LauncherViewModel.AppPickerTarget.PIP ->
                                                            "CHOOSE PIP APP"

                                                        LauncherViewModel.AppPickerTarget.RADIO ->
                                                            "CHOOSE RADIO APP"

                                                        else ->
                                                            "CHOOSE CARPLAY APP"
                                                    },
                                                accent = accent,
                                                onAppClick = { app ->
                                                    vm.launchApp(
                                                        app.packageName
                                                    )
                                                },
                                                onPickerSelect = { slot, app ->
                                                    vm.assignShortcut(
                                                        slot,
                                                        app
                                                    )
                                                },
                                                onCarPlaySelect = { app ->
                                                    vm.assignPickerApp(app)
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        NavDestination.SETTINGS -> {
                                            SettingsScreen(
                                                settings = settings,
                                                accent = accent,
                                                onUpdate = { block ->
                                                    vm.updateSettings(block)
                                                },
                                                onReset = {
                                                    vm.resetSettings()
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }

                            Aw11Shell(
                                hasGps = location != null,
                                mediaAvailable =
                                    nowPlaying
                                        ?.controller
                                        ?.packageName
                                        .isNullOrBlank()
                                        .not(),
                                onNav = {
                                    if (
                                        nav == NavDestination.HOME
                                    ) {
                                        searchOpenRequestId++
                                    } else {
                                        searchOpenRequestId = 0

                                        vm.navigate(
                                            NavDestination.HOME
                                        )
                                    }
                                },
                                onMedia = {
                                    val packageName =
                                        nowPlaying
                                            ?.controller
                                            ?.packageName

                                    if (!packageName.isNullOrBlank()) {
                                        openMediaWithReturnOverlay(
                                            packageName
                                        )
                                    }
                                },
                                onApps = {
                                    searchOpenRequestId = 0

                                    vm.navigate(
                                        NavDestination.APP_LIBRARY
                                    )
                                },
                                currentDest = nav,
                                onSettings = {
                                    searchOpenRequestId = 0

                                    vm.navigate(
                                        NavDestination.SETTINGS
                                    )
                                }
                            ) {
                                mainPane(
                                    Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            } // CompositionLocalProvider
        }
    }

    override fun onResume() {
        super.onResume()

        keepLocationInBackground = false

        stopService(
            Intent(
                this,
                MediaReturnOverlayService::class.java
            )
        )

        stopService(
            Intent(
                this,
                NavigationForegroundService::class.java
            )
        )

        vm.refreshConnectivity()
        vm.refreshMedia()
    }

    override fun onStop() {
        super.onStop()

        if (!keepLocationInBackground) {
            vm.stopLocationUpdates()
        }
    }

    override fun onStart() {
        super.onStart()
        startLocationWithPermissionCheck()
    }

    private fun initializeHereSdk() {
        val accessKeyId = BuildConfig.HERE_ACCESS_KEY_ID
        val accessKeySecret = BuildConfig.HERE_ACCESS_KEY_SECRET

        check(
            accessKeyId.isNotBlank() &&
                    accessKeySecret.isNotBlank()
        ) {
            "HERE SDK credentials are missing."
        }

        val authenticationMode =
            AuthenticationMode.withKeySecret(
                accessKeyId,
                accessKeySecret
            )

        val options = SDKOptions(authenticationMode)

        try {
            SDKNativeEngine.makeSharedInstance(
                applicationContext,
                options
            )
        } catch (e: InstantiationErrorException) {
            throw RuntimeException(
                "HERE SDK initialization failed: ${e.error.name}"
            )
        }
    }

    private fun openMediaWithReturnOverlay(
        mediaPackage: String
    ) {
        if (Settings.canDrawOverlays(this)) {
            launchMediaWithReturnOverlay(mediaPackage)
            return
        }

        pendingMediaPackage = mediaPackage

        val permissionIntent =
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse(
                    "package:${this.packageName}"
                )
            )

        runCatching {
            overlayPermissionLauncher.launch(
                permissionIntent
            )
        }.onFailure {
            overlayPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                )
            )
        }
    }

    private fun launchMediaWithReturnOverlay(
        mediaPackage: String
    ) {
        keepLocationInBackground = true

        ContextCompat.startForegroundService(
            this,
            Intent(
                this,
                NavigationForegroundService::class.java
            )
        )

        if (Settings.canDrawOverlays(this)) {
            startService(
                Intent(
                    this,
                    MediaReturnOverlayService::class.java
                ).apply {
                    putExtra(
                        MediaReturnOverlayService.EXTRA_MEDIA_PACKAGE,
                        mediaPackage
                    )
                }
            )
        }

        vm.launchApp(mediaPackage)
    }
    override fun onDestroy() {
        unregisterReceiver(
            carPowerReceiver
        )
        carPowerGraceJob?.cancel()
        super.onDestroy()
    }
    private fun handleExternalPowerChanged(
        externalPower: Boolean
    ) {
        if (externalPower) {
            carPowerGraceJob?.cancel()
            carPowerGraceJob = null

            setCarPowerState(
                CarPowerState.ACTIVE
            )

            return
        }

        if (
            carPowerState ==
            CarPowerState.GRACE_PERIOD ||
            carPowerState ==
            CarPowerState.PARKED
        ) {
            return
        }

        setCarPowerState(
            CarPowerState.GRACE_PERIOD
        )

        carPowerGraceJob =
            lifecycleScope.launch {

                delay(
                    CAR_POWER_GRACE_PERIOD_MS
                )

                setCarPowerState(
                    CarPowerState.PARKED
                )
            }
    }

    private fun setCarPowerState(
        state: CarPowerState
    ) {
        if (carPowerState == state) {
            return
        }

        carPowerState = state

        Log.i(
            CAR_POWER_TAG,
            "state=$state"
        )

        when (state) {

            CarPowerState.ACTIVE,
            CarPowerState.GRACE_PERIOD,
            CarPowerState.PARKED -> {
                window.addFlags(
                    WindowManager.LayoutParams
                        .FLAG_KEEP_SCREEN_ON
                )
            }
        }
    }
}

