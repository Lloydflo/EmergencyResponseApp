package com.ers.emergencyresponseapp.coordination.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ReplyMessageCodecTest {

    @Test
    fun encodeAndDecode_preservesBodyAndSanitizesQuoteMetadata() {
        val encoded = ReplyMessageCodec.encode(
            body = "Proceed to staging.\nBring trauma supplies.",
            sender = "  Alex\n\tRivera  ",
            preview = "Need   medical\r\nbackup at Gate 2"
        )

        val decoded = requireNotNull(ReplyMessageCodec.decode(encoded))

        assertEquals("Proceed to staging.\nBring trauma supplies.", decoded.body)
        assertEquals("Alex Rivera", decoded.sender)
        assertEquals("Need medical backup at Gate 2", decoded.preview)
        assertEquals(decoded.body, ReplyMessageCodec.displayBody(encoded))
    }

    @Test
    fun encode_placesReadableBodyBeforeReplyQuote() {
        val encoded = ReplyMessageCodec.encode(
            body = "Copy, moving now.",
            sender = "Dispatch",
            preview = "Proceed to Sector B"
        )

        assertEquals(
            "Copy, moving now.\n\n↪ Reply · ERS1\nFrom: Dispatch\n> Proceed to Sector B",
            encoded
        )
    }

    @Test
    fun encode_capsAllFieldsAtTheirDocumentedLimits() {
        val decoded = requireNotNull(
            ReplyMessageCodec.decode(
                ReplyMessageCodec.encode(
                    body = "b".repeat(ReplyMessageCodec.MAX_BODY_LENGTH + 50),
                    sender = "s".repeat(ReplyMessageCodec.MAX_SENDER_LENGTH + 50),
                    preview = "p".repeat(ReplyMessageCodec.MAX_PREVIEW_LENGTH + 50)
                )
            )
        )

        assertEquals(ReplyMessageCodec.MAX_BODY_LENGTH, decoded.body.length)
        assertEquals(ReplyMessageCodec.MAX_SENDER_LENGTH, decoded.sender.length)
        assertEquals(ReplyMessageCodec.MAX_PREVIEW_LENGTH, decoded.preview.length)
    }

    @Test
    fun decode_acceptsCrLfTransportLineEndings() {
        val encoded = ReplyMessageCodec.encode(
            body = "Received",
            sender = "Responder 12",
            preview = "Hold position"
        ).replace("\n", "\r\n")

        val decoded = requireNotNull(ReplyMessageCodec.decode(encoded))

        assertEquals("Received", decoded.body)
        assertEquals("Responder 12", decoded.sender)
        assertEquals("Hold position", decoded.preview)
    }

    @Test
    fun legacyText_isNotDecodedAndDisplayBodyReturnsItUnchanged() {
        val legacy = "Legacy message\nwith two lines"

        assertNull(ReplyMessageCodec.decode(legacy))
        assertEquals(legacy, ReplyMessageCodec.displayBody(legacy))
        assertEquals("", ReplyMessageCodec.displayBody(null))
    }

    @Test
    fun malformedEnvelopes_areRejected() {
        val malformed = listOf(
            "Body\n\n↪ Reply · ERS1\nFrom: Dispatch",
            "Body\n\n↪ Reply · ERS1\nDispatch\n> Preview",
            "Body\n\n↪ Reply · ERS1\nFrom: \n> Preview",
            "Body\n\n↪ Reply · ERS1\nFrom: Dispatch\n> ",
            "Body\n\n↪ Reply · ERS1\nFrom: Dispatch\n> Preview\nUnexpected"
        )

        malformed.forEach { assertNull(ReplyMessageCodec.decode(it)) }
    }

    @Test
    fun decode_rejectsOversizedUntrustedEnvelope() {
        val oversized = "b".repeat(ReplyMessageCodec.MAX_BODY_LENGTH + 1) +
                "\n\n↪ Reply · ERS1\nFrom: Dispatch\n> Preview"

        assertNull(ReplyMessageCodec.decode(oversized))
    }

    @Test
    fun encode_rejectsBlankRequiredFields() {
        assertThrows(IllegalArgumentException::class.java) {
            ReplyMessageCodec.encode("", "Dispatch", "Preview")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReplyMessageCodec.encode("Body", "\n", "Preview")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReplyMessageCodec.encode("Body", "Dispatch", "\r\n")
        }
    }
}
