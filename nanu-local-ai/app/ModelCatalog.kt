package com.example.llama

data class RecommendedModel(
    val id: String,
    val name: String,
    val quant: String,
    val sizeLabel: String,
    val sizeBytes: Long,
    val minimumRamGb: Int,
    val speedLabel: String,
    val useCase: String,
    val notes: String,
    val pageUrl: String,
    val downloadUrl: String,
    val fileName: String,
    val licenseLabel: String
)

object ModelCatalog {
    val models = listOf(
        RecommendedModel(
            id = "gemma3-1b-q4km",
            name = "Gemma 3 1B Instruct",
            quant = "Q4_K_M",
            sizeLabel = "806 MB",
            sizeBytes = 806L * 1024L * 1024L,
            minimumRamGb = 4,
            speedLabel = "Very fast",
            useCase = "Lightweight chat",
            notes = "A compact option for lower-memory phones and tablets. Good when speed and battery use matter more than maximum answer quality.",
            pageUrl = "https://huggingface.co/ggml-org/gemma-3-1b-it-GGUF/blob/main/gemma-3-1b-it-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/ggml-org/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf?download=true",
            fileName = "gemma-3-1b-it-Q4_K_M.gguf",
            licenseLabel = "Gemma license"
        ),
        RecommendedModel(
            id = "qwen3-1.7b-q4km",
            name = "Qwen3 1.7B",
            quant = "Q4_K_M",
            sizeLabel = "1.28 GB",
            sizeBytes = 1280L * 1024L * 1024L,
            minimumRamGb = 6,
            speedLabel = "Fast",
            useCase = "Best everyday balance",
            notes = "Our first proven Nanu model. Strong general chat and coding for its size, with a good speed/quality balance on modern Android devices.",
            pageUrl = "https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/blob/main/Qwen3-1.7B-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf?download=true",
            fileName = "Qwen3-1.7B-Q4_K_M.gguf",
            licenseLabel = "Apache-2.0"
        ),
        RecommendedModel(
            id = "qwen2.5-coder-1.5b-q4km",
            name = "Qwen2.5 Coder 1.5B Instruct",
            quant = "Q4_K_M",
            sizeLabel = "1.12 GB",
            sizeBytes = 1120L * 1024L * 1024L,
            minimumRamGb = 6,
            speedLabel = "Fast",
            useCase = "Dedicated coding",
            notes = "A coding-focused model for Python, scripts, debugging and code explanation. A strong choice to pair with Nanu's Coding mode.",
            pageUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/blob/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf?download=true",
            fileName = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            licenseLabel = "Apache-2.0"
        ),
        RecommendedModel(
            id = "qwen3-4b-q4km",
            name = "Qwen3 4B",
            quant = "Q4_K_M",
            sizeLabel = "2.50 GB",
            sizeBytes = 2500L * 1024L * 1024L,
            minimumRamGb = 8,
            speedLabel = "Medium",
            useCase = "Better quality",
            notes = "Recommended for stronger devices when you want better reasoning, writing and coding quality than the 1.7B model. Expect lower generation speed and more heat.",
            pageUrl = "https://huggingface.co/ggml-org/Qwen3-4B-GGUF/blob/main/Qwen3-4B-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/ggml-org/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf?download=true",
            fileName = "Qwen3-4B-Q4_K_M.gguf",
            licenseLabel = "Apache-2.0"
        ),
        RecommendedModel(
            id = "qwen3-8b-q4km",
            name = "Qwen3 8B",
            quant = "Q4_K_M",
            sizeLabel = "5.03 GB",
            sizeBytes = 5030L * 1024L * 1024L,
            minimumRamGb = 12,
            speedLabel = "Slow / advanced",
            useCase = "Highest quality in this list",
            notes = "For high-memory flagship devices only. It can deliver better answers, but Android CPU inference may be slow and sustained use can cause thermal throttling. Not the default recommendation even on 12 GB devices.",
            pageUrl = "https://huggingface.co/Qwen/Qwen3-8B-GGUF/blob/main/Qwen3-8B-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/Qwen/Qwen3-8B-GGUF/resolve/main/Qwen3-8B-Q4_K_M.gguf?download=true",
            fileName = "Qwen3-8B-Q4_K_M.gguf",
            licenseLabel = "Apache-2.0"
        )
    )

    fun bestForRam(totalRamGb: Double, modeId: String? = null): RecommendedModel {
        if (modeId == "coding" && totalRamGb >= 6.0) {
            return models.first { it.id == "qwen2.5-coder-1.5b-q4km" }
        }
        return when {
            totalRamGb >= 10.0 -> models.first { it.id == "qwen3-4b-q4km" }
            totalRamGb >= 6.0 -> models.first { it.id == "qwen3-1.7b-q4km" }
            else -> models.first { it.id == "gemma3-1b-q4km" }
        }
    }
}
