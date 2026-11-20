package com.sushant.grabbit

import com.sushant.grabbit.engine.AudioOpt
import com.sushant.grabbit.engine.AudioOptions
import com.sushant.grabbit.engine.audioKbps
import com.sushant.grabbit.engine.isAudioQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioOptionsTest {
    @Test
    fun groupsBitratesBestFirst(): Unit {
        val opts = AudioOptions.build(listOf(129.5 to 3_000_000L, 160.2 to 3_900_000L, 49.8 to 1_100_000L, 127.0 to 3_100_000L, 0.0 to 5L))
        assertEquals(listOf(AudioOpt(160, 3_900_000L), AudioOpt(128, 3_100_000L), AudioOpt(48, 1_100_000L)), opts)
    }

    @Test
    fun audioQualityKeys(): Unit {
        assertTrue(isAudioQuality("audio"))
        assertTrue(isAudioQuality("a128"))
        assertFalse(isAudioQuality("best"))
        assertFalse(isAudioQuality("h720"))
        assertEquals(128, audioKbps("a128"))
    }
}
