package com.example.llama

import java.util.Locale
import kotlin.random.Random

data class TarotCard(
    val id: String,
    val name: String,
    val arcana: String,
    val symbol: String,
    val keywords: String,
    val upright: String,
    val reversed: String,
    val love: String,
    val career: String,
    val money: String
)

data class TarotDraw(val card: TarotCard, val reversed: Boolean, val position: String)

/** Offline tarot reference and random draw engine. It never makes factual predictions. */
object TarotDeck {
    private data class Major(
        val name: String,
        val symbol: String,
        val keywords: String,
        val upright: String,
        val reversed: String
    )

    private val majors = listOf(
        Major("The Fool", "✦", "beginnings, freedom, trust", "A fresh beginning, openness and a willingness to learn through experience.", "Poor preparation, unnecessary risk or fear of taking the first step."),
        Major("The Magician", "∞", "skill, action, resourcefulness", "Use the skills and resources already available to turn intention into action.", "Scattered effort, manipulation or ability that is not being used honestly."),
        Major("The High Priestess", "☾", "intuition, mystery, inner voice", "Pause and listen to intuition, subtle information and what has not yet been said.", "Ignoring intuition, secrecy or confusion caused by incomplete information."),
        Major("The Empress", "♀", "nurture, creativity, abundance", "Growth comes through patience, care, creativity and a supportive environment.", "Creative blockage, overprotection or neglecting your own needs."),
        Major("The Emperor", "♜", "structure, authority, stability", "Clear boundaries, planning and responsible leadership can create stability.", "Rigidity, excessive control or weak boundaries."),
        Major("The Hierophant", "⚜", "tradition, teaching, shared values", "Learn from proven guidance, community or a trusted system of values.", "Questioning convention, unhelpful conformity or choosing a personal path."),
        Major("The Lovers", "♡", "choice, connection, alignment", "Choose in line with your values and build relationships through honest agreement.", "Misalignment, avoidance of a choice or imbalance in a relationship."),
        Major("The Chariot", "➶", "direction, willpower, progress", "Focused effort and emotional control can move the situation forward.", "Loss of direction, aggression or competing goals pulling apart."),
        Major("Strength", "♌", "courage, patience, compassion", "Quiet courage and self-control are stronger than force.", "Self-doubt, depleted confidence or emotion becoming difficult to manage."),
        Major("The Hermit", "⌁", "reflection, solitude, wisdom", "Step back from noise and seek an answer through careful reflection.", "Isolation, avoidance or refusing useful guidance."),
        Major("Wheel of Fortune", "◉", "change, cycles, turning point", "Conditions are changing; adapt thoughtfully to the new cycle.", "Resistance to change, a repeated pattern or a temporary setback."),
        Major("Justice", "⚖", "truth, balance, accountability", "Look at evidence, consequences and fairness before deciding.", "Bias, avoidance of responsibility or an unfair situation needing correction."),
        Major("The Hanged Man", "◇", "pause, surrender, perspective", "A deliberate pause and a different viewpoint may reveal the way forward.", "Stalling without learning, resistance or needless sacrifice."),
        Major("Death", "✥", "ending, transition, renewal", "A necessary ending creates room for meaningful transformation; it does not predict physical death.", "Clinging to the past, delayed transition or fear of necessary change."),
        Major("Temperance", "△", "moderation, harmony, integration", "Combine patience and balance; steady adjustment is better than an extreme response.", "Excess, impatience or parts of life working against one another."),
        Major("The Devil", "⛓", "attachment, temptation, shadow", "Notice habits, fears or attachments that reduce your freedom to choose.", "Recognizing an unhealthy pattern and beginning to release it."),
        Major("The Tower", "ϟ", "disruption, revelation, reset", "An unstable structure may change suddenly, revealing what must be rebuilt.", "Avoiding a necessary truth or reducing the impact of an approaching change."),
        Major("The Star", "★", "hope, healing, renewal", "Renew hope, recover gradually and act from a clearer sense of purpose.", "Discouragement, disconnection or difficulty trusting the recovery process."),
        Major("The Moon", "☽", "uncertainty, dreams, illusion", "Move carefully while facts are unclear; feelings contain clues but are not proof.", "Confusion beginning to clear, or fear distorting what you see."),
        Major("The Sun", "☀", "clarity, vitality, joy", "Clarity, confidence and open communication support a positive direction.", "Temporary pessimism, delayed success or pressure to appear happier than you feel."),
        Major("Judgement", "♬", "review, awakening, decision", "Review the past honestly, learn from it and answer a call to change.", "Harsh self-judgement, denial of a lesson or indecision."),
        Major("The World", "⊕", "completion, integration, achievement", "A cycle reaches completion and its lessons can now be integrated.", "An unfinished detail, delayed closure or difficulty recognizing progress.")
    )

    private data class Suit(
        val name: String,
        val symbol: String,
        val domain: String,
        val love: String,
        val career: String,
        val money: String
    )

    private val suits = listOf(
        Suit("Wands", "♢", "energy, ambition and creative action", "passion and initiative", "motivation, leadership and new projects", "enterprise and purposeful spending"),
        Suit("Cups", "◡", "emotion, relationships and intuition", "emotional honesty and connection", "fulfilment, teamwork and people skills", "values and emotional spending choices"),
        Suit("Swords", "†", "thought, truth and difficult decisions", "communication, boundaries and conflict", "strategy, analysis and workplace tension", "clear decisions, contracts and financial stress"),
        Suit("Pentacles", "⬟", "work, health and material stability", "reliability and practical commitment", "skills, steady work and tangible results", "saving, income and long-term security")
    )

    private data class Rank(val name: String, val theme: String, val shadow: String)
    private val ranks = listOf(
        Rank("Ace", "a new opportunity or seed", "a delayed or overlooked beginning"),
        Rank("Two", "balance, choice and partnership", "indecision or imbalance"),
        Rank("Three", "early growth, cooperation and expression", "poor coordination or stalled development"),
        Rank("Four", "stability, consolidation and boundaries", "stagnation or an unstable foundation"),
        Rank("Five", "challenge, friction and adjustment", "unresolved tension or avoiding a necessary challenge"),
        Rank("Six", "movement, support and gradual improvement", "delayed progress or unequal support"),
        Rank("Seven", "assessment, persistence and a test of conviction", "confusion, exhaustion or weak commitment"),
        Rank("Eight", "focused movement, practice and momentum", "restriction, distraction or rushed effort"),
        Rank("Nine", "resilience, independence and nearing completion", "fatigue, defensiveness or difficulty sustaining effort"),
        Rank("Ten", "completion, responsibility and the result of a cycle", "burden, unfinished business or resistance to closure"),
        Rank("Page", "curiosity, a message and beginner energy", "immaturity, poor news or learning without action"),
        Rank("Knight", "pursuit, movement and committed action", "recklessness, delay or inconsistent direction"),
        Rank("Queen", "mature inner command and supportive influence", "insecurity, overextension or blocked self-trust"),
        Rank("King", "responsible outward command and mastery", "misused authority, stubbornness or poor control")
    )

    val cards: List<TarotCard> = buildList {
        majors.forEachIndexed { index, card ->
            add(
                TarotCard(
                    id = "major_$index",
                    name = card.name,
                    arcana = "Major Arcana ${roman(index)}",
                    symbol = card.symbol,
                    keywords = card.keywords,
                    upright = card.upright,
                    reversed = card.reversed,
                    love = "Consider how ${card.keywords} affect honesty, choice and connection.",
                    career = "Apply this theme to purpose, responsibility and your next practical action.",
                    money = "Use this theme for reflection, but base financial decisions on evidence and risk limits."
                )
            )
        }
        suits.forEach { suit ->
            ranks.forEachIndexed { index, rank ->
                val number = index + 1
                add(
                    TarotCard(
                        id = "${suit.name.lowercase(Locale.US)}_$number",
                        name = "${rank.name} of ${suit.name}",
                        arcana = "Minor Arcana • ${suit.name}",
                        symbol = suit.symbol,
                        keywords = "${rank.theme}; ${suit.domain}",
                        upright = "This card combines ${rank.theme} with ${suit.domain}.",
                        reversed = "This card can point to ${rank.shadow} in matters of ${suit.domain}.",
                        love = "Look at ${suit.love} through the theme of ${rank.theme}.",
                        career = "Consider ${suit.career}, especially ${rank.theme}.",
                        money = "Review ${suit.money}; this is reflective guidance, not a financial signal."
                    )
                )
            }
        }
    }

    init {
        check(cards.size == 78) { "A tarot deck must contain exactly 78 cards." }
        check(cards.map { it.id }.distinct().size == 78) { "Tarot card IDs must be unique." }
    }

    fun draw(count: Int, allowReversed: Boolean = true, random: Random = Random.Default): List<TarotDraw> {
        require(count in 1..3) { "Choose one card or a three-card spread." }
        val positions = if (count == 1) listOf("Guidance") else listOf("Past", "Present", "Future")
        return cards.shuffled(random).take(count).mapIndexed { index, card ->
            TarotDraw(card, allowReversed && random.nextBoolean(), positions[index])
        }
    }

    fun reading(questionInput: String, count: Int = 1, allowReversed: Boolean = true): String {
        val question = questionInput.replace(Regex("[\\r\\n\\t]+"), " ").trim().take(500)
        val draws = draw(count, allowReversed)
        return buildString {
            append(if (count == 1) "One-card reflection" else "Past–present–future reflection")
            if (question.isNotBlank()) append("\nQuestion: $question")
            append("\n\n")
            draws.forEach { draw ->
                val orientation = if (draw.reversed) "Reversed" else "Upright"
                val meaning = if (draw.reversed) draw.card.reversed else draw.card.upright
                append("${draw.position}: ${draw.card.symbol} ${draw.card.name} — $orientation\n")
                append("Keywords: ${draw.card.keywords}\n")
                append("Meaning: $meaning\n")
                append("Love: ${draw.card.love}\n")
                append("Career: ${draw.card.career}\n")
                append("Money: ${draw.card.money}\n\n")
            }
            append("Use this reading for reflection or entertainment, not as factual prediction or medical, legal, safety or trading advice.")
        }
    }

    fun details(card: TarotCard): String = buildString {
        append("${card.symbol} ${card.name}\n${card.arcana}\n\n")
        append("Keywords: ${card.keywords}\n\n")
        append("Upright: ${card.upright}\n\n")
        append("Reversed: ${card.reversed}\n\n")
        append("Love: ${card.love}\n\n")
        append("Career: ${card.career}\n\n")
        append("Money: ${card.money}")
    }

    private fun roman(value: Int): String = when (value) {
        0 -> "0"
        1 -> "I"
        2 -> "II"
        3 -> "III"
        4 -> "IV"
        5 -> "V"
        6 -> "VI"
        7 -> "VII"
        8 -> "VIII"
        9 -> "IX"
        10 -> "X"
        11 -> "XI"
        12 -> "XII"
        13 -> "XIII"
        14 -> "XIV"
        15 -> "XV"
        16 -> "XVI"
        17 -> "XVII"
        18 -> "XVIII"
        19 -> "XIX"
        20 -> "XX"
        else -> "XXI"
    }
}
