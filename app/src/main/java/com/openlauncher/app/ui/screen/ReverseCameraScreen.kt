package com.openlauncher.app.ui.screen

import android.graphics.SurfaceTexture
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import com.jiangdg.usb.USBMonitor
import com.jiangdg.uvc.UVCCamera

private const val PREVIEW_WIDTH = 1280
private const val PREVIEW_HEIGHT = 720

@Composable
fun ReverseCameraScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var status by remember {
        mutableStateOf("CAMERA INITIALIZING")
    }

    val cameraView = remember {
        AspectRatioTextureView(context).apply {
            setAspectRatio(
                PREVIEW_WIDTH,
                PREVIEW_HEIGHT
            )
        }
    }

    DisposableEffect(cameraView) {

        val usbMonitor =
            USBMonitor.getInstance(context)

        var currentCamera: UVCCamera? = null

        var activeDeviceId: Int? = null
        var openingDeviceId: Int? = null
        var permissionRequestedDeviceId: Int? = null

        fun stopCamera() {
            val camera = currentCamera

            currentCamera = null
            activeDeviceId = null
            openingDeviceId = null

            if (camera != null) {
                runCatching {
                    camera.stopPreview()
                }

                runCatching {
                    camera.destroy()
                }
            }
        }

        fun openCamera(
            device: UsbDevice,
            ctrlBlock: USBMonitor.UsbControlBlock
        ) {
            if (!cameraView.isAvailable) {
                status = "CAMERA SURFACE NOT READY"
                return
            }

            val surfaceTexture =
                cameraView.surfaceTexture

            if (surfaceTexture == null) {
                status = "CAMERA SURFACE NOT READY"
                return
            }

            if (
                activeDeviceId == device.deviceId ||
                openingDeviceId == device.deviceId
            ) {
                return
            }

            openingDeviceId = device.deviceId

            status = "OPENING CAMERA"

            var camera: UVCCamera? = null

            try {
                stopCamera()

                openingDeviceId = device.deviceId

                camera = UVCCamera()

                camera.open(ctrlBlock)

                /*
                 * Minimal UVC preview path:
                 *
                 * USB/UVC
                 *   ↓
                 * MJPEG 1280x720
                 *   ↓
                 * SurfaceTexture
                 *
                 * No frame callback.
                 * No NV21 conversion.
                 * No ByteArray copy.
                 * No AUSBC render pipeline.
                 */
                camera.setPreviewSize(
                    PREVIEW_WIDTH,
                    PREVIEW_HEIGHT,
                    55,
                    61,
                    UVCCamera.FRAME_FORMAT_MJPEG,
                    UVCCamera.DEFAULT_BANDWIDTH
                )

                camera.setPreviewTexture(
                    surfaceTexture
                )

                camera.startPreview()

                currentCamera = camera
                activeDeviceId = device.deviceId
                openingDeviceId = null
                permissionRequestedDeviceId = null

                status = "CAMERA 720P"

            } catch (e: Exception) {

                runCatching {
                    camera?.destroy()
                }

                currentCamera = null
                activeDeviceId = null
                openingDeviceId = null

                status =
                    "CAMERA ERROR: " +
                            (e.localizedMessage ?: "UNKNOWN")
            }
        }

        fun requestCamera(
            device: UsbDevice
        ) {
            if (!cameraView.isAvailable) {
                return
            }

            if (!isUvcCamera(device)) {
                return
            }

            if (activeDeviceId == device.deviceId) {
                return
            }

            if (
                permissionRequestedDeviceId ==
                device.deviceId
            ) {
                return
            }

            permissionRequestedDeviceId =
                device.deviceId

            status = "USB CAMERA FOUND"

            try {
                usbMonitor.requestPermission(
                    device
                )
            } catch (e: Exception) {
                permissionRequestedDeviceId = null

                status =
                    "USB ERROR: " +
                            (e.localizedMessage ?: "UNKNOWN")
            }
        }

        fun requestConnectedCamera() {

            val device =
                try {
                    usbMonitor
                        .deviceList
                        .firstOrNull {
                            isUvcCamera(it)
                        }
                } catch (_: Exception) {
                    null
                }

            if (device == null) {
                status =
                    "WAITING FOR USB CAMERA"

                return
            }

            requestCamera(device)
        }

        val deviceListener =
            object :
                USBMonitor.OnDeviceConnectListener {

                override fun onAttach(
                    device: UsbDevice?
                ) {
                    device ?: return

                    if (!isUvcCamera(device)) {
                        return
                    }

                    cameraView.post {
                        requestCamera(device)
                    }
                }

                override fun onConnect(
                    device: UsbDevice?,
                    ctrlBlock:
                    USBMonitor.UsbControlBlock?,
                    createNew: Boolean
                ) {
                    if (
                        device == null ||
                        ctrlBlock == null
                    ) {
                        return
                    }

                    if (!isUvcCamera(device)) {
                        return
                    }

                    cameraView.post {
                        openCamera(
                            device,
                            ctrlBlock
                        )
                    }
                }

                override fun onDisconnect(
                    device: UsbDevice?,
                    ctrlBlock:
                    USBMonitor.UsbControlBlock?
                ) {
                    device ?: return

                    if (!isUvcCamera(device)) {
                        return
                    }

                    cameraView.post {

                        if (
                            activeDeviceId ==
                            device.deviceId
                        ) {
                            stopCamera()
                        }

                        permissionRequestedDeviceId =
                            null

                        status =
                            "CAMERA DISCONNECTED"
                    }
                }

                override fun onDetach(
                    device: UsbDevice?
                ) {
                    device ?: return

                    if (!isUvcCamera(device)) {
                        return
                    }

                    cameraView.post {

                        if (
                            activeDeviceId ==
                            device.deviceId
                        ) {
                            stopCamera()
                        }

                        permissionRequestedDeviceId =
                            null

                        status =
                            "WAITING FOR USB CAMERA"
                    }
                }

                override fun onCancel(
                    device: UsbDevice?
                ) {
                    cameraView.post {
                        permissionRequestedDeviceId =
                            null

                        status =
                            "USB PERMISSION DENIED"
                    }
                }
            }

        usbMonitor.setOnDeviceConnectListener(
            deviceListener
        )

        fun startUsbMonitor() {
            try {
                if (!usbMonitor.isRegistered) {
                    usbMonitor.register()
                }

                requestConnectedCamera()

            } catch (e: Exception) {
                status =
                    "USB ERROR: " +
                            (e.localizedMessage ?: "UNKNOWN")
            }
        }

        val surfaceListener =
            object :
                TextureView.SurfaceTextureListener {

                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    startUsbMonitor()
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) = Unit

                override fun onSurfaceTextureDestroyed(
                    surface: SurfaceTexture
                ): Boolean {

                    stopCamera()

                    permissionRequestedDeviceId =
                        null

                    return true
                }

                override fun onSurfaceTextureUpdated(
                    surface: SurfaceTexture
                ) = Unit
            }

        cameraView.surfaceTextureListener =
            surfaceListener

        /*
         * TextureView may already have its SurfaceTexture
         * before DisposableEffect starts.
         */
        if (cameraView.isAvailable) {
            startUsbMonitor()
        }

        onDispose {

            cameraView.surfaceTextureListener =
                null

            stopCamera()

            runCatching {
                if (usbMonitor.isRegistered) {
                    usbMonitor.unregister()
                }
            }

            runCatching {
                usbMonitor.destroy()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        AndroidView(
            factory = {
                cameraView
            },
            modifier = Modifier
                .fillMaxSize()
                .align(
                    Alignment.Center
                )
        )

        Text(
            text = status,
            color = Color(
                0xFFD7E800
            ),
            fontFamily =
                FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier
                .align(
                    Alignment.TopCenter
                )
                .padding(
                    top = 8.dp
                )
        )
    }
}

private fun isUvcCamera(
    device: UsbDevice
): Boolean {

    if (
        device.deviceClass ==
        UsbConstants.USB_CLASS_VIDEO
    ) {
        return true
    }

    for (
    index in
    0 until device.interfaceCount
    ) {
        val usbInterface =
            device.getInterface(index)

        if (
            usbInterface.interfaceClass ==
            UsbConstants.USB_CLASS_VIDEO
        ) {
            return true
        }
    }

    return false
}