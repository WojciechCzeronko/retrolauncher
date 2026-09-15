package com.openlauncher.app.ui.map

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.here.sdk.core.GeoCoordinates
import com.here.sdk.gestures.TapListener
import com.here.sdk.mapview.MapFeatures
import com.here.sdk.mapview.MapImageFactory
import com.here.sdk.mapview.MapMarker3D
import com.here.sdk.mapview.MapRenderMode
import com.here.sdk.mapview.MapView
import com.here.sdk.mapview.MapViewOptions
import com.here.sdk.mapview.RenderSize
import com.openlauncher.app.R
import com.openlauncher.app.data.AppSettings
import com.openlauncher.app.ui.map.components.Aw11DemoControls
import com.openlauncher.app.ui.map.components.Aw11GuidanceHeader
import com.openlauncher.app.ui.map.components.Aw11RecenterButton
import com.openlauncher.app.ui.map.components.Aw11RouteInfo
import com.openlauncher.app.ui.map.components.Aw11SearchPanel
import com.openlauncher.app.ui.map.components.Aw11SelectedLocationPanel
import com.openlauncher.app.ui.map.here.HereCameraController
import com.openlauncher.app.ui.map.here.HereMapPoiPicker
import com.openlauncher.app.ui.map.here.HereRouteRenderer
import com.openlauncher.app.ui.map.here.HereSearchPinRenderer
import com.openlauncher.app.ui.map.here.HereSelectedLocationRenderer
import com.openlauncher.app.ui.map.navigation.DemoNavigationController
import com.openlauncher.app.ui.map.navigation.DemoTripData
import com.openlauncher.app.ui.map.navigation.ManeuverGuidance
import com.openlauncher.app.ui.map.navigation.ManeuverProgressTracker
import com.openlauncher.app.ui.map.navigation.RouteProgressTracker
import com.openlauncher.app.util.LocationData
import kotlinx.coroutines.delay
import kotlin.math.exp

private const val TAG = "Aw11HereMap"

private const val DEFAULT_LATITUDE = 53.14209513566131
private const val DEFAULT_LONGITUDE = 18.030773831645174
private const val MAX_REROUTE_GPS_ACCURACY_METERS = 50f
private const val ROUTE_SNAP_ENTER_METERS = 15.0
private const val ROUTE_SNAP_EXIT_METERS = 20.0
private const val AUTO_REROUTE_COOLDOWN_MS = 30_000L
private const val MAX_NUMBER_OF_SEARCH_RESULTS = 5

private const val MAX_AUTO_REROUTES_PER_GUIDANCE = MAX_NUMBER_OF_SEARCH_RESULTS
private const val LOOK_AHEAD_SECONDS = 1.5
private const val LOOK_AHEAD_MIN_METERS = 10.0
private const val LOOK_AHEAD_MAX_METERS = 30.0
private const val LOOK_AHEAD_MIN_SPEED_MPS = 1.0f

private const val DEFAULT_CAMERA_DISTANCE_METERS = 500.0
private const val ZOOM_RESPONSE_FACTOR = 0.25
private const val DEMO_UPDATE_INTERVAL_MS = 250L
private const val SEARCH_RESULTS_RESERVED_LEFT_DP = 260
private const val SEARCH_RESULTS_PADDING_DP = 12
private const val LOOK_AHEAD_SMOOTHING_TIME_MS = 300.0
private const val CAMERA_BEARING_SMOOTHING_TIME_MS = 450.0

private const val BEARING_LOCK_SPEED_MPS = 1.5f
private const val BEARING_UNLOCK_SPEED_MPS = 2.5f

private const val ARRIVAL_REMAINING_ROUTE_METERS = 20
private const val ARRIVAL_DESTINATION_RADIUS_METERS = 30.0
private const val ARRIVAL_CONFIRMATION_COUNT = 3
private const val ARRIVAL_AUTO_CLOSE_DELAY_MS = 20_000L

private const val VISUAL_ROUTE_UPDATE_INTERVAL_MS = 100L
private const val POI_PICK_AREA_DP = 40

private const val MAP_PIXEL_BASE_SIZE_PX = 4f
private const val MAP_PIXEL_REFERENCE_WIDTH_PX = 3200f
private const val MAP_PIXEL_MIN_SIZE_PX = 2f

private const val MAP_PIXEL_SHADER = """
    uniform shader content;
    uniform float pixelSize;

    half4 main(float2 coord) {
        float2 pixelCoord =
            (floor(coord / pixelSize) + 0.5) * pixelSize;

        return content.eval(pixelCoord);
    }
"""


private class MapAnimationState {
    var latitude = DEFAULT_LATITUDE
    var longitude = DEFAULT_LONGITUDE
    var cameraLatitude = DEFAULT_LATITUDE
    var cameraLongitude = DEFAULT_LONGITUDE
    var cameraDistanceMeters =
        DEFAULT_CAMERA_DISTANCE_METERS
    var bearing = 0f
    var lastLocationUpdateMs = 0L
    var lastVisualRouteUpdateMs = 0L
    var isSnappedToRoute = false
    var lookAheadLatitude = DEFAULT_LATITUDE
    var lookAheadLongitude = DEFAULT_LONGITUDE
    var hasLookAheadTarget = false
    var cameraBearing = 0f
    var hasCameraBearing = false
    var hasVehicleBearing = false
    var isVehicleBearingUnlocked = false
}

@Composable
fun Aw11HereMap(
    location: LocationData?,
    settings: AppSettings,
    openSearchRequestId: Int = 0,
    onDemoDataChanged: (
        LocationData?,
        DemoTripData?
    ) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = rememberAw11HereMapState()
    val mapView = remember(context) {
        val options = MapViewOptions().apply {
            renderMode = MapRenderMode.TEXTURE
        }

        MapView(
            context,
            options
        ).apply {
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {
                val shader =
                    RuntimeShader(
                        MAP_PIXEL_SHADER
                    )

                val displayWidthPx =
                    context.resources.displayMetrics
                        .widthPixels
                        .toFloat()

                val mapPixelSizePx =
                    (
                            MAP_PIXEL_BASE_SIZE_PX *
                                    displayWidthPx /
                                    MAP_PIXEL_REFERENCE_WIDTH_PX
                            )
                        .coerceIn(
                            MAP_PIXEL_MIN_SIZE_PX,
                            MAP_PIXEL_BASE_SIZE_PX
                        )

                shader.setFloatUniform(
                    "pixelSize",
                    mapPixelSizePx
                )

                setRenderEffect(
                    RenderEffect.createRuntimeShaderEffect(
                        shader,
                        "content"
                    )
                )
            }
        }
    }

    val routeRenderer = remember(mapView) {
        HereRouteRenderer(mapView)
    }
    val searchPinRenderer = remember(mapView) {
        HereSearchPinRenderer(mapView)
    }

    val selectedLocationRenderer =
        remember(mapView) {
            HereSelectedLocationRenderer(
                mapView
            )
        }

    val mapPoiPicker =
        remember(mapView) {
            HereMapPoiPicker(
                mapView
            )
        }

    val cameraController = remember(mapView) {
        HereCameraController(mapView)
    }

    val animationState = remember {
        MapAnimationState()
    }

    val animationProgress = remember {
        Animatable(1f)
    }

    val isMapResumed = remember {
        mutableStateOf(
            lifecycleOwner.lifecycle.currentState
                .isAtLeast(Lifecycle.State.RESUMED)
        )
    }

    val snapMarkerOnResume = remember {
        mutableStateOf(false)
    }
    val routingController = remember {
        HereRoutingController()
    }

    val searchController = remember {
        HereSearchController()
    }

    val routeProgressTracker = remember {
        RouteProgressTracker()
    }
    val visualRouteProgressTracker = remember {
        RouteProgressTracker()
    }
    val maneuverProgressTracker = remember {
        ManeuverProgressTracker()
    }

    val maneuverGuidance = remember {
        mutableStateOf<ManeuverGuidance?>(null)
    }
    val demoNavigationController = remember {
        DemoNavigationController()
    }

    val demoLocation = remember {
        mutableStateOf<LocationData?>(null)
    }

    val isDemoMode = remember {
        mutableStateOf(false)
    }

    val isDemoRunning = remember {
        mutableStateOf(false)
    }
    val isDemoPaused = remember {
        mutableStateOf(false)
    }
    val demoSpeedMultiplier = remember {
        mutableStateOf(1.0)
    }

    val navigationLocation =
        if (isDemoMode.value) {
            demoLocation.value
        } else {
            location
        }

    val carMarker = remember {
        mutableStateOf<MapMarker3D?>(null)
    }

    val lastRouteRefreshMs = remember {
        mutableLongStateOf(0L)
    }

    val offRouteSinceMs = remember {
        mutableLongStateOf(0L)
    }

    val lastAutoRerouteRequestMs = remember {
        mutableLongStateOf(0L)
    }

    val autoRerouteCount = remember {
        mutableIntStateOf(0)
    }

    val isRouteRequestInProgress = remember {
        mutableStateOf(false)
    }

    val routeRefreshIntervalMs =
        settings.routeRefreshIntervalSeconds * 1000L

    val arrivalConfirmationCount = remember {
        mutableIntStateOf(0)
    }

    val mapSelectionRequestId =
        remember {
            mutableIntStateOf(0)
        }

    LaunchedEffect(
        openSearchRequestId
    ) {
        if (
            openSearchRequestId <= 0 ||
            state.activeRoute != null
        ) {
            return@LaunchedEffect
        }

        mapSelectionRequestId.intValue++

        selectedLocationRenderer.clear()
        searchPinRenderer.clear()

        state.selectedLocation = null

        state.openSearch()
    }

    val endGuidance: () -> Unit = {
        demoNavigationController.stop()

        isDemoRunning.value = false
        isDemoPaused.value = false
        isDemoMode.value = false

        demoLocation.value = null

        onDemoDataChanged(
            null,
            null
        )

        routeRenderer.clearRoute()
        routeProgressTracker.clear()
        visualRouteProgressTracker.clear()

        animationState.lastVisualRouteUpdateMs = 0L
        maneuverProgressTracker.clear()

        maneuverGuidance.value = null

        state.activeRoute = null
        state.routeProgress = null
        state.destination = null
        state.destinationPositionHint = null
        state.destinationTitle = null
        state.isArrived = false

        arrivalConfirmationCount.intValue = 0

        animationState.isSnappedToRoute = false

        lastRouteRefreshMs.longValue = 0L
        offRouteSinceMs.longValue = 0L
        lastAutoRerouteRequestMs.longValue = 0L
        autoRerouteCount.intValue = 0

        Log.d(
            TAG,
            "Guidance session ended."
        )
    }

    fun startGuidanceTo(
        destinationPosition: GeoCoordinates,
        destinationAccessPoints: List<GeoCoordinates> = emptyList(),
        destinationTitle: String
    ) {
        searchPinRenderer.clear()
        selectedLocationRenderer.clear()

        mapSelectionRequestId.intValue++

        state.selectedLocation = null

        val start =
            GeoCoordinates(
                animationState.latitude,
                animationState.longitude
            )

        val destination =
            destinationAccessPoints
                .minByOrNull { accessPoint ->
                    start.distanceTo(accessPoint)
                }
                ?: destinationPosition

        val destinationPositionHint =
            if (destinationAccessPoints.isNotEmpty()) {
                destinationPosition
            } else {
                null
            }
        state.destination =
            destination

        state.destinationPositionHint =
            destinationPositionHint

        state.destinationTitle =
            destinationTitle

        state.isArrived = false

        arrivalConfirmationCount.intValue = 0

        offRouteSinceMs.longValue = 0L
        lastAutoRerouteRequestMs.longValue = 0L
        autoRerouteCount.intValue = 0

        lastRouteRefreshMs.longValue =
            SystemClock.elapsedRealtime()

        state.closeSearch()

        Log.d(
            TAG,
            "Calculating route to: " +
                    "$destinationTitle, " +
                    "position=" +
                    "${destinationPosition.latitude}," +
                    "${destinationPosition.longitude}, " +
                    "routingTarget=" +
                    "${destination.latitude}," +
                    "${destination.longitude}, " +
                    "accessPoints=" +
                    destinationAccessPoints.size
        )

        routingController.calculateRoute(
            start = start,
            destination = destination,
            destinationPositionHint =
                destinationPositionHint,
            startHeadingDegrees =
                navigationLocation
                    ?.bearingDegrees,
            onSuccess = { route ->
                state.activeRoute =
                    route

                routeProgressTracker.setRoute(
                    route
                )
                visualRouteProgressTracker.setRoute(
                    route
                )

                animationState.lastVisualRouteUpdateMs = 0L

                maneuverProgressTracker.setRoute(
                    route
                )

                state.routeProgress =
                    routeProgressTracker.update(
                        start
                    )

                maneuverGuidance.value =
                    maneuverProgressTracker.update(
                        state.routeProgress
                    )

                routeRenderer.showRoute(
                    route = route,
                    destination =
                        destinationPosition
                )

                Log.d(
                    TAG,
                    "Route: ${route.lengthInMeters} m, " +
                            "${route.duration.seconds} s"
                )
            },
            onError = { error ->
                Log.e(
                    TAG,
                    "Route calculation failed: ${error.name}"
                )
            }
        )
    }

    fun resolveSelectedMapLocation(
        coordinates: GeoCoordinates,
        preferredTitle: String?,
        requestId: Int
    ) {
        if (
            mapSelectionRequestId.intValue !=
            requestId
        ) {
            return
        }

        selectedLocationRenderer.show(
            coordinates
        )

        state.selectedLocation =
            HereSelectedLocation(
                coordinates = coordinates,
                title = preferredTitle,
                address = null
            )

        searchController.reverseGeocode(
            coordinates = coordinates,
            onSuccess = { resolvedLocation ->
                if (
                    mapSelectionRequestId.intValue ==
                    requestId
                ) {
                    state.selectedLocation =
                        resolvedLocation.copy(
                            title =
                                preferredTitle
                                    ?.takeIf {
                                        it.isNotBlank()
                                    }
                                    ?: resolvedLocation.title
                        )
                }
            },
            onError = { error ->
                if (
                    mapSelectionRequestId.intValue ==
                    requestId
                ) {
                    Log.e(
                        TAG,
                        "Reverse geocoding failed: ${error.name}"
                    )

                    state.selectedLocation =
                        HereSelectedLocation(
                            coordinates =
                                coordinates,
                            title =
                                preferredTitle
                                    ?.takeIf {
                                        it.isNotBlank()
                                    }
                                    ?: "SELECTED POINT",
                            address =
                                "ADDRESS UNAVAILABLE"
                        )
                }
            }
        )
    }

    LaunchedEffect(
        state.isArrived,
        state.activeRoute
    ) {
        if (
            !state.isArrived ||
            state.activeRoute == null
        ) {
            return@LaunchedEffect
        }

        Log.d(
            TAG,
            "Arrival confirmed. Guidance will end automatically in 20 seconds."
        )

        delay(
            ARRIVAL_AUTO_CLOSE_DELAY_MS
        )

        if (
            state.isArrived &&
            state.activeRoute != null
        ) {
            Log.d(
                TAG,
                "Arrival timeout reached. Ending guidance automatically."
            )

            endGuidance()
        }
    }

    LaunchedEffect(
        isDemoRunning.value,
        state.activeRoute,
        demoSpeedMultiplier.value
    ) {
        if (!isDemoRunning.value) {
            return@LaunchedEffect
        }

        val route =
            state.activeRoute ?: run {
                isDemoRunning.value = false
                return@LaunchedEffect
            }

        if (!demoNavigationController.isRunning) {
            demoLocation.value =
                demoNavigationController.start(
                    route = route,
                    speedMultiplier =
                        demoSpeedMultiplier.value
                )
        } else {
            demoNavigationController.setSpeedMultiplier(
                demoSpeedMultiplier.value
            )

            demoLocation.value =
                demoNavigationController.currentLocation
        }

        isDemoMode.value = true

        var lastUpdateMs =
            SystemClock.elapsedRealtime()

        while (
            isDemoRunning.value &&
            demoNavigationController.isRunning
        ) {
            delay(
                DEMO_UPDATE_INTERVAL_MS
            )

            val now =
                SystemClock.elapsedRealtime()

            val deltaSeconds =
                (
                        now - lastUpdateMs
                        ) / 1000.0

            lastUpdateMs = now

            val updatedLocation =
                demoNavigationController.update(
                    deltaSeconds
                )

            demoLocation.value =
                updatedLocation

            onDemoDataChanged(
                updatedLocation,
                demoNavigationController.tripData
            )
        }

        if (
            isDemoRunning.value &&
            !demoNavigationController.isRunning
        ) {
            // Keep the final demo position at the destination.
            demoLocation.value =
                demoNavigationController.currentLocation

            isDemoRunning.value = false
        }
    }

    DisposableEffect(mapView, lifecycleOwner) {
        mapView.onCreate(null)

        mapView.gestures.tapListener =
            TapListener { touchPoint ->
                if (state.activeRoute != null) {
                    return@TapListener
                }

                val tappedCoordinates =
                    mapView.viewToGeoCoordinates(
                        touchPoint
                    ) ?: return@TapListener

                state.isFollowing = false

                searchPinRenderer.clear()
                state.closeSearch()

                val requestId =
                    mapSelectionRequestId.intValue + 1

                mapSelectionRequestId.intValue =
                    requestId

                // Show immediate feedback at the tapped location.
                selectedLocationRenderer.show(
                    tappedCoordinates
                )

                state.selectedLocation =
                    HereSelectedLocation(
                        coordinates =
                            tappedCoordinates,
                        title = null,
                        address = null
                    )

                val pickAreaSizePx =
                    with(density) {
                        POI_PICK_AREA_DP
                            .dp
                            .toPx()
                            .toDouble()
                    }

                mapPoiPicker.pick(
                    touchPoint = touchPoint,
                    pickAreaSizePx =
                        pickAreaSizePx,
                    onResult = { pickedPoi ->
                        if (
                            mapSelectionRequestId.intValue !=
                            requestId
                        ) {
                            return@pick
                        }

                        if (pickedPoi != null) {
                            Log.d(
                                TAG,
                                "POI picked: " +
                                        "${pickedPoi.name}, " +
                                        "${pickedPoi.coordinates.latitude}, " +
                                        "${pickedPoi.coordinates.longitude}"
                            )

                            resolveSelectedMapLocation(
                                coordinates =
                                    pickedPoi.coordinates,
                                preferredTitle =
                                    pickedPoi.name,
                                requestId =
                                    requestId
                            )
                        } else {
                            Log.d(
                                TAG,
                                "No POI picked. Falling back to reverse geocoding."
                            )

                            resolveSelectedMapLocation(
                                coordinates =
                                    tappedCoordinates,
                                preferredTitle = null,
                                requestId =
                                    requestId
                            )
                        }
                    }
                )
            }

        mapView.mapScene.loadScene(
            "aw11-style-new.zip"
        ) { mapError ->
            if (mapError == null) {
                Log.d(TAG, "HERE map scene loaded.")

                mapView.mapScene.disableFeatures(
                    listOf(
                        MapFeatures.EXTRUDED_BUILDINGS,
                        MapFeatures.SHADOWS,
                        MapFeatures.AMBIENT_OCCLUSION,
                        MapFeatures.TRAFFIC_LIGHTS
                    )
                )
                val initialCoordinates =
                    if (location != null) {
                        GeoCoordinates(
                            location.latitude,
                            location.longitude
                        )
                    } else {
                        GeoCoordinates(
                            DEFAULT_LATITUDE,
                            DEFAULT_LONGITUDE
                        )
                    }

                animationState.latitude = initialCoordinates.latitude
                animationState.longitude = initialCoordinates.longitude
                animationState.cameraLatitude = initialCoordinates.latitude
                animationState.cameraLongitude = initialCoordinates.longitude

                location?.bearingDegrees?.let {
                    animationState.bearing = it
                    animationState.hasVehicleBearing = true

                    animationState.cameraBearing = it
                    animationState.hasCameraBearing = true
                }

                cameraController.showInitialPosition(
                    initialCoordinates
                )

                val markerImage =
                    MapImageFactory.fromResource(
                        context.resources,
                        R.drawable.ic_car_marker_triangle
                    )

                val marker = MapMarker3D(
                    initialCoordinates,
                    markerImage,
                    1.0,
                    RenderSize.Unit.PIXELS
                )

                marker.bearing =
                    animationState.bearing.toDouble()

                mapView.mapScene.addMapMarker3d(
                    marker
                )

                carMarker.value = marker
            } else {
                Log.e(
                    TAG,
                    "HERE map scene failed: ${mapError.name}"
                )
            }
        }

        val lifecycleObserver =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        if (!isMapResumed.value) {
                            snapMarkerOnResume.value = true
                        }

                        isMapResumed.value = true
                        mapView.onResume()
                    }

                    Lifecycle.Event.ON_PAUSE -> {
                        isMapResumed.value = false
                        mapView.onPause()
                    }

                    else -> Unit
                }
            }

        lifecycleOwner.lifecycle.addObserver(
            lifecycleObserver
        )

        // The composable can enter composition while the Activity is already resumed.
        if (
            lifecycleOwner.lifecycle.currentState
                .isAtLeast(Lifecycle.State.RESUMED)
        ) {
            mapView.onResume()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(
                lifecycleObserver
            )

            carMarker.value?.let { marker ->
                mapView.mapScene.removeMapMarker3d(
                    marker
                )
            }
            searchPinRenderer.clear()
            carMarker.value = null

            routeRenderer.clearRoute()
            selectedLocationRenderer.clear()
            mapView.gestures.tapListener = null
            mapView.onPause()
            routingController.dispose()
            mapView.onDestroy()
        }
    }

    // Car movement
    LaunchedEffect(
        navigationLocation?.latitude,
        navigationLocation?.longitude,
        navigationLocation?.bearingDegrees,
        navigationLocation?.speedMps,
        carMarker.value,
        state.activeRoute,
        state.destination,
        settings.autoReroute,
        settings.offRouteThresholdMeters,
        settings.rerouteDelaySeconds,
        isMapResumed.value
    ) {
        val currentLocation =
            navigationLocation ?: return@LaunchedEffect

        val marker =
            carMarker.value ?: return@LaunchedEffect

        if (!isMapResumed.value) {
            return@LaunchedEffect
        }
        val rawCoordinates = GeoCoordinates(
            currentLocation.latitude,
            currentLocation.longitude
        )

        val activeRoute =
            state.activeRoute

        val routeProgress =
            if (activeRoute != null) {
                routeProgressTracker.update(
                    rawCoordinates
                )
            } else {
                null
            }

        if (
            routeProgress != null &&
            activeRoute != null
        ) {
            state.routeProgress =
                routeProgress

            val destination =
                state.destination

            if (
                !state.isArrived &&
                destination != null
            ) {
                val destinationDistanceMeters =
                    distanceMeters(
                        rawCoordinates,
                        destination
                    )

                val isInsideArrivalZone =
                    routeProgress.remainingDistanceMeters <=
                            ARRIVAL_REMAINING_ROUTE_METERS &&
                            destinationDistanceMeters <=
                            ARRIVAL_DESTINATION_RADIUS_METERS

                if (isInsideArrivalZone) {
                    if (isDemoMode.value) {
                        arrivalConfirmationCount.intValue =
                            ARRIVAL_CONFIRMATION_COUNT
                    } else {
                        arrivalConfirmationCount.intValue++
                    }

                    if (
                        arrivalConfirmationCount.intValue >=
                        ARRIVAL_CONFIRMATION_COUNT
                    ) {
                        state.isArrived = true

                        Log.d(
                            TAG,
                            "Destination reached: " +
                                    "routeRemaining=${routeProgress.remainingDistanceMeters} m, " +
                                    "destinationDistance=${destinationDistanceMeters.toInt()} m"
                        )
                    }
                } else {
                    arrivalConfirmationCount.intValue = 0
                }
            }

            maneuverGuidance.value =
                maneuverProgressTracker.update(
                    routeProgress
                )

            maneuverGuidance.value?.let { guidance ->
                Log.d(
                    TAG,
                    "Next maneuver: " +
                            "${guidance.actionName}, " +
                            "${guidance.distanceMeters} m, " +
                            guidance.instruction
                )
            }
        }
        routeProgress?.let { progress ->
            Log.d(
                TAG,
                "Route progress: " +
                        "distance=${progress.remainingDistanceMeters} m, " +
                        "eta=${progress.remainingDurationSeconds} s, " +
                        "offRoute=${progress.distanceFromRouteMeters.toInt()} m, " +
                        "progress=${"%.3f".format(progress.progressFraction)}"
            )
            if (
                !state.isArrived &&
                !isDemoMode.value &&
                settings.autoReroute &&
                autoRerouteCount.intValue <
                MAX_AUTO_REROUTES_PER_GUIDANCE &&
                routeProgress != null &&
                state.destination != null
            ) {
                val gpsAccuracyIsGood =
                    currentLocation.accuracy <=
                            MAX_REROUTE_GPS_ACCURACY_METERS

                val isOffRoute =
                    routeProgress.distanceFromRouteMeters >
                            settings.offRouteThresholdMeters

                val now =
                    SystemClock.elapsedRealtime()

                if (
                    !gpsAccuracyIsGood ||
                    !isOffRoute
                ) {
                    offRouteSinceMs.longValue = 0L
                } else if (
                    offRouteSinceMs.longValue == 0L
                ) {
                    offRouteSinceMs.longValue = now

                    Log.d(
                        TAG,
                        "Off route detected: " +
                                "${routeProgress.distanceFromRouteMeters.toInt()} m"
                    )
                } else {
                    val offRouteDurationMs =
                        now - offRouteSinceMs.longValue

                    val rerouteDelayMs =
                        settings.rerouteDelaySeconds * 1000L

                    val cooldownFinished =
                        lastAutoRerouteRequestMs.longValue == 0L ||
                                now - lastAutoRerouteRequestMs.longValue >=
                                AUTO_REROUTE_COOLDOWN_MS

                    val rerouteLimitNotReached =
                        autoRerouteCount.intValue <
                                MAX_AUTO_REROUTES_PER_GUIDANCE

                    if (
                        offRouteDurationMs >= rerouteDelayMs &&
                        cooldownFinished &&
                        rerouteLimitNotReached &&
                        !isRouteRequestInProgress.value
                    ) {
                        val destination =
                            state.destination

                        if (destination != null) {
                            isRouteRequestInProgress.value = true

                            // Reset now so a failed request does not immediately fire again.
                            offRouteSinceMs.longValue = 0L

                            lastAutoRerouteRequestMs.longValue =
                                SystemClock.elapsedRealtime()

                            Log.d(
                                TAG,
                                "Auto reroute started: " +
                                        "offRoute=${routeProgress.distanceFromRouteMeters.toInt()} m"
                            )

                            routingController.calculateRoute(
                                start = rawCoordinates,
                                destination = destination,
                                destinationPositionHint =
                                    state.destinationPositionHint,
                                startHeadingDegrees =
                                    currentLocation.bearingDegrees,
                                onSuccess = { newRoute ->
                                    routeProgressTracker.setRoute(
                                        newRoute
                                    )
                                    visualRouteProgressTracker.setRoute(
                                        newRoute
                                    )

                                    animationState.lastVisualRouteUpdateMs = 0L

                                    maneuverProgressTracker.setRoute(
                                        newRoute
                                    )
                                    val newProgress =
                                        routeProgressTracker.update(
                                            rawCoordinates
                                        )

                                    state.activeRoute =
                                        newRoute

                                    state.routeProgress =
                                        newProgress

                                    maneuverGuidance.value =
                                        maneuverProgressTracker.update(
                                            state.routeProgress
                                        )

                                    routeRenderer.showRoute(
                                        route = newRoute,
                                        destination =
                                            state.destinationPositionHint
                                                ?: destination
                                    )

                                    lastRouteRefreshMs.longValue =
                                        SystemClock.elapsedRealtime()

                                    isRouteRequestInProgress.value =
                                        false

                                    autoRerouteCount.intValue++
                                    Log.d(
                                        TAG,
                                        "Auto reroute completed: " +
                                                "${newRoute.lengthInMeters} m, " +
                                                "${newRoute.duration.seconds} s, " +
                                                "count=${autoRerouteCount.intValue}/$MAX_AUTO_REROUTES_PER_GUIDANCE"
                                    )
                                    if (
                                        autoRerouteCount.intValue >=
                                        MAX_AUTO_REROUTES_PER_GUIDANCE
                                    ) {
                                        Log.d(
                                            TAG,
                                            "Auto reroute limit reached. " +
                                                    "Further reroutes disabled for this guidance session."
                                        )
                                    }
                                },
                                onError = { error ->
                                    isRouteRequestInProgress.value =
                                        false

                                    Log.e(
                                        TAG,
                                        "Auto reroute failed: ${error.name}"
                                    )
                                }
                            )
                        }
                    }
                }
            } else {
                offRouteSinceMs.longValue = 0L
            }
        }
        val shouldSnapToRoute =
            routeProgress?.let { progress ->

                if (animationState.isSnappedToRoute) {
                    progress.distanceFromRouteMeters <=
                            ROUTE_SNAP_EXIT_METERS
                } else {
                    progress.distanceFromRouteMeters <=
                            ROUTE_SNAP_ENTER_METERS
                }

            } ?: false

        animationState.isSnappedToRoute =
            shouldSnapToRoute

        val displayCoordinates =
            if (
                shouldSnapToRoute &&
                routeProgress != null
            ) {
                routeProgress.matchedCoordinates
            } else {
                rawCoordinates
            }
        val lookAheadDistanceMeters =
            calculateLookAheadDistanceMeters(
                currentLocation.speedMps
            )

        val now = SystemClock.elapsedRealtime()

        val updateIntervalMs =
            if (animationState.lastLocationUpdateMs > 0L) {
                now - animationState.lastLocationUpdateMs
            } else {
                500L
            }

        val rawCameraTargetCoordinates =
            if (
                shouldSnapToRoute &&
                routeProgress != null &&
                lookAheadDistanceMeters > 0.0
            ) {
                routeProgressTracker
                    .getLookAheadCoordinates(
                        progress = routeProgress,
                        distanceMeters =
                            lookAheadDistanceMeters
                    )
                    ?: displayCoordinates
            } else {
                displayCoordinates
            }

        val cameraTargetCoordinates =
            if (
                shouldSnapToRoute &&
                lookAheadDistanceMeters > 0.0
            ) {
                smoothLookAheadTarget(
                    target = rawCameraTargetCoordinates,
                    animationState = animationState,
                    updateIntervalMs = updateIntervalMs
                )
            } else {
                animationState.hasLookAheadTarget = false
                displayCoordinates
            }
        val targetLatitude =
            displayCoordinates.latitude

        val targetLongitude =
            displayCoordinates.longitude

        val startLatitude = animationState.latitude
        val startLongitude = animationState.longitude


        val startCameraLatitude =
            animationState.cameraLatitude

        val startCameraLongitude =
            animationState.cameraLongitude

        val targetCameraLatitude =
            cameraTargetCoordinates.latitude

        val targetCameraLongitude =
            cameraTargetCoordinates.longitude

        val startVehicleBearing =
            animationState.bearing

        when {
            currentLocation.speedMps >=
                    BEARING_UNLOCK_SPEED_MPS -> {
                animationState.isVehicleBearingUnlocked =
                    true
            }

            currentLocation.speedMps <=
                    BEARING_LOCK_SPEED_MPS -> {
                animationState.isVehicleBearingUnlocked =
                    false
            }
        }

        val gpsBearing =
            currentLocation.bearingDegrees

        val shouldAcceptGpsBearing =
            gpsBearing != null &&
                    (
                            !animationState.hasVehicleBearing ||
                                    animationState.isVehicleBearingUnlocked
                            )

        val targetVehicleBearing =
            if (shouldAcceptGpsBearing) {
                gpsBearing!!
            } else {
                startVehicleBearing
            }

        if (shouldAcceptGpsBearing) {
            animationState.hasVehicleBearing = true
        }

        val vehicleBearingDelta =
            shortestBearingDelta(
                startVehicleBearing,
                targetVehicleBearing
            )

        val startCameraBearing =
            if (animationState.hasCameraBearing) {
                animationState.cameraBearing
            } else {
                targetVehicleBearing
            }

        val targetCameraBearing =
            if (animationState.hasCameraBearing) {
                smoothCameraBearing(
                    current = startCameraBearing,
                    target = targetVehicleBearing,
                    updateIntervalMs = updateIntervalMs
                )
            } else {
                targetVehicleBearing
            }

        val cameraBearingDelta =
            shortestBearingDelta(
                startCameraBearing,
                targetCameraBearing
            )

        animationState.hasCameraBearing = true



        animationState.lastLocationUpdateMs = now

        val animationDurationMs =
            (updateIntervalMs * 1.05)
                .toLong()
                .coerceIn(
                    250L,
                    1500L
                )

        val requestedCameraDistanceMeters =
            calculateCameraDistanceMeters(
                currentLocation.speedMps
            )

        val targetCameraDistanceMeters =
            animationState.cameraDistanceMeters +
                    (
                            requestedCameraDistanceMeters -
                                    animationState.cameraDistanceMeters
                            ) *
                    ZOOM_RESPONSE_FACTOR

        val startCameraDistanceMeters =
            animationState.cameraDistanceMeters

        if (snapMarkerOnResume.value) {
            snapMarkerOnResume.value = false

            animationProgress.snapTo(1f)

            marker.coordinates =
                displayCoordinates

            marker.bearing =
                targetVehicleBearing.toDouble()

            if (
                shouldSnapToRoute &&
                activeRoute != null
            ) {
                visualRouteProgressTracker
                    .update(displayCoordinates)
                    ?.let { visualProgress ->
                        routeRenderer.updateRouteProgress(
                            route = activeRoute,
                            matchedSegmentIndex =
                                visualProgress.matchedSegmentIndex,
                            matchedCoordinates =
                                visualProgress.matchedCoordinates
                        )
                    }

                animationState.lastVisualRouteUpdateMs =
                    now
            }

            if (
                state.isFollowing &&
                !state.isRecentering
            ) {
                cameraController.follow(
                    coordinates =
                        cameraTargetCoordinates,
                    bearingDegrees =
                        targetCameraBearing,
                    zoomDistanceMeters =
                        requestedCameraDistanceMeters
                )
            }

            animationState.latitude =
                displayCoordinates.latitude

            animationState.longitude =
                displayCoordinates.longitude

            animationState.cameraLatitude =
                cameraTargetCoordinates.latitude

            animationState.cameraLongitude =
                cameraTargetCoordinates.longitude

            animationState.bearing =
                targetVehicleBearing

            animationState.cameraBearing =
                targetCameraBearing

            animationState.cameraDistanceMeters =
                requestedCameraDistanceMeters

            animationState.lastLocationUpdateMs =
                now

            return@LaunchedEffect
        }

        animationProgress.snapTo(0f)

        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = animationDurationMs.toInt(),
                easing = LinearEasing
            )
        ) {
            val progress = value

            val latitude =
                startLatitude +
                        (targetLatitude - startLatitude) * progress

            val longitude =
                startLongitude +
                        (targetLongitude - startLongitude) * progress

            val vehicleBearing =
                normalizeBearing(
                    startVehicleBearing +
                            vehicleBearingDelta * progress
                )

            val cameraBearing =
                normalizeBearing(
                    startCameraBearing +
                            cameraBearingDelta * progress
                )


            val cameraDistanceMeters =
                startCameraDistanceMeters +
                        (
                                targetCameraDistanceMeters -
                                        startCameraDistanceMeters
                                ) *
                        progress

            val cameraLatitude =
                startCameraLatitude +
                        (
                                targetCameraLatitude -
                                        startCameraLatitude
                                ) *
                        progress

            val cameraLongitude =
                startCameraLongitude +
                        (
                                targetCameraLongitude -
                                        startCameraLongitude
                                ) *
                        progress

            val coordinates = GeoCoordinates(
                latitude,
                longitude
            )

            marker.coordinates =
                coordinates

            marker.bearing =
                vehicleBearing.toDouble()

            if (
                shouldSnapToRoute &&
                activeRoute != null
            ) {
                val frameNow =
                    SystemClock.elapsedRealtime()

                if (
                    frameNow -
                    animationState.lastVisualRouteUpdateMs >=
                    VISUAL_ROUTE_UPDATE_INTERVAL_MS
                ) {
                    visualRouteProgressTracker
                        .update(
                            coordinates
                        )
                        ?.let { visualProgress ->

                            routeRenderer.updateRouteProgress(
                                route = activeRoute,
                                matchedSegmentIndex =
                                    visualProgress.matchedSegmentIndex,
                                matchedCoordinates =
                                    visualProgress.matchedCoordinates
                            )
                        }

                    animationState.lastVisualRouteUpdateMs =
                        frameNow
                }
            }

            val cameraCoordinates =
                GeoCoordinates(
                    cameraLatitude,
                    cameraLongitude
                )

            if (
                state.isFollowing &&
                !state.isRecentering
            ) {
                cameraController.follow(
                    coordinates = cameraCoordinates,
                    bearingDegrees = cameraBearing,
                    zoomDistanceMeters =
                        cameraDistanceMeters
                )
            }

            animationState.latitude = latitude
            animationState.longitude = longitude
            animationState.bearing =
                vehicleBearing

            animationState.cameraBearing =
                cameraBearing
            animationState.cameraLatitude =
                cameraLatitude

            animationState.cameraLongitude =
                cameraLongitude
            animationState.cameraDistanceMeters =
                cameraDistanceMeters
        }
    }


    // Periodic route refresh ETA
    LaunchedEffect(
        navigationLocation?.latitude,
        navigationLocation?.longitude,
        state.destination,
        isDemoMode.value,
        state.isArrived
    ) {
        if (
            isDemoMode.value ||
            state.isArrived
        ) {
            return@LaunchedEffect
        }

        val currentLocation =
            navigationLocation ?: return@LaunchedEffect

        val destination =
            state.destination ?: return@LaunchedEffect

        val currentRoute =
            state.activeRoute ?: return@LaunchedEffect

        val now =
            SystemClock.elapsedRealtime()

        if (
            now - lastRouteRefreshMs.longValue <
            routeRefreshIntervalMs
        ) {
            return@LaunchedEffect
        }


        val isCurrentlyOffRoute =
            state.routeProgress
                ?.distanceFromRouteMeters
                ?.let { distance ->
                    distance >
                            settings.offRouteThresholdMeters
                }
                ?: false

        if (isCurrentlyOffRoute) {
            // Traffic refresh must not act as an implicit reroute.
            lastRouteRefreshMs.longValue = now

            Log.d(
                TAG,
                "Route refresh skipped: vehicle is off route."
            )

            return@LaunchedEffect
        }

        if (isRouteRequestInProgress.value) {
            return@LaunchedEffect
        }

        val currentRemainingDurationSeconds =
            state.routeProgress
                ?.remainingDurationSeconds
                ?: currentRoute.duration.seconds

        lastRouteRefreshMs.longValue = now
        isRouteRequestInProgress.value = true

        val start = GeoCoordinates(
            currentLocation.latitude,
            currentLocation.longitude
        )

        routingController.calculateRoute(
            start = start,
            destination = destination,
            destinationPositionHint =
                state.destinationPositionHint,
            startHeadingDegrees =
                currentLocation.bearingDegrees,
            onSuccess = { candidateRoute ->
                val candidateDurationSeconds =
                    candidateRoute.duration.seconds

                val routeGainSeconds =
                    currentRemainingDurationSeconds -
                            candidateDurationSeconds

                val minimumGainSeconds =
                    settings.minimumRouteGainSeconds.toLong()

                if (
                    routeGainSeconds >=
                    minimumGainSeconds
                ) {
                    state.activeRoute =
                        candidateRoute

                    routeProgressTracker.setRoute(
                        candidateRoute
                    )
                    visualRouteProgressTracker.setRoute(
                        candidateRoute
                    )

                    animationState.lastVisualRouteUpdateMs = 0L

                    maneuverProgressTracker.setRoute(
                        candidateRoute
                    )
                    state.routeProgress =
                        routeProgressTracker.update(
                            start
                        )
                    maneuverGuidance.value =
                        maneuverProgressTracker.update(
                            state.routeProgress
                        )
                    routeRenderer.showRoute(
                        route = candidateRoute,
                        destination =
                            state.destinationPositionHint
                                ?: destination
                    )

                    Log.d(
                        TAG,
                        "Traffic route accepted: " +
                                "currentEta=$currentRemainingDurationSeconds s, " +
                                "candidateEta=$candidateDurationSeconds s, " +
                                "gain=$routeGainSeconds s"
                    )
                } else {
                    Log.d(
                        TAG,
                        "Traffic route ignored: " +
                                "currentEta=$currentRemainingDurationSeconds s, " +
                                "candidateEta=$candidateDurationSeconds s, " +
                                "gain=$routeGainSeconds s, " +
                                "required=$minimumGainSeconds s"
                    )
                }

                isRouteRequestInProgress.value =
                    false
            },
            onError = { error ->
                isRouteRequestInProgress.value = false
                Log.e(
                    TAG,
                    "Route refresh failed: ${error.name}"
                )
            }
        )
    }

    val guidanceReservedLeftPx = 0.0
    // Set principal point
    LaunchedEffect(
        state.mapSize.width,
        state.mapSize.height,
        guidanceReservedLeftPx
    ) {
        cameraController.setNavigationPrincipalPoint(
            width = state.mapSize.width,
            height = state.mapSize.height,
            reservedLeftPx =
                guidanceReservedLeftPx
        )
    }

    // Automatically return to follow mode when guidance starts.
    LaunchedEffect(
        state.activeRoute != null
    ) {
        if (state.activeRoute == null) {
            return@LaunchedEffect
        }

        if (state.isRecentering) {
            return@LaunchedEffect
        }

        state.isRecentering = true

        val targetCoordinates =
            GeoCoordinates(
                animationState.latitude,
                animationState.longitude
            )

        cameraController.recenter(
            coordinates = targetCoordinates,
            bearingDegrees = animationState.bearing,
            zoomDistanceMeters =
                animationState.cameraDistanceMeters,
            onFinished = {
                state.isRecentering = false
                state.isFollowing = true
            }
        )
    }

    Box(
        modifier = modifier
    ) {
        AndroidView(
            factory = {
                mapView.apply {
                    setOnTouchListener { _, event ->
                        if (
                            event.actionMasked == MotionEvent.ACTION_MOVE &&
                            state.isFollowing
                        ) {
                            state.isFollowing = false
                        }
                        false
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    state.mapSize = size
                }
        )
//        Aw11MapPixelMask(
//            modifier = Modifier
//                .fillMaxSize()
//        )
        if (
            state.activeRoute == null &&
            state.selectedLocation == null
        ) {
            Aw11SearchPanel(
                isOpen = state.isSearchOpen,
                query = state.searchQuery,
                results = state.searchResults,
                isSearching = state.isSearching,
                error = state.searchError,
                showOpenButton = false,
                onOpen = {
                    state.openSearch()
                },
                onQueryChange = { query ->
                    state.searchQuery = query
                },
                onSearch = { queryText ->

                    state.searchQuery = queryText
                    searchPinRenderer.clear()
                    state.startSearch()

                    val center = GeoCoordinates(
                        animationState.latitude,
                        animationState.longitude
                    )

                    searchController.search(
                        queryText = queryText,
                        center = center,
                        onSuccess = { results ->
                            val visibleResults =
                                results.take(
                                    MAX_NUMBER_OF_SEARCH_RESULTS
                                )

                            state.completeSearch(
                                visibleResults
                            )

                            searchPinRenderer.showResults(
                                visibleResults
                            )

                            if (visibleResults.isNotEmpty()) {
                                state.isFollowing = false

                                val searchCoordinates =
                                    buildList {
                                        add(
                                            GeoCoordinates(
                                                animationState.latitude,
                                                animationState.longitude
                                            )
                                        )

                                        addAll(
                                            visibleResults.map {
                                                it.coordinates
                                            }
                                        )
                                    }

                                val reservedLeftPx =
                                    with(density) {
                                        SEARCH_RESULTS_RESERVED_LEFT_DP
                                            .dp
                                            .toPx()
                                            .toDouble()
                                    }

                                val paddingPx =
                                    with(density) {
                                        SEARCH_RESULTS_PADDING_DP
                                            .dp
                                            .toPx()
                                            .toDouble()
                                    }

                                cameraController.showSearchResults(
                                    coordinates =
                                        searchCoordinates,
                                    width =
                                        state.mapSize.width,
                                    height =
                                        state.mapSize.height,
                                    reservedLeftPx =
                                        reservedLeftPx,
                                    paddingPx =
                                        paddingPx
                                )
                            }
                        },
                        onError = { error ->
                            state.failSearch(
                                error.name
                            )
                        }
                    )
                },
                onNearbySearch = { category ->

                    state.searchQuery = ""

                    searchPinRenderer.clear()
                    state.startSearch()

                    val center =
                        GeoCoordinates(
                            animationState.latitude,
                            animationState.longitude
                        )

                    searchController.searchNearby(
                        category = category,
                        center = center,
                        onSuccess = { results ->

                            val visibleResults =
                                results.take(
                                    MAX_NUMBER_OF_SEARCH_RESULTS
                                )

                            state.completeSearch(
                                visibleResults
                            )

                            searchPinRenderer.showResults(
                                visibleResults
                            )

                            if (visibleResults.isNotEmpty()) {
                                state.isFollowing = false

                                val searchCoordinates =
                                    buildList {
                                        add(
                                            GeoCoordinates(
                                                animationState.latitude,
                                                animationState.longitude
                                            )
                                        )

                                        addAll(
                                            visibleResults.map {
                                                it.coordinates
                                            }
                                        )
                                    }

                                val reservedLeftPx =
                                    with(density) {
                                        SEARCH_RESULTS_RESERVED_LEFT_DP
                                            .dp
                                            .toPx()
                                            .toDouble()
                                    }

                                val paddingPx =
                                    with(density) {
                                        SEARCH_RESULTS_PADDING_DP
                                            .dp
                                            .toPx()
                                            .toDouble()
                                    }

                                cameraController.showSearchResults(
                                    coordinates =
                                        searchCoordinates,
                                    width =
                                        state.mapSize.width,
                                    height =
                                        state.mapSize.height,
                                    reservedLeftPx =
                                        reservedLeftPx,
                                    paddingPx =
                                        paddingPx
                                )
                            }
                        },
                        onError = { error ->
                            state.failSearch(
                                error.name
                            )
                        }
                    )
                },
                onClear = {
                    searchPinRenderer.clear()
                    state.clearSearch()
                },
                onClose = {
                    searchPinRenderer.clear()
                    state.closeSearch()
                },
                onResultSelected = { result ->
                    startGuidanceTo(
                        destinationPosition =
                            result.coordinates,
                        destinationAccessPoints =
                            result.accessPoints,
                        destinationTitle =
                            result.title
                    )
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            )
        }

        state.selectedLocation
            ?.takeIf {
                state.activeRoute == null
            }
            ?.let { location ->
                Aw11SelectedLocationPanel(
                    location = location,
                    onGoThere = {
                        val title =
                            location.title
                                ?.takeIf {
                                    it.isNotBlank()
                                }
                                ?: location.address
                                    ?.takeIf {
                                        it.isNotBlank()
                                    }
                                ?: "SELECTED LOCATION"

                        startGuidanceTo(
                            destinationPosition =
                                location.coordinates,
                            destinationTitle =
                                title
                        )
                    },
                    onClose = {
                        mapSelectionRequestId.intValue++

                        selectedLocationRenderer.clear()

                        state.selectedLocation = null
                    },
                    modifier = Modifier
                        .align(
                            Alignment.TopStart
                        )
                        .padding(10.dp)
                )
            }

        state.activeRoute?.let { route ->
            Aw11DemoControls(
                isDemoMode =
                    isDemoMode.value,
                speedMultiplier =
                    demoSpeedMultiplier.value,
                isPaused =
                    isDemoPaused.value,
                onStart = {
                    demoSpeedMultiplier.value =
                        1.0

                    val initialLocation =
                        demoNavigationController.start(
                            route = route,
                            speedMultiplier =
                                demoSpeedMultiplier.value
                        )

                    if (initialLocation != null) {
                        demoLocation.value =
                            initialLocation

                        isDemoMode.value = true

                        isDemoRunning.value = true
                        isDemoPaused.value = false
                        onDemoDataChanged(
                            initialLocation,
                            demoNavigationController.tripData
                        )
                        Log.d(
                            TAG,
                            "Demo started: 1X"
                        )
                    }
                },
                onMultiplierChange = { multiplier ->
                    demoSpeedMultiplier.value =
                        multiplier

                    Log.d(
                        TAG,
                        "Demo speed: ${multiplier}X"
                    )
                },
                onPause = {
                    demoLocation.value =
                        demoNavigationController.pause()
                    onDemoDataChanged(
                        demoLocation.value,
                        demoNavigationController.tripData
                    )
                    isDemoPaused.value =
                        true

                    Log.d(
                        TAG,
                        "Demo paused."
                    )
                },
                onResume = {
                    demoLocation.value =
                        demoNavigationController.resume()
                    onDemoDataChanged(
                        demoLocation.value,
                        demoNavigationController.tripData
                    )
                    isDemoPaused.value =
                        false

                    Log.d(
                        TAG,
                        "Demo resumed."
                    )
                },
                onStop = {
                    demoNavigationController.stop()

                    isDemoRunning.value = false
                    isDemoPaused.value = false
                    isDemoMode.value = false

                    demoLocation.value = null
                    onDemoDataChanged(
                        null,
                        null
                    )
                    Log.d(
                        TAG,
                        "Demo stopped."
                    )
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = 124.dp,
                        end = 10.dp
                    )
            )
        }


        //Eta widget
        // Combined ETA + turn-by-turn guidance header
        state.activeRoute
            ?.takeIf {
                !state.isSearchOpen &&
                        !state.isArrived
            }
            ?.let { route ->

                val progress =
                    state.routeProgress

                Aw11GuidanceHeader(
                    remainingDistanceMeters =
                        progress
                            ?.remainingDistanceMeters
                            ?: route.lengthInMeters,
                    remainingDurationSeconds =
                        progress
                            ?.remainingDurationSeconds
                            ?: route.duration.seconds,
                    guidance =
                        maneuverGuidance.value,
                    onEndGuidance =
                        endGuidance,
                    modifier = Modifier
                        .align(
                            Alignment.TopCenter
                        )
                        .padding(
                            horizontal = 10.dp,
                            vertical = 10.dp
                        )
                )
            }

        if (state.isArrived) {
            state.activeRoute?.let { route ->

                val progress =
                    state.routeProgress

                Aw11RouteInfo(
                    destinationTitle =
                        state.destinationTitle,
                    distanceMeters =
                        progress
                            ?.remainingDistanceMeters
                            ?: route.lengthInMeters,
                    durationSeconds =
                        progress
                            ?.remainingDurationSeconds
                            ?: route.duration.seconds,
                    isArrived = true,
                    onEndGuidance =
                        endGuidance,
                    modifier = Modifier
                        .align(
                            Alignment.Center
                        )
                )
            }
        }

        Aw11RecenterButton(
            visible = !state.isFollowing,
            onClick = {
                if (state.isRecentering) {
                    return@Aw11RecenterButton
                }

                state.isRecentering = true

                val targetCoordinates = GeoCoordinates(
                    animationState.latitude,
                    animationState.longitude
                )

                cameraController.recenter(
                    coordinates = targetCoordinates,
                    bearingDegrees = animationState.bearing,
                    zoomDistanceMeters =
                        animationState.cameraDistanceMeters,
                    onFinished = {
                        state.isRecentering = false
                        state.isFollowing = true
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(10.dp)
        )
    }
}

private fun shortestBearingDelta(
    from: Float,
    to: Float
): Float {
    return ((to - from + 540f) % 360f) - 180f
}

private fun normalizeBearing(
    bearing: Float
): Float {
    return ((bearing % 360f) + 360f) % 360f
}

private fun calculateLookAheadDistanceMeters(
    speedMps: Float
): Double {
    if (speedMps <= LOOK_AHEAD_MIN_SPEED_MPS) {
        return 0.0
    }

    return (
            speedMps *
                    LOOK_AHEAD_SECONDS
            )
        .coerceIn(
            LOOK_AHEAD_MIN_METERS,
            LOOK_AHEAD_MAX_METERS
        )
}

private fun calculateCameraDistanceMeters(
    speedMps: Float
): Double {
    val speedKmh =
        speedMps * 3.6

    return when {
        speedKmh <= 30.0 ->
            500.0

        speedKmh <= 50.0 ->
            interpolateZoom(
                speedKmh,
                30.0,
                50.0,
                500.0,
                575.0
            )

        speedKmh <= 70.0 ->
            interpolateZoom(
                speedKmh,
                50.0,
                70.0,
                575.0,
                650.0
            )

        speedKmh <= 90.0 ->
            interpolateZoom(
                speedKmh,
                70.0,
                90.0,
                650.0,
                725.0
            )

        speedKmh <= 110.0 ->
            interpolateZoom(
                speedKmh,
                90.0,
                110.0,
                725.0,
                950.0
            )

        speedKmh <= 130.0 ->
            interpolateZoom(
                speedKmh,
                110.0,
                130.0,
                950.0,
                1200.0
            )

        else ->
            1350.0
    }
}

private fun interpolateZoom(
    value: Double,
    startValue: Double,
    endValue: Double,
    startZoom: Double,
    endZoom: Double
): Double {
    val fraction =
        (
                (value - startValue) /
                        (endValue - startValue)
                )
            .coerceIn(
                0.0,
                1.0
            )

    return startZoom +
            (endZoom - startZoom) *
            fraction
}

private fun smoothLookAheadTarget(
    target: GeoCoordinates,
    animationState: MapAnimationState,
    updateIntervalMs: Long
): GeoCoordinates {
    if (!animationState.hasLookAheadTarget) {
        animationState.lookAheadLatitude =
            target.latitude

        animationState.lookAheadLongitude =
            target.longitude

        animationState.hasLookAheadTarget =
            true

        return target
    }

    val alpha =
        1.0 -
                exp(
                    -updateIntervalMs.toDouble() /
                            LOOK_AHEAD_SMOOTHING_TIME_MS
                )

    animationState.lookAheadLatitude +=
        (
                target.latitude -
                        animationState.lookAheadLatitude
                ) * alpha

    animationState.lookAheadLongitude +=
        (
                target.longitude -
                        animationState.lookAheadLongitude
                ) * alpha

    return GeoCoordinates(
        animationState.lookAheadLatitude,
        animationState.lookAheadLongitude
    )
}

private fun smoothCameraBearing(
    current: Float,
    target: Float,
    updateIntervalMs: Long
): Float {
    val alpha =
        (
                1.0 -
                        exp(
                            -updateIntervalMs.toDouble() /
                                    CAMERA_BEARING_SMOOTHING_TIME_MS
                        )
                )
            .toFloat()

    val delta =
        shortestBearingDelta(
            current,
            target
        )

    return normalizeBearing(
        current +
                delta * alpha
    )
}

private fun distanceMeters(
    first: GeoCoordinates,
    second: GeoCoordinates
): Double {
    val latitudeRadians =
        Math.toRadians(
            (
                    first.latitude +
                            second.latitude
                    ) / 2.0
        )

    val metersPerDegreeLatitude =
        111_320.0

    val metersPerDegreeLongitude =
        111_320.0 *
                kotlin.math.cos(
                    latitudeRadians
                )

    val x =
        (
                second.longitude -
                        first.longitude
                ) *
                metersPerDegreeLongitude

    val y =
        (
                second.latitude -
                        first.latitude
                ) *
                metersPerDegreeLatitude

    return kotlin.math.hypot(
        x,
        y
    )
}