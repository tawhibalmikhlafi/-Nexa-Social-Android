package com.nexa.social.core.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Simple file based JSON persistence for structured local data.
 *
 * Each domain (posts, communities, messages, notifications, local accounts) uses its own file,
 * so no single monolithic blob is produced and any store can be replaced by a network source later.
 * All calls are expected to run on a background dispatcher.
 */
class JsonFileStore(context: Context) {

    private val directory: File = File(context.filesDir, "nexa_data").apply { mkdirs() }
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    private fun file(name: String): File = File(directory, name)

    inline fun <reified T> readList(fileName: String): List<T> {
        val type = TypeToken.getParameterized(List::class.java, T::class.java).type
        return read(fileName, type) ?: emptyList()
    }

    inline fun <reified T> writeList(fileName: String, value: List<T>) {
        val type = TypeToken.getParameterized(List::class.java, T::class.java).type
        write(fileName, value, type)
    }

    fun <T> read(fileName: String, type: java.lang.reflect.Type): T? {
        val target = file(fileName)
        if (!target.exists()) return null
        return runCatching { gson.fromJson<T>(target.readText(), type) }.getOrNull()
    }

    fun write(fileName: String, value: Any, type: java.lang.reflect.Type) {
        val json = gson.toJson(value, type)
        val target = file(fileName)
        val temp = File(directory, "$fileName.tmp")
        temp.writeText(json)
        if (target.exists()) target.delete()
        temp.renameTo(target)
    }

    fun delete(fileName: String) {
        val target = file(fileName)
        if (target.exists()) target.delete()
    }

    fun exists(fileName: String): Boolean = file(fileName).exists()
}
