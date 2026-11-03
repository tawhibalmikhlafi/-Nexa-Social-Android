package com.nexa.social.core.navigation

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-app destinations reachable from a shared Nexa link. */
data class DeepLinkTarget(
    val kind: Kind,
    val id: String
) {
    enum class Kind { POST, USER }
}

/**
 * Parses `nexasocial://post/{id}` and `nexasocial://user/{id}` links. Only these two hosts are
 * accepted; auth callbacks are handled separately by [com.nexa.social.core.auth.AuthLinkParser].
 */
object DeepLinkRouter {

    private val _target = MutableStateFlow<DeepLinkTarget?>(null)
    val target: StateFlow<DeepLinkTarget?> = _target.asStateFlow()

    fun handle(uri: Uri?): Boolean {
        if (uri == null || uri.scheme != "nexasocial") return false
        val kind = when (uri.host?.lowercase()) {
            "post" -> DeepLinkTarget.Kind.POST
            "user" -> DeepLinkTarget.Kind.USER
            else -> return false
        }
        val id = uri.pathSegments.firstOrNull { it.isNotBlank() } ?: return false
        _target.value = DeepLinkTarget(kind, id)
        return true
    }

    fun consume() {
        _target.value = null
    }

    fun postLink(postId: String): String = "nexasocial://post/$postId"

    fun userLink(userId: String): String = "nexasocial://user/$userId"
}
