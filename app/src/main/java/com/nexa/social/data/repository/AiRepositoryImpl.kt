package com.nexa.social.data.repository

import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.Resource
import com.nexa.social.domain.model.AiRequest
import com.nexa.social.domain.model.AiResult
import com.nexa.social.domain.model.AiTask
import com.nexa.social.domain.repository.AiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * No AI provider is configured in this build. [isConfigured] is therefore `false`.
 *
 * A few tasks are answered by deterministic on-device text utilities and are explicitly labelled
 * [AiResult.isLocalTool] = true. Anything that would require a real model returns
 * [FailureReason.NOT_CONFIGURED] instead of inventing output.
 */
class AiRepositoryImpl : AiRepository {

    override val isConfigured: Boolean = false

    override suspend fun run(request: AiRequest): Resource<AiResult> = withContext(Dispatchers.Default) {
        val input = request.input.trim()
        if (input.isBlank()) return@withContext Resource.Failure(FailureReason.REQUIRED)
        when (request.task) {
            AiTask.PROOFREAD -> Resource.Success(
                AiResult(
                    text = proofread(input),
                    providerLabel = LOCAL_LABEL,
                    isLocalTool = true
                )
            )
            AiTask.SUGGEST_TAGS -> {
                val tags = suggestTags(input)
                if (tags.isEmpty()) Resource.Failure(FailureReason.NOT_CONFIGURED)
                else Resource.Success(AiResult(tags.joinToString(" ") { "#$it" }, LOCAL_LABEL, true))
            }
            AiTask.SUMMARIZE -> {
                val summary = firstSentence(input)
                if (summary.isBlank()) Resource.Failure(FailureReason.NOT_CONFIGURED)
                else Resource.Success(AiResult(summary, LOCAL_LABEL, true))
            }
            AiTask.IMPROVE_WRITING,
            AiTask.SUGGEST_TITLE,
            AiTask.TRANSLATE,
            AiTask.REWRITE_STYLE -> Resource.Failure(FailureReason.NOT_CONFIGURED)
        }
    }

    private fun proofread(text: String): String = text
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex(" +([.,!?،؛:])"), "$1")
        .replace(Regex("([.,!?،؛:])(?=[^\\s\\d])"), "$1 ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private fun suggestTags(text: String): List<String> {
        val stopWords = setOf(
            "the", "and", "for", "with", "this", "that", "from", "are", "was", "you",
            "في", "من", "على", "إلى", "عن", "هذا", "هذه", "التي", "الذي", "مع", "أن"
        )
        return text.lowercase()
            .split(Regex("[^\\p{L}\\p{N}#]+"))
            .map { it.trimStart('#') }
            .filter { it.length in 3..20 && it !in stopWords }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
    }

    private fun firstSentence(text: String): String =
        text.split(Regex("(?<=[.!?؟])\\s+|\\n"))
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(160)
            .orEmpty()

    private companion object {
        const val LOCAL_LABEL = "On-device text tool"
    }
}
