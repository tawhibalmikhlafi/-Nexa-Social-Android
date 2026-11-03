package com.nexa.social.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import android.net.Uri

/**
 * Returns a lambda that opens the system photo picker and reports the chosen image uri.
 * Uses the platform picker, so no storage permission is required.
 */
@Composable
fun rememberImagePicker(onPicked: (Uri?) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> onPicked(uri) }

    return {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
}
