package com.example.llama

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NanuToolRegistryTest {
    @Test fun calculatorUsesBoundedDeterministicMath() {
        assertEquals(14.0,NanuToolRegistry.calculate("2 + 3 * 4"),0.000001)
        assertEquals(512.0,NanuToolRegistry.calculate("2 ^ 3 ^ 2"),0.000001)
        assertEquals(-2.5,NanuToolRegistry.calculate("-(8-3)/2"),0.000001)
        try { NanuToolRegistry.calculate("1/0"); fail("Division by zero must fail") } catch(_:IllegalArgumentException) { }
        try { NanuToolRegistry.calculate("Runtime.exec('x')"); fail("Code must never execute") } catch(_:IllegalArgumentException) { }
    }

    @Test fun parserAcceptsOnlyOneAllowListedStructuredCall() {
        val call=NanuToolRegistry.parseCall("<tool_call>{\"name\":\"calculator\",\"arguments\":{\"expression\":\"5*5\"}}</tool_call>")
        assertEquals("calculator",call?.name)
        assertEquals("5*5",call?.arguments?.getString("expression"))
        assertNull(NanuToolRegistry.parseCall("A normal final answer."))
        try {
            NanuToolRegistry.parseCall("Do this <tool_call>{\"name\":\"calculator\",\"arguments\":{}}</tool_call>")
            fail("Mixed prose and tool calls must fail")
        } catch(_:IllegalArgumentException) { }
        try {
            NanuToolRegistry.parseCall("<tool_call>{\"name\":\"send_transaction\",\"arguments\":{}}</tool_call>")
            fail("Unlisted tools must fail")
        } catch(_:IllegalArgumentException) { }
    }

    @Test fun positionSizingEnforcesRiskCap() {
        val result=NanuToolRegistry.positionSize(1000.0,1.0,10.0,9.5)
        assertTrue(result.contains("Risk amount: 10 USD"))
        assertTrue(result.contains("Maximum quantity before fees/slippage: 20"))
        try { NanuToolRegistry.positionSize(1000.0,6.0,10.0,9.5); fail("Risk above cap must fail") }
        catch(_:IllegalArgumentException) { }
    }

    @Test fun obviousCurrentQuestionsRouteToReadOnlyTools() {
        val weather=NanuToolRegistry.suggestedCall("User request:\nWhat is the current weather in Kanpur?",true)
        assertEquals("current_weather",weather?.name)
        assertEquals("Kanpur",weather?.arguments?.getString("location"))

        val btc=NanuToolRegistry.suggestedCall("User request:\nWhat is the current price of BTC?",true)
        assertEquals("crypto_price",btc?.name)
        assertEquals("BTC",btc?.arguments?.getString("symbol"))

        val typo=NanuToolRegistry.suggestedCall("User request:\nWhat is the current prize of bitcoin?",true)
        assertEquals("crypto_price",typo?.name)

        val image=NanuToolRegistry.suggestedCall("User request:\nShow me an image of the moon",true)
        assertEquals("image_search",image?.name)
        assertEquals("moon",image?.arguments?.getString("query"))
    }

    @Test fun onlineSwitchBlocksAutomaticLiveRoutingButKeepsOfflineTarot() {
        assertNull(NanuToolRegistry.suggestedCall("User request:\nWeather in Kanpur",false))
        val tarot=NanuToolRegistry.suggestedCall("User request:\nGive me a three-card tarot reading",false)
        assertEquals("tarot_draw",tarot?.name)
        assertEquals(3,tarot?.arguments?.getInt("count"))
    }
}
