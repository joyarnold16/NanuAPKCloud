# Nanu Intelligence v2

This increment completes the first shared foundation for Nanu's six requested product areas while keeping inference and project knowledge local-first.

## Capability map

| Area | RC8 / v2 implementation |
| --- | --- |
| RAG and document memory | `LocalRagEngine` chunks every readable project document, ranks relevant excerpts locally, diversifies sources, enforces a context budget, and emits stable source citations. `ProStore` persists explicit user-created memory notes that can be disabled or deleted. |
| Better voice assistant | Continuous Talk prefers Android's on-device recognizer, supports partial results and a hands-free listen/reply loop. Chat responses have a visible Speak/Stop toggle, and agent-assisted completions can be spoken. |
| Improved image tools | The existing stable-diffusion.cpp flow supports local generation, image-to-image editing, configurable strength/steps, and bounded sequential batch edits in Pro projects. |
| Image discovery | When Online Tools are enabled, Chat can return reusable image previews from Wikimedia Commons with the source page, creator and licence metadata. This is separate from local image generation. |
| Product boundary | Trading Lab, Paper Trading, trade journals, technical-analysis UI, Trading chat mode and position-size tools are excluded. Trading will be developed as a separate application. General read-only current-price and currency-rate questions remain available as information tools. |
| Nanu Pro | Projects, project documents/memory, custom assistant instructions, and batch image editing are entitlement-gated. The base bounded agent and read-only online tools do not require Pro. Billing handles products with or without an optional one-time offer token. |
| Agents and tools | Every text chat can use the closed registry for `calculator`, device/place time, weather, current crypto reference prices, currency reference rates, news, web search, reusable-image search, offline tarot, and (when a Pro project is selected) `project_search`. Calls are structured and bounded to two tool steps. |
| Tarot | A separate offline screen contains all 78 cards, upright/reversed meanings, love/career/money context, one-card and past–present–future draws, plus a 30-reading device-local history. It is clearly separated from factual and trading tools. |

## Local safety boundaries

- Project files and memory stay in app-private storage unless the user explicitly shares them.
- Retrieved document text and tool results are reference data, never instructions that can override the system policy.
- Tool names are a closed allow-list; arbitrary shell and file execution are not available.
- Online Tools are read-only, can be switched off on Home, and send only the short place, symbol/pair or search term required for the request. Online answers retain source links and retrieval times.
- Trading analysis and execution features are not part of Nanu Local AI.
- Agents remain subject to the same shared text and image safety policy as normal chat.

## Validation

Unit coverage includes retrieval relevance, source diversity, context bounds, citation-name sanitization, version-1 database migration, memory persistence, tool-call parsing/routing, calculator isolation, position-risk limits, the complete 78-card deck, unique draws, and bounded online-result parsing. The RC8 workflow runs these tests before producing the APK and AAB.

Real-device acceptance should cover microphone/TTS behavior, long-document latency, process recovery during agent tool loops, each public provider's rate-limit/failure behavior, link handling, offline-toggle behavior, Tarot persistence, image-model performance and thermal behavior, and purchase restoration with the Play test product.
