package com.example.llama

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NanuFinalFeatureTest {
    @Test fun ocrChoosesReadableLatinOutput() {
        val result = OnDeviceOcr.chooseBest("Invoice total 4200", "")
        assertEquals("Invoice total 4200", result.text)
        assertEquals("on-device text OCR", result.method)
    }

    @Test fun ocrChoosesDevanagariWhenItContainsStrongerText() {
        val result = OnDeviceOcr.chooseBest("tiny", "नमस्ते दुनिया यह एक परीक्षण है")
        assertTrue(result.text.startsWith("नमस्ते"))
        assertEquals("on-device Devanagari OCR", result.method)
    }

    @Test fun insightParserUsesOnlyExactToolMetrics() {
        val weather = NanuInsightParser.parse("Humidity: 67%\nSource: Open-Meteo")
        assertEquals(1, weather.size)
        assertEquals("67%", weather.single().displayValue)
        assertEquals(0.67f, weather.single().fraction, 0.001f)

        val market = NanuInsightParser.parse("24-hour change: -2.50%\nSource: CoinGecko")
        assertEquals("-2.50%", market.single().displayValue)
        assertTrue(market.single().signed)

        assertTrue(NanuInsightParser.parse("The outlook seems positive.").isEmpty())
    }
}
