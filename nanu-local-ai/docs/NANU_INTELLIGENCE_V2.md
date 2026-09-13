# Nanu Intelligence v2

This increment completes the first shared foundation for Nanu's six requested product areas while keeping inference and project knowledge local-first.

## Capability map

| Area | RC8 / v2 implementation |
| --- | --- |
| RAG and document memory | `LocalRagEngine` chunks every readable project document, ranks relevant excerpts locally, diversifies sources, enforces a context budget, and emits stable source citations. `ProStore` persists explicit user-created memory notes that can be disabled or deleted. |
| Better voice assistant | Continuous Talk prefers Android's on-device recognizer, supports partial results and a hands-free listen/reply loop. Chat responses have a visible Speak/Stop toggle, and agent-assisted completions can be spoken. |
| Improved image tools | The existing stable-diffusion.cpp flow supports local generation, image-to-image editing, configurable strength/steps, and bounded sequential batch edits in Pro projects. |
| Trading tools and UI | Trading Lab keeps indicator analysis deterministic, and Paper Trading remains virtual-money only. Agents may call capped position-size math, but there is no broker, wallet, private-key, transaction, or order-execution tool. |
| Nanu Pro | Projects, documents, images, conversations, memory, batch editing, and local agents are entitlement-gated. Billing handles products with or without an optional one-time offer token. |
| Agents and tools | A selected local agent may use only `calculator`, `position_size`, and (when a project is selected) `project_search`. Calls must be structured, outputs are bounded and treated as untrusted, and each request is limited to two tool steps. |

## Local safety boundaries

- Project files and memory stay in app-private storage unless the user explicitly shares them.
- Retrieved document text and tool results are reference data, never instructions that can override the system policy.
- Tool names are a closed allow-list; arbitrary shell and file execution are not available.
- Trading features do not connect to a broker or execute real-money orders.
- Agents remain subject to the same shared text and image safety policy as normal chat.

## Validation

Unit coverage includes retrieval relevance, source diversity, context bounds, citation-name sanitization, version-1 database migration, memory persistence, tool-call parsing, calculator isolation, and position-risk limits. The RC8 workflow runs these tests before producing the APK and AAB.

Real-device acceptance should cover microphone/TTS behavior, long-document latency, process recovery during agent tool loops, image-model performance and thermal behavior, and purchase restoration with the Play test product.
