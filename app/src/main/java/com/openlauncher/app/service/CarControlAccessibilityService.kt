package com.openlauncher.app.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CarControlAccessibilityService : AccessibilityService() {

    private enum class ShutdownState {
        IDLE,
        WAITING_FOR_POWER_MENU,
        WAITING_FOR_CONFIRMATION,
        SHUTDOWN_REQUESTED
    }

    companion object {
        private const val TAG = "CarControl"

        private const val POWER_OFF_LABEL =
            "Wyłączanie zasilania"

        private const val CONFIRMATION_TEXT =
            "Dotknij ponownie, aby wyłączyć tablet"

        private val _isConnected =
            MutableStateFlow(false)

        val isConnected: StateFlow<Boolean> =
            _isConnected.asStateFlow()

        // Give Samsung SystemUI time to display the first power menu.
        private const val INITIAL_UI_DELAY_MS = 500L

        // How often we inspect the currently visible SystemUI.
        private const val POLL_INTERVAL_MS = 250L

        // Prevent repeated clicks from being sent too quickly.
        private const val MIN_CLICK_INTERVAL_MS = 700L

        // Stop retrying after this time.
        private const val MAX_SEQUENCE_DURATION_MS = 15_000L
    }

    private val handler =
        Handler(Looper.getMainLooper())

    private var shutdownState =
        ShutdownState.IDLE

    private var sequenceStartedAt = 0L
    private var lastClickAt = 0L

    private var firstClickAttempts = 0
    private var secondClickAttempts = 0

    override fun onServiceConnected() {
        super.onServiceConnected()

        _isConnected.value = true

        Log.i(
            TAG,
            "Accessibility service connected"
        )
    }

    override fun onUnbind(
        intent: Intent?
    ): Boolean {
        _isConnected.value = false

        Log.w(
            TAG,
            "Accessibility service disconnected"
        )

        return super.onUnbind(intent)
    }

    override fun onKeyEvent(
        event: KeyEvent
    ): Boolean {

        if (event.keyCode != KeyEvent.KEYCODE_F11) {
            // F12 and all other keys must continue to reach the launcher.
            return false
        }

        when (event.action) {

            KeyEvent.ACTION_DOWN -> {
                if (
                    event.repeatCount == 0 &&
                    shutdownState == ShutdownState.IDLE
                ) {
                    startShutdownSequence()
                }
            }

            KeyEvent.ACTION_UP -> {
                cancelShutdownSequence()
            }
        }

        return true
    }

    private fun startShutdownSequence() {
        Log.i(
            TAG,
            "ACC OFF received"
        )

        handler.removeCallbacksAndMessages(null)

        shutdownState =
            ShutdownState.WAITING_FOR_POWER_MENU

        sequenceStartedAt =
            SystemClock.elapsedRealtime()

        lastClickAt = 0L
        firstClickAttempts = 0
        secondClickAttempts = 0

        val result =
            performGlobalAction(
                GLOBAL_ACTION_POWER_DIALOG
            )

        Log.i(
            TAG,
            "Power dialog requested: $result"
        )

        handler.postDelayed(
            {
                pollShutdownUi()
            },
            INITIAL_UI_DELAY_MS
        )
    }

    private fun pollShutdownUi() {

        if (shutdownState == ShutdownState.IDLE) {
            return
        }

        val now =
            SystemClock.elapsedRealtime()

        if (
            now - sequenceStartedAt >
            MAX_SEQUENCE_DURATION_MS
        ) {
            Log.w(
                TAG,
                "Shutdown UI sequence timed out"
            )

            // Do not click anything else automatically.
            // The state will be reset when ACC returns.
            return
        }

        val root =
            rootInActiveWindow

        if (root == null) {
            Log.d(
                TAG,
                "Accessibility root is null"
            )

            scheduleNextPoll()
            return
        }

        val confirmationVisible =
            containsText(
                root,
                CONFIRMATION_TEXT
            )

        val powerOffNode =
            findPowerOffNode(root)

        Log.d(
            TAG,
            "state=$shutdownState, " +
                    "confirmation=$confirmationVisible, " +
                    "powerButton=${powerOffNode != null}"
        )

        when (shutdownState) {

            ShutdownState.WAITING_FOR_POWER_MENU -> {

                if (confirmationVisible) {
                    // Samsung already switched to the confirmation screen.
                    shutdownState =
                        ShutdownState.WAITING_FOR_CONFIRMATION

                    scheduleNextPoll()
                    return
                }

                if (
                    powerOffNode != null &&
                    canClickAgain(now)
                ) {
                    val clicked =
                        clickNode(powerOffNode)

                    firstClickAttempts++

                    Log.i(
                        TAG,
                        "First power click " +
                                "attempt=$firstClickAttempts " +
                                "result=$clicked"
                    )

                    if (clicked) {
                        lastClickAt = now

                        // Do not assume the screen changed.
                        // We verify it during the next poll.
                        shutdownState =
                            ShutdownState.WAITING_FOR_CONFIRMATION
                    }
                }
            }

            ShutdownState.WAITING_FOR_CONFIRMATION -> {

                if (confirmationVisible) {

                    // The confirmation screen is genuinely visible.
                    // Now it is safe to perform the second click.
                    if (
                        powerOffNode != null &&
                        canClickAgain(now)
                    ) {
                        val clicked =
                            clickNode(powerOffNode)

                        secondClickAttempts++

                        Log.i(
                            TAG,
                            "Second power click " +
                                    "attempt=$secondClickAttempts " +
                                    "result=$clicked"
                        )

                        if (clicked) {
                            lastClickAt = now

                            shutdownState =
                                ShutdownState.SHUTDOWN_REQUESTED
                        }
                    }

                } else {

                    // We expected the confirmation screen but it has not
                    // appeared yet. If the original power menu is still
                    // present, retry the first click.
                    if (
                        powerOffNode != null &&
                        canClickAgain(now)
                    ) {
                        val clicked =
                            clickNode(powerOffNode)

                        firstClickAttempts++

                        Log.i(
                            TAG,
                            "Retry first power click " +
                                    "attempt=$firstClickAttempts " +
                                    "result=$clicked"
                        )

                        if (clicked) {
                            lastClickAt = now
                        }
                    }
                }
            }

            ShutdownState.SHUTDOWN_REQUESTED -> {

                if (confirmationVisible) {

                    // ACTION_CLICK may return true even though Samsung
                    // did not actually accept the click.
                    //
                    // If the confirmation screen is still visible,
                    // retry the second click.
                    if (
                        powerOffNode != null &&
                        canClickAgain(now)
                    ) {
                        val clicked =
                            clickNode(powerOffNode)

                        secondClickAttempts++

                        Log.i(
                            TAG,
                            "Retry second power click " +
                                    "attempt=$secondClickAttempts " +
                                    "result=$clicked"
                        )

                        if (clicked) {
                            lastClickAt = now
                        }
                    }

                } else {

                    // The confirmation screen disappeared after the
                    // second click. Android is now shutting down.
                    Log.i(
                        TAG,
                        "Shutdown confirmation disappeared - " +
                                "Android shutdown started"
                    )

                    handler.removeCallbacksAndMessages(
                        null
                    )

                    // Keep SHUTDOWN_REQUESTED state until ACC changes.
                    return
                }
            }

            ShutdownState.IDLE -> {
                return
            }
        }

        scheduleNextPoll()
    }

    private fun scheduleNextPoll() {
        handler.postDelayed(
            {
                pollShutdownUi()
            },
            POLL_INTERVAL_MS
        )
    }

    private fun canClickAgain(
        now: Long
    ): Boolean {
        return (
                lastClickAt == 0L ||
                        now - lastClickAt >=
                        MIN_CLICK_INTERVAL_MS
                )
    }

    private fun findPowerOffNode(
        root: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {

        // First use Android's built-in text lookup.
        val matches =
            root.findAccessibilityNodeInfosByText(
                POWER_OFF_LABEL
            )

        for (node in matches) {
            if (
                matchesText(
                    node,
                    POWER_OFF_LABEL
                )
            ) {
                return node
            }
        }

        // Samsung SystemUI sometimes exposes the label only through
        // contentDescription, so recursively inspect the tree as fallback.
        return findNodeRecursively(
            root,
            POWER_OFF_LABEL
        )
    }

    private fun findNodeRecursively(
        node: AccessibilityNodeInfo,
        expectedText: String
    ): AccessibilityNodeInfo? {

        if (
            matchesText(
                node,
                expectedText
            )
        ) {
            return node
        }

        for (
        index in
        0 until node.childCount
        ) {
            val child =
                node.getChild(index)
                    ?: continue

            val result =
                findNodeRecursively(
                    child,
                    expectedText
                )

            if (result != null) {
                return result
            }
        }

        return null
    }

    private fun containsText(
        node: AccessibilityNodeInfo,
        expectedText: String
    ): Boolean {

        if (
            matchesText(
                node,
                expectedText
            )
        ) {
            return true
        }

        for (
        index in
        0 until node.childCount
        ) {
            val child =
                node.getChild(index)
                    ?: continue

            if (
                containsText(
                    child,
                    expectedText
                )
            ) {
                return true
            }
        }

        return false
    }

    private fun matchesText(
        node: AccessibilityNodeInfo,
        expectedText: String
    ): Boolean {

        val expected =
            expectedText
                .trim()
                .lowercase()

        val text =
            node.text
                ?.toString()
                ?.trim()
                ?.lowercase()

        val description =
            node.contentDescription
                ?.toString()
                ?.trim()
                ?.lowercase()

        return (
                text?.contains(expected) == true ||
                        description?.contains(expected) == true
                )
    }

    private fun clickNode(
        node: AccessibilityNodeInfo
    ): Boolean {

        var current:
                AccessibilityNodeInfo? = node

        while (current != null) {

            if (
                current.isClickable &&
                current.isEnabled
            ) {
                Log.d(
                    TAG,
                    "Clicking node: " +
                            "text=${current.text}, " +
                            "description=${current.contentDescription}, " +
                            "class=${current.className}"
                )

                return current.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )
            }

            current =
                current.parent
        }

        Log.w(
            TAG,
            "No clickable parent found for power button"
        )

        return false
    }

    private fun cancelShutdownSequence() {
        Log.i(
            TAG,
            "ACC ON received"
        )

        handler.removeCallbacksAndMessages(null)

        val root =
            rootInActiveWindow

        val confirmationVisible =
            root?.let {
                containsText(
                    it,
                    CONFIRMATION_TEXT
                )
            } == true

        when {

            confirmationVisible -> {
                // First BACK closes Samsung's confirmation screen.
                performGlobalAction(
                    GLOBAL_ACTION_BACK
                )

                // Second BACK closes the original three-button power menu.
                handler.postDelayed(
                    {
                        performGlobalAction(
                            GLOBAL_ACTION_BACK
                        )
                    },
                    300L
                )
            }

            shutdownState ==
                    ShutdownState.WAITING_FOR_POWER_MENU ||
                    shutdownState ==
                    ShutdownState.WAITING_FOR_CONFIRMATION -> {

                // Only the main power menu is currently visible.
                performGlobalAction(
                    GLOBAL_ACTION_BACK
                )
            }

            // If the confirmation screen has already disappeared after
            // the second click, Android shutdown is committed.
            // Do not send BACK into another screen.
            else -> Unit
        }

        shutdownState =
            ShutdownState.IDLE
    }

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {
        // UI state is inspected by the polling loop.
    }

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        _isConnected.value = false

        handler.removeCallbacksAndMessages(null)

        super.onDestroy()
    }
}