package com.ers.emergencyresponseapp.coordination.model

/**
 * Encodes reply context inside the existing chat `text` field.
 *
 * The reply body deliberately comes first so clients that do not know about
 * this codec still show the responder's message before a human-readable quote.
 * The versioned marker also keeps ordinary legacy messages from being mistaken
 * for encoded replies.
 */
object ReplyMessageCodec {
    const val MAX_BODY_LENGTH = 2_000
    const val MAX_SENDER_LENGTH = 80
    const val MAX_PREVIEW_LENGTH = 160

    private const val QUOTE_MARKER = "\n\n↪ Reply · ERS1\n"
    private const val SENDER_PREFIX = "From: "
    private const val PREVIEW_PREFIX = "> "

    data class Decoded(
        val body: String,
        val sender: String,
        val preview: String
    )

    /**
     * Creates a backward-compatible wire value for an existing text field.
     * Oversized values are capped before encoding. Sender and preview metadata
     * are kept on one line so they cannot inject extra envelope fields.
     */
    fun encode(
        body: String,
        sender: String,
        preview: String
    ): String {
        val cleanBody = body.trim().take(MAX_BODY_LENGTH)
        val cleanSender = sanitizeInline(sender).take(MAX_SENDER_LENGTH).trim()
        val cleanPreview = sanitizeInline(preview).take(MAX_PREVIEW_LENGTH).trim()

        require(cleanBody.isNotBlank()) { "Reply body cannot be blank" }
        require(cleanSender.isNotBlank()) { "Reply sender cannot be blank" }
        require(cleanPreview.isNotBlank()) { "Reply preview cannot be blank" }

        return buildString(
            cleanBody.length + cleanSender.length + cleanPreview.length + 32
        ) {
            append(cleanBody)
            append(QUOTE_MARKER)
            append(SENDER_PREFIX)
            append(cleanSender)
            append('\n')
            append(PREVIEW_PREFIX)
            append(cleanPreview)
        }
    }

    /**
     * Returns reply metadata only for a complete, valid ERS1 envelope.
     * Plain legacy messages and malformed or oversized envelopes return null.
     */
    fun decode(text: String?): Decoded? {
        if (text.isNullOrBlank()) return null

        val normalized = normalizeLineEndings(text)
        val markerIndex = normalized.lastIndexOf(QUOTE_MARKER)
        if (markerIndex <= 0) return null

        val body = normalized.substring(0, markerIndex).trim()
        if (body.isBlank() || body.length > MAX_BODY_LENGTH) return null

        val quoteLines = normalized
            .substring(markerIndex + QUOTE_MARKER.length)
            .split('\n')
        if (quoteLines.size != 2) return null

        val senderLine = quoteLines[0]
        val previewLine = quoteLines[1]
        if (!senderLine.startsWith(SENDER_PREFIX) ||
            !previewLine.startsWith(PREVIEW_PREFIX)
        ) {
            return null
        }

        val sender = senderLine.removePrefix(SENDER_PREFIX)
        val preview = previewLine.removePrefix(PREVIEW_PREFIX)
        if (sender.isBlank() || preview.isBlank() ||
            sender.length > MAX_SENDER_LENGTH ||
            preview.length > MAX_PREVIEW_LENGTH ||
            sender != sanitizeInline(sender) ||
            preview != sanitizeInline(preview)
        ) {
            return null
        }

        return Decoded(
            body = body,
            sender = sender,
            preview = preview
        )
    }

    /** Returns the responder-authored body, or the original legacy text. */
    fun displayBody(text: String?): String = decode(text)?.body ?: text.orEmpty()

    private fun normalizeLineEndings(value: String): String = value
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    private fun sanitizeInline(value: String): String = value
        .replace(Regex("[\\r\\n\\t\\u2028\\u2029]+"), " ")
        .filterNot { character ->
            character.code < 0x20 || character.code == 0x7F
        }
        .replace(Regex(" {2,}"), " ")
        .trim()
}
