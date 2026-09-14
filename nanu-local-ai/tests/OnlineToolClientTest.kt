package com.example.llama

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OnlineToolClientTest {
    @Test fun newsParserAcceptsOnlyHttpsItems() {
        val xml = """<rss><channel>
            <item><title>Useful headline</title><link>https://example.com/story</link><source>Example</source><pubDate>Mon, 14 Sep 2026 00:00:00 GMT</pubDate></item>
            <item><title>Unsafe link</title><link>http://example.com/plain</link></item>
        </channel></rss>"""
        val items = OnlineToolClient.parseNews(xml)
        assertEquals(1, items.size)
        assertEquals("Useful headline", items.single().title)
        assertEquals("Example", items.single().source)
    }

    @Test fun onlineQueriesAreNormalizedAndBounded() {
        assertEquals("weather in Kanpur", OnlineToolClient.boundedQuery("  weather\n in\tKanpur  "))
        try { OnlineToolClient.boundedQuery(""); fail("Blank query must fail") } catch (_: IllegalArgumentException) { }
        try { OnlineToolClient.boundedQuery("x".repeat(301)); fail("Oversized query must fail") } catch (_: IllegalArgumentException) { }
    }
}
