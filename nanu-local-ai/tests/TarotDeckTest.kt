package com.example.llama

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class TarotDeckTest {
    @Test fun deckContainsEveryUniqueMajorAndMinorCard() {
        assertEquals(78, TarotDeck.cards.size)
        assertEquals(78, TarotDeck.cards.map { it.id }.distinct().size)
        assertEquals(22, TarotDeck.cards.count { it.arcana.startsWith("Major") })
        assertEquals(56, TarotDeck.cards.count { it.arcana.startsWith("Minor") })
        assertTrue(TarotDeck.cards.all { it.upright.isNotBlank() && it.reversed.isNotBlank() })
    }

    @Test fun oneAndThreeCardDrawsDoNotRepeatCards() {
        assertEquals(1, TarotDeck.draw(1, random = Random(7)).size)
        val spread = TarotDeck.draw(3, random = Random(8))
        assertEquals(listOf("Past", "Present", "Future"), spread.map { it.position })
        assertEquals(3, spread.map { it.card.id }.distinct().size)
    }

    @Test fun readingKeepsReflectionSeparateFromFactualAdvice() {
        val reading = TarotDeck.reading("What should I reflect on?", 1)
        assertTrue(reading.contains("Question: What should I reflect on?"))
        assertTrue(reading.contains("not as factual prediction"))
    }
}
