package com.openlauncher.app.ui.screen.aw11

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.model.NowPlayingState
import com.openlauncher.app.ui.map.Aw11HereMap
import com.openlauncher.app.ui.map.navigation.DemoTripData
import com.openlauncher.app.ui.theme.Aw11Border
import com.openlauncher.app.util.LocationData
import com.openlauncher.app.viewmodel.TripData
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import com.openlauncher.app.ui.theme.Aw11Background

private enum class StartupPhase {
    SEGMENT_TEST,
    FADE_OUT,
    LIVE
}


@Composable
internal fun Aw11HomeLayout(
    settings: AppSettings,
    hereSdkReady: Boolean,
    location: LocationData?,
    tripData: TripData,
    onResetTrip: () -> Unit,
    isMetric: Boolean,
    nowPlaying: NowPlayingState?,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenMedia: () -> Unit,
    openSearchRequestId: Int,
    modifier: Modifier = Modifier
) {
    var startupPhase by remember {
        mutableStateOf(StartupPhase.SEGMENT_TEST)
    }
    var demoDisplayLocation by remember {
        mutableStateOf<LocationData?>(null)
    }

    var demoDisplayTripData by remember {
        mutableStateOf<DemoTripData?>(null)
    }

    val displayLocation =
        demoDisplayLocation ?: location
    val selfTestProgress = remember {
        Animatable(0f)
    }

    LaunchedEffect(Unit) {
        startupPhase = StartupPhase.SEGMENT_TEST

        // Fill all display bars
        selfTestProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 600,
                easing = LinearEasing
            )
        )

        // Keep all segments illuminated for a moment
        delay(300)

        // Empty all display bars
        selfTestProgress.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = 600,
                easing = LinearEasing
            )
        )

        startupPhase = StartupPhase.FADE_OUT
        delay(150)

        startupPhase = StartupPhase.LIVE
    }

    val showTestValues = startupPhase != StartupPhase.LIVE
    val speedDisplay =
        ((displayLocation?.speedMps ?: 0f) *
                if (isMetric) 3.6f else 2.237f)
            .coerceAtLeast(0f)

    val speedBarMax = if (isMetric) 150f else 93f
    val speedProgress = (speedDisplay / speedBarMax).coerceIn(0f, 1f)
    val displayedTripDistanceMeters =
        demoDisplayTripData?.distanceMeters
            ?: tripData.distanceMeters

    val displayedTripDriveTimeMs =
        demoDisplayTripData?.driveTimeMs
            ?: tripData.driveTimeMs

    val displayedTripAverageSpeedMps =
        demoDisplayTripData?.averageSpeedMps
            ?: tripData.averageSpeedMps

    val displayedTripMaxSpeedMps =
        demoDisplayTripData?.maxSpeedMps
            ?: tripData.maxSpeedMps
    val distanceDisplay =
        if (isMetric) {
            "%.1f KM".format(displayedTripDistanceMeters / 1000.0)
        } else {
            "%.1f MI".format(displayedTripDistanceMeters / 1609.344)
        }

    val avgSpeed =
        displayedTripAverageSpeedMps *
                if (isMetric) 3.6f else 2.237f

    val maxSpeed =
        displayedTripMaxSpeedMps *
                if (isMetric) 3.6f else 2.237f

    val totalMinutes =
        displayedTripDriveTimeMs / 60_000L
    val driveTimeDisplay =
        "%02d:%02d".format(
            totalMinutes / 60,
            totalMinutes % 60
        )

    val displayedDistance =
        if (showTestValues) "88.8 KM" else distanceDisplay

    val displayedDriveTime =
        if (showTestValues) "88:88" else driveTimeDisplay

    val displayedAvgSpeed =
        if (showTestValues) "888" else "%03.0f".format(avgSpeed)

    val displayedMaxSpeed =
        if (showTestValues) "888" else "%03.0f".format(maxSpeed)

    Row(
        modifier = modifier
            .fillMaxSize()
    ) {
        // CENTER — NAVIGATION / MAP
        Box(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight()
                .clipToBounds()
                .border(
                    1.dp,
                    Aw11Border.copy(
                        alpha = 0.45f
                    )
                )
        ) {
            if (hereSdkReady) {
                Aw11HereMap(
                    location = location,
                    settings = settings,
                    openSearchRequestId =
                        openSearchRequestId,
                    onDemoDataChanged = { demoLocation,
                                          demoTripData ->

                        demoDisplayLocation =
                            demoLocation

                        demoDisplayTripData =
                            demoTripData
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(1.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Aw11Background)
                )
            }
        }

        Spacer(
            Modifier.width(3.dp)
        )

        Column(
            modifier = Modifier
                .weight(0.32f)
                .fillMaxHeight()
        ) {
            Aw11SpeedPanel(
                speedDisplay = speedDisplay,
                speedProgress = speedProgress,
                isMetric = isMetric,
                showTestValues = showTestValues,
                selfTestProgress =
                    selfTestProgress.value,
                modifier = Modifier.weight(0.28f)
            )

            Spacer(Modifier.height(3.dp))

            Aw11MediaPanel(
                nowPlaying = nowPlaying,
                showTestValues = showTestValues,
                selfTestProgress =
                    selfTestProgress.value,
                onPrev = onPrev,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onOpenMedia = onOpenMedia,
                modifier = Modifier.weight(0.36f)
            )

            Spacer(Modifier.height(3.dp))

            Aw11TripPanel(
                distance = displayedDistance,
                driveTime = displayedDriveTime,
                averageSpeed = displayedAvgSpeed,
                maxSpeed = displayedMaxSpeed,
                onResetTrip = onResetTrip,
                modifier = Modifier.weight(0.36f)
            )
        }
    }
}
