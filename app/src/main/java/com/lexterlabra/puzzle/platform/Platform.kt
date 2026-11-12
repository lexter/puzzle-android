package com.lexterlabra.puzzle.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

sealed interface PickResult {
    class Picked(val bytes: ByteArray) : PickResult
    data object Cancelled : PickResult
    data object PermissionDenied : PickResult
}

interface ImagePicker {
    val cameraAvailable: Boolean
    fun pickFromGallery()
    fun takePhoto()
}

/** Gallery (photo picker) and camera capture, with the camera permission handled inline. */
@Composable
fun rememberImagePicker(onResult: (PickResult) -> Unit): ImagePicker {
    val context = LocalContext.current
    val deliver by rememberUpdatedState(onResult)
    val pendingPhoto = remember { mutableStateOf<Uri?>(null) }

    fun read(uri: Uri): PickResult =
        context.contentResolver.openInputStream(uri)?.use { PickResult.Picked(it.readBytes()) } ?: PickResult.Cancelled

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        deliver(uri?.let(::read) ?: PickResult.Cancelled)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingPhoto.value
        deliver(if (saved && uri != null) read(uri) else PickResult.Cancelled)
        // Don't keep the photo around once its bytes are in memory.
        captureFile(context).delete()
    }
    val launchCamera = {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", captureFile(context))
        pendingPhoto.value = uri
        camera.launch(uri)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else deliver(PickResult.PermissionDenied)
    }

    return remember(context) {
        object : ImagePicker {
            override val cameraAvailable = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

            override fun pickFromGallery() =
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

            override fun takePhoto() {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (granted) launchCamera() else permission.launch(Manifest.permission.CAMERA)
            }
        }
    }
}

/**
 * Player name used until the player sets one. It's advertised over mDNS, so it must not identify
 * the owner: the device model (e.g. "Pixel 8"), never `Settings.Global.DEVICE_NAME`, which is the
 * user-assigned name (e.g. "Jane's Pixel").
 */
object DefaultPlayerName {
    fun get(): String = Build.MODEL
}

/** Where the camera writes a new photo; deleted as soon as it has been read. */
private fun captureFile(context: Context): File = File(context.cacheDir, "photos").apply { mkdirs() }.resolve("capture.jpg")

/** Sent in the P2P handshake. */
const val PLATFORM_NAME = "android"

/** Opens this app's page in the system Settings (for denied permissions). */
fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

class Haptics(private val feedback: HapticFeedback) {
    /** Light impact for tapping or sliding a tile. */
    fun tap() = feedback.performHapticFeedback(HapticFeedbackType.VirtualKey)

    /** Softer tick for rapid, automatic tile moves such as the start-of-game shuffle. */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    fun success() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}
