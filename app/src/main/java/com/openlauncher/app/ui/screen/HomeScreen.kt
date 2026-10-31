package com.openlauncher.app.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.data.UnitSystem
import com.openlauncher.app.model.NowPlayingState
import com.openlauncher.app.ui.screen.aw11.Aw11HomeLayout
import com.openlauncher.app.util.LocationData
import com.openlauncher.app.viewmodel.TripData

@Composable
fun HomeScreen(
    settings: AppSettings,
    hereSdkReady: Boolean,
    nowPlaying: NowPlayingState?,
    location: LocationData?,
    tripData: TripData,
    onResetTrip: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onOpenMedia: () -> Unit,
    openSearchRequestId: Int,
    modifier: Modifier = Modifier
) {
    Aw11HomeLayout(
        settings = settings,
        hereSdkReady = hereSdkReady,
        location = location,
        tripData = tripData,
        onResetTrip = onResetTrip,
        isMetric =
            settings.unitSystem ==
                    UnitSystem.METRIC,
        nowPlaying = nowPlaying,
        onPrev = onPrev,
        onPlayPause = onPlayPause,
        onNext = onNext,
        onOpenMedia = onOpenMedia,
        openSearchRequestId = openSearchRequestId,
        modifier = modifier
    )
}