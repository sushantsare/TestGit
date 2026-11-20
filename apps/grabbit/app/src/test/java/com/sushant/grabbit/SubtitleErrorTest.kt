package com.sushant.grabbit

import com.sushant.grabbit.engine.Errors
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleErrorTest {
    @Test fun detectsSubtitleFailures() {
        assertTrue(Errors.isSubtitleError("ERROR: Unable to download video subtitles for 'en': HTTP Error 429: Too Many Requests"))
        assertTrue(Errors.isSubtitleError("ERROR: Unable to download subtitles for 'hi-en': HTTP Error 403"))
        assertTrue(Errors.isSubtitleError("ERROR: Postprocessing: [EmbedSubtitle] Conversion failed"))
    }

    @Test fun ignoresOtherErrors() {
        assertFalse(Errors.isSubtitleError("ERROR: [youtube] abc: HTTP Error 429: Too Many Requests"))
        assertFalse(Errors.isSubtitleError("ERROR: Requested format is not available"))
        assertFalse(Errors.isSubtitleError(null))
    }

    @Test fun subtitleMessageDoesNotSuggestLogin() {
        val m = Errors.friendly("ERROR: Unable to download video subtitles for 'en': HTTP Error 429: Too Many Requests")
        assertTrue(m.contains("subtitles"))
        assertFalse(m.contains("log in"))
    }
}
