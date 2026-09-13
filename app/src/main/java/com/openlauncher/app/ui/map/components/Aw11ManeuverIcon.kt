package com.openlauncher.app.ui.map.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.openlauncher.app.R
import com.openlauncher.app.ui.theme.Aw11Primary
import kotlin.math.abs

@Composable
fun Aw11ManeuverIcon(
    actionName: String,
    roundaboutAngleDegrees: Double? = null,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(
            id = maneuverIconResource(
                actionName = actionName,
                roundaboutAngleDegrees = roundaboutAngleDegrees
            )
        ),
        contentDescription = null,
        colorFilter = ColorFilter.tint(
            Aw11Primary
        ),
        modifier = modifier
    )
}

@DrawableRes
private fun maneuverIconResource(
    actionName: String,
    roundaboutAngleDegrees: Double?
): Int {
    return when {
        actionName == "ARRIVE" ->
            R.drawable.ic_maneuver_arrive_4col_2white

        actionName.contains(
            "ROUNDABOUT"
        ) ->
            roundaboutIconResource(
                actionName = actionName,
                roundaboutAngleDegrees =
                    roundaboutAngleDegrees
            )

        actionName == "SLIGHT_LEFT_TURN" ->
            R.drawable.ic_maneuver_slight_left

        actionName == "SLIGHT_RIGHT_TURN" ->
            R.drawable.ic_maneuver_slight_right

        actionName == "SHARP_LEFT_TURN" ->
            R.drawable.ic_maneuver_sharp_left

        actionName == "SHARP_RIGHT_TURN" ->
            R.drawable.ic_maneuver_sharp_right

        actionName == "LEFT_FORK" ->
            R.drawable.ic_maneuver_keep_left

        actionName == "RIGHT_FORK" ->
            R.drawable.ic_maneuver_keep_right

        actionName == "MIDDLE_FORK" ->
            R.drawable.ic_maneuver_straight

        actionName == "LEFT_U_TURN" ->
            R.drawable.ic_maneuver_uturn_left

        actionName == "RIGHT_U_TURN" ->
            R.drawable.ic_maneuver_uturn_right

        actionName == "LEFT_RAMP" ->
            R.drawable.ic_maneuver_ramp_left

        actionName == "RIGHT_RAMP" ->
            R.drawable.ic_maneuver_ramp_right

        actionName == "LEFT_EXIT" ->
            R.drawable.ic_maneuver_exit_left

        actionName == "RIGHT_EXIT" ->
            R.drawable.ic_maneuver_exit_right

        actionName == "ENTER_HIGHWAY_FROM_LEFT" ->
            R.drawable.ic_maneuver_ramp_right

        actionName == "ENTER_HIGHWAY_FROM_RIGHT" ->
            R.drawable.ic_maneuver_ramp_left

        actionName == "LEFT_TURN" ->
            R.drawable.ic_maneuver_left

        actionName == "RIGHT_TURN" ->
            R.drawable.ic_maneuver_right

        actionName == "CONTINUE_ON" ->
            R.drawable.ic_maneuver_straight

        actionName.contains("LEFT") ->
            R.drawable.ic_maneuver_left

        actionName.contains("RIGHT") ->
            R.drawable.ic_maneuver_right

        else ->
            R.drawable.ic_maneuver_straight
    }
}

@DrawableRes
private fun roundaboutIconResource(
    actionName: String,
    roundaboutAngleDegrees: Double?
): Int {

    if (roundaboutAngleDegrees != null) {

        val angle =
            abs(roundaboutAngleDegrees)

        val rightDriving =
            roundaboutAngleDegrees >= 0.0

        return when {
            angle < -45.0 ->
                R.drawable.ic_maneuver_roundabout_left

            angle > 45.0 ->
                R.drawable.ic_maneuver_roundabout_right

            else ->
                R.drawable.ic_maneuver_roundabout_straight
        }
    }

    // Fallback for maneuvers where HERE does not provide
    // a roundabout angle.
    val exitNumber =
        actionName
            .substringAfter(
                "_ROUNDABOUT_EXIT",
                ""
            )
            .toIntOrNull()

    return when (exitNumber) {
        1 ->
            R.drawable.ic_maneuver_roundabout_right

        2 ->
            R.drawable.ic_maneuver_roundabout_straight

        null ->
            R.drawable.ic_maneuver_roundabout_straight

        else ->
            R.drawable.ic_maneuver_roundabout_left
    }
}