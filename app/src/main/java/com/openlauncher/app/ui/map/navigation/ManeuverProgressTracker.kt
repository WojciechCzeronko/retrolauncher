package com.openlauncher.app.ui.map.navigation

import com.here.sdk.core.GeoCoordinates
import com.here.sdk.routing.Route
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val MANEUVER_PASS_TOLERANCE_METERS = 8.0
private const val TAG = "AW11_NAV"

data class ManeuverGuidance(
    val actionName: String,
    val distanceMeters: Int,
    val roadName: String,
    val instruction: String,
    val roundaboutAngleDegrees: Double?
)

class ManeuverProgressTracker {

    private var maneuvers:
            List<RouteManeuverPoint> =
        emptyList()

    private var totalGeometryLengthMeters =
        0.0

    fun setRoute(
        route: Route
    ) {
        val vertices =
            route.geometry.vertices

        if (vertices.size < 2) {
            clear()
            return
        }

        val cumulativeDistances =
            DoubleArray(
                vertices.size
            )

        var cumulativeDistance =
            0.0

        for (
        index in
        0 until vertices.lastIndex
        ) {
            cumulativeDistance +=
                vertices[index]
                    .distanceTo(
                        vertices[index + 1]
                    )

            cumulativeDistances[
                index + 1
            ] = cumulativeDistance
        }

        totalGeometryLengthMeters =
            cumulativeDistance

        maneuvers =
            route.sections
                .flatMap { section ->
                    section.maneuvers
                }
                .filter { maneuver ->
                    maneuver.action.name !=
                            "DEPART"
                }
                .map { maneuver ->
                    val closestVertexIndex =
                        findClosestVertexIndex(
                            coordinates =
                                maneuver.coordinates,
                            vertices =
                                vertices
                        )
                    if (
                        maneuver.action.name
                            .contains("ROUNDABOUT")
                    ) {

                        val maneuverDistance =
                            cumulativeDistances[
                                closestVertexIndex
                            ]

                        val angle60 =
                            calculateGeometryTurnAngle(
                                maneuverDistanceMeters =
                                    maneuverDistance,
                                vertices = vertices,
                                cumulativeDistances =
                                    cumulativeDistances,
                                sampleDistanceMeters = 60.0
                            )

                        val angle100 =
                            calculateGeometryTurnAngle(
                                maneuverDistanceMeters =
                                    maneuverDistance,
                                vertices = vertices,
                                cumulativeDistances =
                                    cumulativeDistances,
                                sampleDistanceMeters = 100.0
                            )
                    }


                    val geometryRoundaboutAngle =
                        if (
                            maneuver.action.name
                                .contains("ROUNDABOUT")
                        ) {
                            calculateGeometryTurnAngle(
                                maneuverDistanceMeters =
                                    cumulativeDistances[
                                        closestVertexIndex
                                    ],
                                vertices = vertices,
                                cumulativeDistances =
                                    cumulativeDistances,
                                sampleDistanceMeters = 100.0
                            )
                                ?: calculateGeometryTurnAngle(
                                    maneuverDistanceMeters =
                                        cumulativeDistances[
                                            closestVertexIndex
                                        ],
                                    vertices = vertices,
                                    cumulativeDistances =
                                        cumulativeDistances,
                                    sampleDistanceMeters = 60.0
                                )
                        } else {
                            null
                        }
                    RouteManeuverPoint(
                        actionName =
                            maneuver.action.name,
                        instruction =
                            maneuver.text.trim(),
                        roundaboutAngleDegrees =
                            geometryRoundaboutAngle,
                        distanceAlongGeometryMeters =
                            cumulativeDistances[
                                closestVertexIndex
                            ]
                    )
                }
                .sortedBy { maneuver ->
                    maneuver
                        .distanceAlongGeometryMeters
                }
    }

    fun update(
        progress: RouteProgress?
    ): ManeuverGuidance? {
        if (
            progress == null ||
            maneuvers.isEmpty() ||
            totalGeometryLengthMeters <= 0.0
        ) {
            return null
        }

        val progressDistanceMeters =
            totalGeometryLengthMeters *
                    progress.progressFraction

        val nextManeuverIndex =
            maneuvers.indexOfFirst { maneuver ->
                maneuver
                    .distanceAlongGeometryMeters >=
                        progressDistanceMeters -
                        MANEUVER_PASS_TOLERANCE_METERS
            }

        if (nextManeuverIndex < 0) {
            return null
        }

        val nextManeuver =
            maneuvers[nextManeuverIndex]

        val displayManeuver =
            if (
                nextManeuver.actionName
                    .endsWith("_ROUNDABOUT_ENTER")
            ) {
                maneuvers
                    .drop(nextManeuverIndex + 1)
                    .takeWhile {
                        it.actionName.contains(
                            "ROUNDABOUT"
                        )
                    }
                    .firstOrNull {
                        it.actionName.contains(
                            "_ROUNDABOUT_EXIT"
                        )
                    }
                    ?: nextManeuver
            } else {
                nextManeuver
            }

        val remainingDistanceMeters =
            (
                    nextManeuver
                        .distanceAlongGeometryMeters -
                            progressDistanceMeters
                    )
                .coerceAtLeast(0.0)
                .roundToInt()


        return ManeuverGuidance(
            actionName =
                displayManeuver.actionName,
            distanceMeters =
                remainingDistanceMeters,
            roadName =
                extractRoadName(
                    displayManeuver.instruction
                ),
            instruction =
                displayManeuver.instruction,
            roundaboutAngleDegrees =
                displayManeuver
                    .roundaboutAngleDegrees
        )
    }

    fun clear() {
        maneuvers =
            emptyList()

        totalGeometryLengthMeters =
            0.0
    }

    private fun findClosestVertexIndex(
        coordinates: GeoCoordinates,
        vertices: List<GeoCoordinates>
    ): Int {
        return vertices.indices
            .minByOrNull { index ->
                coordinates.distanceTo(
                    vertices[index]
                )
            }
            ?: 0
    }
    private fun extractRoadName(
        instruction: String
    ): String {
        val withoutDistance =
            instruction
                .substringBefore(
                    ". Go for",
                    instruction
                )
                .trim()

        val separators =
            listOf(
                " onto ",
                " toward ",
                " into ",
                " on "
            )

        for (separator in separators) {
            val index =
                withoutDistance.indexOf(
                    separator,
                    ignoreCase = true
                )

            if (index >= 0) {
                return withoutDistance
                    .substring(
                        index + separator.length
                    )
                    .trim()
            }
        }

        return withoutDistance
    }

    private data class RouteManeuverPoint(
        val actionName: String,
        val instruction: String,
        val roundaboutAngleDegrees: Double?,
        val distanceAlongGeometryMeters: Double
    )

    private fun calculateGeometryTurnAngle(
        maneuverDistanceMeters: Double,
        vertices: List<GeoCoordinates>,
        cumulativeDistances: DoubleArray,
        sampleDistanceMeters: Double
    ): Double? {

        val beforeFar =
            coordinateNearDistance(
                maneuverDistanceMeters -
                        sampleDistanceMeters,
                vertices,
                cumulativeDistances
            ) ?: return null

        val beforeNear =
            coordinateNearDistance(
                maneuverDistanceMeters -
                        sampleDistanceMeters / 2.0,
                vertices,
                cumulativeDistances
            ) ?: return null

        val afterNear =
            coordinateNearDistance(
                maneuverDistanceMeters +
                        sampleDistanceMeters / 2.0,
                vertices,
                cumulativeDistances
            ) ?: return null

        val afterFar =
            coordinateNearDistance(
                maneuverDistanceMeters +
                        sampleDistanceMeters,
                vertices,
                cumulativeDistances
            ) ?: return null

        if (
            beforeFar.distanceTo(beforeNear) < 2.0 ||
            afterNear.distanceTo(afterFar) < 2.0
        ) {
            return null
        }

        val incomingBearing =
            bearingDegrees(
                beforeFar,
                beforeNear
            )

        val outgoingBearing =
            bearingDegrees(
                afterNear,
                afterFar
            )

        return normalizeAngle(
            outgoingBearing -
                    incomingBearing
        )
    }

    private fun coordinateNearDistance(
        targetDistanceMeters: Double,
        vertices: List<GeoCoordinates>,
        cumulativeDistances: DoubleArray
    ): GeoCoordinates? {

        if (vertices.isEmpty()) {
            return null
        }

        val target =
            targetDistanceMeters.coerceIn(
                0.0,
                cumulativeDistances.last()
            )

        val index =
            cumulativeDistances.indices
                .minByOrNull { index ->
                    abs(
                        cumulativeDistances[index] -
                                target
                    )
                }
                ?: return null

        return vertices[index]
    }

    private fun bearingDegrees(
        from: GeoCoordinates,
        to: GeoCoordinates
    ): Double {

        val lat1 =
            Math.toRadians(from.latitude)

        val lat2 =
            Math.toRadians(to.latitude)

        val deltaLongitude =
            Math.toRadians(
                to.longitude -
                        from.longitude
            )

        val y =
            sin(deltaLongitude) *
                    cos(lat2)

        val x =
            cos(lat1) *
                    sin(lat2) -
                    sin(lat1) *
                    cos(lat2) *
                    cos(deltaLongitude)

        return (
                Math.toDegrees(
                    atan2(y, x)
                ) + 360.0
                ) % 360.0
    }

    private fun normalizeAngle(
        angle: Double
    ): Double =
        (
                angle +
                        540.0
                ) % 360.0 - 180.0
}