package com.openlauncher.app.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlauncher.app.ui.map.navigation.ManeuverGuidance
import com.openlauncher.app.ui.theme.Aw11Background
import com.openlauncher.app.ui.theme.Aw11Border
import com.openlauncher.app.ui.theme.Aw11Primary
import com.openlauncher.app.ui.theme.Aw11Secondary
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun Aw11GuidanceHeader(
    remainingDistanceMeters: Int,
    remainingDurationSeconds: Long,
    guidance: ManeuverGuidance?,
    onEndGuidance: () -> Unit,
    modifier: Modifier = Modifier
) {
    val arrivalTime =
        LocalTime.now()
            .plusSeconds(
                remainingDurationSeconds.coerceAtLeast(0L)
            )
            .format(
                DateTimeFormatter.ofPattern("HH:mm")
            )

    val durationMinutes =
        when {
            remainingDurationSeconds <= 0L -> 0L
            else ->
                (remainingDurationSeconds + 59L) / 60L
        }

    val routeDistance =
        formatRouteDistance(
            remainingDistanceMeters
        )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(104.dp)
            .background(
                Aw11Background.copy(
                    alpha = 0.96f
                )
            )
            .border(
                width = 1.dp,
                color = Aw11Border.copy(
                    alpha = 0.75f
                )
            )
            .padding(
                horizontal = 12.dp,
                vertical = 8.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        // ETA
        GuidanceValueBlock(
            label = "ETA",
            value = arrivalTime,
            secondaryValue =
                "$durationMinutes MIN",
            modifier = Modifier
                .weight(0.18f)
                .fillMaxHeight()
        )

        GuidanceDivider()

        // REMAINING DISTANCE
        GuidanceValueBlock(
            label = "DISTANCE",
            value = routeDistance,
            modifier = Modifier
                .weight(0.23f)
                .fillMaxHeight()
                .padding(start = 12.dp)
        )

        GuidanceDivider()

        // NEXT MANEUVER
        Row(
            modifier = Modifier
                .weight(0.59f)
                .fillMaxHeight()
                .padding(start = 12.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            if (guidance != null) {

                Aw11ManeuverIcon(
                    actionName =
                        guidance.actionName,
                    roundaboutAngleDegrees =
                        guidance.roundaboutAngleDegrees,
                    modifier = Modifier
                        .size(66.dp)
                )

                Spacer(
                    Modifier.width(10.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Row(
                        verticalAlignment =
                            Alignment.Bottom
                    ) {
                        Text(
                            text = "IN ",
                            color = Aw11Secondary,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )

                        Text(
                            text =
                                formatManeuverDistance(
                                    guidance.distanceMeters
                                ),
                            color = Aw11Primary,
                            fontSize = 22.sp,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Text(
                        text =
                            maneuverActionLabel(
                                guidance.actionName
                            ),
                        color = Aw11Primary,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
                    )

                    if (
                        guidance.roadName
                            .isNotBlank()
                    ) {
                        Text(
                            text =
                                guidance.roadName
                                    .uppercase(),
                            color = Aw11Secondary,
                            fontSize = 9.sp,
                            letterSpacing = 0.4.sp,
                            maxLines = 1,
                            overflow =
                                TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Text(
                    text = "CALCULATING...",
                    color = Aw11Secondary,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.width(10.dp))

            GuidanceDivider()

            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .height(52.dp)
                    .border(
                        width = 1.dp,
                        color = Aw11Primary
                    )
                    .clickable(
                        onClick = onEndGuidance
                    )
                    .padding(
                        horizontal = 12.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        text = "✖",
                        color = Aw11Primary,
                        fontSize = 18.sp
                    )

                    Text(
                        text = "END",
                        color = Aw11Primary,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun GuidanceValueBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    secondaryValue: String? = null
) {
    Column(
        modifier = modifier,
        verticalArrangement =
            Arrangement.Center
    ) {
        Text(
            text = label,
            color = Aw11Secondary,
            fontSize = 9.sp,
            letterSpacing = 1.sp
        )

        Text(
            text = value,
            color = Aw11Primary,
            fontSize = 24.sp,
            letterSpacing = 0.5.sp,
            maxLines = 1
        )

        secondaryValue?.let {
            Text(
                text = it,
                color = Aw11Secondary,
                fontSize = 9.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun RowScope.GuidanceDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight(0.78f)
            .background(
                Aw11Border.copy(
                    alpha = 0.65f
                )
            )
    )
}

private fun formatRouteDistance(
    distanceMeters: Int
): String {
    val distance =
        distanceMeters.coerceAtLeast(0)

    return if (distance < 1000) {
        "$distance M"
    } else {
        "%.1f KM".format(
            distance / 1000.0
        )
    }
}

private fun formatManeuverDistance(
    distanceMeters: Int
): String {
    val distance =
        distanceMeters.coerceAtLeast(0)

    if (distance <= 8) {
        return "NOW"
    }

    if (distance >= 1000) {
        return "%.1f KM".format(
            distance / 1000.0
        )
    }

    val rounded =
        when {
            distance >= 500 ->
                roundToNearest(
                    distance,
                    50
                )

            distance >= 100 ->
                roundToNearest(
                    distance,
                    10
                )

            distance >= 50 ->
                roundToNearest(
                    distance,
                    5
                )

            else -> distance
        }

    return "$rounded M"
}

private fun roundToNearest(
    value: Int,
    step: Int
): Int {
    return (
            (value + step / 2) /
                    step
            ) * step
}

private fun maneuverActionLabel(
    actionName: String
): String {
    return when {
        actionName == "ARRIVE" ->
            "ARRIVE"

        actionName == "CONTINUE_ON" ->
            "CONTINUE"

        actionName == "LEFT_TURN" ->
            "TURN LEFT"

        actionName == "RIGHT_TURN" ->
            "TURN RIGHT"

        actionName == "SHARP_LEFT_TURN" ->
            "SHARP LEFT"

        actionName == "SHARP_RIGHT_TURN" ->
            "SHARP RIGHT"

        actionName == "SLIGHT_LEFT_TURN" ->
            "SLIGHT LEFT"

        actionName == "SLIGHT_RIGHT_TURN" ->
            "SLIGHT RIGHT"

        actionName == "LEFT_FORK" ->
            "KEEP LEFT"

        actionName == "RIGHT_FORK" ->
            "KEEP RIGHT"

        actionName == "LEFT_EXIT" ->
            "LEFT EXIT"

        actionName == "RIGHT_EXIT" ->
            "RIGHT EXIT"

        actionName ==
                "ENTER_HIGHWAY_FROM_LEFT" ||
                actionName ==
                "ENTER_HIGHWAY_FROM_RIGHT" ->
            "ENTER HIGHWAY"

        actionName == "LEFT_U_TURN" ||
                actionName == "RIGHT_U_TURN" ->
            "U-TURN"

        actionName.contains(
            "_ROUNDABOUT_EXIT"
        ) -> {
            val exitNumber =
                actionName
                    .substringAfter(
                        "_ROUNDABOUT_EXIT",
                        ""
                    )
                    .toIntOrNull()

            if (exitNumber != null) {
                "ROUNDABOUT EXIT $exitNumber"
            } else {
                "ROUNDABOUT EXIT"
            }
        }

        actionName.endsWith(
            "_ROUNDABOUT_ENTER"
        ) ->
            "ENTER ROUNDABOUT"

        else ->
            actionName.replace(
                "_",
                " "
            )
    }
}