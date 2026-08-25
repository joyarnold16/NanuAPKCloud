package com.example.llama

object SafetyGuard {
    const val SYSTEM_RULES = " Safety rules: refuse requests to generate explicit sexual or nude content, any sexual content involving minors, non-consensual sexual content, instructions that facilitate violent wrongdoing or self-harm, credential theft/phishing/malware, fraud, or deceptive impersonation. Safe educational, medical, historical, defensive-security and harm-prevention discussion is allowed when it does not provide actionable harmful instructions."

    fun blockedReason(text: String, image: Boolean = false): String? {
        val value = text.lowercase().replace(Regex("\\s+"), " ").trim()
        if (value.isBlank()) return null

        val minor = containsAny(value, listOf(
            "child", "kid", "underage", "minor", "toddler", "baby", "preteen", "schoolgirl", "schoolboy"
        ))
        val sexual = containsAny(value, listOf(
            " nude", "nude ", " naked", "naked ", "porn", "explicit sex", "sexual image", "sex scene", "erotic", "genitals"
        ))
        if (minor && sexual) {
            return "Nanu cannot generate sexual or nude content involving minors."
        }

        if (containsAny(value, listOf("rape fantasy", "forced sex", "sexual assault scene", "non-consensual sex"))) {
            return "Nanu cannot generate non-consensual sexual content."
        }

        if (image && sexual) {
            return "Nanu cannot create explicit sexual or nude images."
        }

        if (containsAny(value, listOf(
                "best way to kill myself", "how to kill myself", "how can i kill myself",
                "suicide method", "suicide instructions", "how to self harm", "how to cut myself"
            ))) {
            return "Nanu cannot provide instructions for self-harm. It can help with immediate safety and support instead."
        }

        if (containsAny(value, listOf(
                "how to make a bomb", "build a bomb", "make an explosive to", "poison someone",
                "kill someone without getting caught", "how to murder", "hide a body after killing"
            ))) {
            return "Nanu cannot provide instructions that facilitate violent wrongdoing."
        }

        if (containsAny(value, listOf(
                "steal passwords", "steal credentials", "phishing kit", "credential phishing page",
                "bypass 2fa to access", "malware to steal", "ransomware code to attack", "keylogger to steal"
            ))) {
            return "Nanu cannot provide instructions or tools for credential theft, phishing, malware, or unauthorized access."
        }

        if (containsAny(value, listOf(
                "fake bank statement", "forge bank statement", "fake identity document", "forge passport",
                "credit card fraud", "scam people into sending money"
            ))) {
            return "Nanu cannot help create fraudulent documents or facilitate scams."
        }

        return null
    }

    private fun containsAny(text: String, phrases: List<String>): Boolean = phrases.any { text.contains(it) }
}
