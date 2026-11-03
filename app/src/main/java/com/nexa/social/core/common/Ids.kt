package com.nexa.social.core.common

import java.util.UUID

/** Central id generation so it can be swapped for server ids later. */
object Ids {
    fun newId(): String = UUID.randomUUID().toString()
}
