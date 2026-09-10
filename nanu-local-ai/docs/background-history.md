# Background tasks and chat history

## Architecture

RC8 remains an overlay on llama.cpp commit `9a286ac98d2cab74231bd3f1fc3f2b8bdf05422e`, assembled by `ci/build_rc8.sh`. The native image engine pin, model catalog/download verification, trading tools, AI safety rules, and reporting remain in the existing pipeline. `ci/patch_background.py` registers the service and copies the new sources and tests into the generated Android project.

Chat, Continuous Talk, Ask My Files, and Create Studio submit durable requests to `LocalTaskService`. The service owns inference and image processes, holds no Activity/View references, and runs one task at a time. Its private notification opens the associated conversation or stops generation. A bounded partial wake lock supports screen-off processing. Completion, errors, cancellation and timeout release service resources. The legacy Talk/Create classes are retained for RC8 source compatibility; launcher navigation uses Continuous Talk/Create Studio.

`ChatStore` uses Android SQLiteOpenHelper, with foreign-key-linked conversations, messages, and tasks. Transactions run off the main thread and serialize task reservation, saves, and deletion. Streaming text checkpoints are throttled to roughly 300 ms; final output is saved immediately. Requests are cleared after completion. UI observes local database changes only while visible. A saved reply remains available after closing/reopening a screen.

Conversation history provides timestamps, literal substring search, automatic titles, rename, deletion with confirmation, clear-all, and reopen/continue. Existing RC8 file-answer preference history migrates once into the database. Earlier ordinary chats were only in memory and cannot be recovered after the original process is gone. Imported attachment text is kept for continuation; generated images retain their existing app-private paths and gallery export behavior. Deleting history removes database records, not exported gallery images or imported source files. Conversation continuation includes a bounded recent context; long conversations/documents may exceed the model's context window.

## Android behavior and limits

- Start tasks from a visible screen. No boot receiver or invisible restart loop is added.
- Android 13+ notification permission is requested. If declined, Android can hide the notification from the drawer; the foreground service still appears in Active apps. Users can stop it there.
- Android 14+ uses `specialUse` with a declared on-device inference subtype. This declaration needs Google Play review before production release; it is not an approval guarantee.
- A foreground service improves continuity but does not prevent force-stop, OEM battery restrictions, memory pressure, or device reboot. At next process start, unfinished records become **Interrupted** and retain the last saved partial reply. Regenerate restarts inference; native token/KV state is not checkpointed.
- Talk microphone listening and TTS playback stop when the screen leaves the foreground. A submitted reply keeps generating. This change does not provide always-listening background microphone access.
- Tasks have a one-hour application timeout and a 65-minute maximum wake lock. Native calls may not react to cancellation until they return; the service retains ownership while they finish. Image subprocesses are terminated in cleanup.
- RC8 targets API 36 with minimum API 33. App backup remains disabled. Database files are protected by the app sandbox; no separate database encryption is added.
- Models load inside the task service. A model selection means selected/available; load failures are recorded with the task. Each text request loads a fresh context to prevent cross-conversation leakage, which adds model-load latency.

## Verification

`tests/ChatStoreTest.kt` covers durable reopen, timestamps, literal search escaping, rename/delete/clear, checkpoint updates without task loss, duplicate claim rejection, concurrent task reservation, failed service start recovery, interrupted partial replies, RC8 history migration, and hidden thinking blocks. The dedicated GitHub validation workflow runs RC8 preflights, these JVM/Robolectric tests, APK/AAB builds and 16 KB native alignment checks.

Real-device acceptance remains necessary: start text and image tasks, press Home, turn the screen off, reopen through the notification; rotate/recreate screens; stop from the notification; deny notifications; force-stop and restore saved partial output; switch conversations; and exercise Talk, file attachments, model downloads, safety reporting, and trading tools on the previous RC8 installation. No emulator or physical-device result is implied by the JVM tests.

References: https://developer.android.com/develop/background-work/services/fgs/service-types and https://developer.android.com/develop/ui/views/notifications/notification-permission
