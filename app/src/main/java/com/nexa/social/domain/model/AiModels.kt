package com.nexa.social.domain.model

/**
 * Requests handled by the Nexa AI layer.
 *
 * There is no AI service configured in this build. This module only defines the contract and an
 * on-device text utilities fallback; nothing here is a real language-model response.
 */
enum class AiTask {
    IMPROVE_WRITING,
    SUGGEST_TITLE,
    PROOFREAD,
    SUMMARIZE,
    TRANSLATE,
    SUGGEST_TAGS,
    REWRITE_STYLE
}

data class AiRequest(
    val task: AiTask,
    val input: String,
    val targetLanguage: String? = null,
    val style: String? = null
)

/**
 * @param isLocalTool true when the text came from on-device deterministic utilities rather than a
 * remote model. The UI must label this clearly so no result is mistaken for a model answer.
 */
data class AiResult(
    val text: String,
    val providerLabel: String,
    val isLocalTool: Boolean
)
