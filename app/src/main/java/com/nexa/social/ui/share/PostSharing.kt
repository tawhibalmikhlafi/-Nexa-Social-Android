package com.nexa.social.ui.share

import android.content.Context
import android.content.Intent
import com.nexa.social.R
import com.nexa.social.core.navigation.DeepLinkRouter

/**
 * Opens the Android Sharesheet with a real deep link. The app cannot know whether the user
 * actually completed a share, so this function deliberately does not increment `shareCount`.
 */
fun sharePost(context: Context, postId: String, text: String): Boolean {
    val link = DeepLinkRouter.postLink(postId)
    val body = buildString {
        val excerpt = text.trim()
        if (excerpt.isNotEmpty()) {
            append(excerpt.take(280))
            append("\n\n")
        }
        append(context.getString(R.string.post_share_text, link))
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, body)
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.post_share))
    }
    return runCatching {
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.post_share_chooser))
        )
        true
    }.getOrDefault(false)
}
