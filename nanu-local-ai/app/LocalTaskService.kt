package com.example.llama

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.File

/** Owns inference; never keeps an Activity, View, or UI callback alive. */
class LocalTaskService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val store by lazy { ChatStore.get(this) }
    private val images by lazy { LocalImageGenerator(applicationContext) }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "Local AI tasks", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            job?.cancel()
            if (job == null) stopSelf()
            return START_NOT_STICKY
        }
        val id = intent?.getStringExtra("task")
        if (id == null) { stopSelf(); return START_NOT_STICKY }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply {
            putExtra("conversation", intent.getStringExtra("conversation"))
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, LocalTaskService::class.java).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("Nanu is working locally")
            .setContentText("You can leave the app. Tap to reopen.").setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stop).build()
        try {
            ServiceCompat.startForeground(this, 401, notification, if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        } catch (e: RuntimeException) {
            scope.launch { store.failQueued(id); active.value = false; stopSelf() }
            return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY
        active.value = true
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var task: ChatTask? = null
            var reply: Message? = null
            try {
                withContext(NonCancellable) {
                    task = store.claim(id)
                    task?.let { claimed -> reply = store.messages(claimed.conversation).first { it.id == claimed.message } }
                }
                if (task == null) return@launch
                ensureActive()
                wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Nanu:LocalTask").apply { acquire(65 * 60 * 1000L) }
                val request = JSONObject(task!!.request)
                if (request.optBoolean("proFeature") || request.has("batchInputs") || request.optBoolean("agentTools")) check(ProEntitlement.enabled(this@LocalTaskService)) { "Restore Nanu Pro before using this tool." }
                withTimeout(60 * 60 * 1000L) {
                    if (request.optBoolean("image")) {
                        val batch = request.optJSONArray("batchInputs")
                        val inputs = if (batch != null) (0 until batch.length()).map { batch.getString(it) } else listOf(request.optString("inputImage"))
                        require(inputs.size in 1..4) { "Choose one to four images." }
                        for ((index,input) in inputs.withIndex()) {
                        ensureActive()
                        val result = images.generate(request.getString("prompt"), negativePrompt=request.optString("negative", "blurry, low quality, distorted, malformed"), quality=request.optBoolean("quality"), width=request.optInt("width").takeIf { it > 0 }, height=request.optInt("height").takeIf { it > 0 }, stepsOverride=request.optInt("steps").takeIf { it > 0 }, inputImagePath=input.takeIf { it.isNotBlank() }, changeStrength=request.optDouble("strength", 0.45)) { progress ->
                            reply = reply!!.copy(content = "Creating image locally…", status = progress)
                            if (inputs.size > 1) reply = reply!!.copy(status = "Image ${index+1}/${inputs.size} • $progress")
                            store.update(task!!, reply!!)
                        }
                        val options = JSONObject(ImageEditArguments.savedOptions(request) ?: "{}").put("inputImage",input)
                        reply = reply!!.copy(content=if (input.isNotBlank()) "Here is your edited image." else "Here is your generated image.", imagePath=result.file.absolutePath, imageOptions=options.toString(), createdAt=System.currentTimeMillis(), status="Done • generated locally • ${result.elapsedSeconds}s")
                        request.optString("projectId").takeIf { it.isNotBlank() }?.let { project ->
                            ProStore.get(this@LocalTaskService).addAsset(project,result.file.name,result.file.absolutePath,"image/png")
                        }
                        if(index < inputs.lastIndex) {
                            store.saveBatchResult(task!!.conversation, reply!!.copy(id=java.util.UUID.randomUUID().toString(),createdAt=System.currentTimeMillis()))
                            reply=reply!!.copy(imagePath=null)
                        }
                        }
                    } else {
                        val engine = AiChat.getInferenceEngine(applicationContext)
                        try {
                            withTimeout(30_000) { engine.state.first { it !is InferenceEngine.State.Initializing && it !is InferenceEngine.State.Uninitialized } }
                            val model = File(request.getString("model"))
                            require(model.isFile) { "Selected model is no longer available" }
                            when(engine.state.value) {
                                is InferenceEngine.State.ModelReady, is InferenceEngine.State.Error -> engine.cleanUp()
                                else -> Unit
                            }
                            engine.loadModel(model.absolutePath)
                            val toolsEnabled = request.optBoolean("agentTools")
                            val projectAvailable = request.optString("projectId").isNotBlank()
                            engine.setSystemPrompt(request.getString("system") + if(toolsEnabled) NanuToolRegistry.systemPrompt(projectAvailable) else "")
                            var nextPrompt = request.getString("prompt")
                            var totalTokens = 0
                            var toolSteps = 0
                            var finalAnswer = false
                            val started = android.os.SystemClock.elapsedRealtime()
                            while(!finalAnswer) {
                                val raw = StringBuilder()
                                var saved = 0L
                                engine.sendUserPrompt(nextPrompt).collect { token ->
                                    totalTokens++
                                    raw.append(token)
                                    val visible = visibleText(raw.toString())
                                    val choosingTool = toolsEnabled && visible.trimStart().startsWith("<tool_call>")
                                    reply = reply!!.copy(content=if(choosingTool) "Choosing a safe local tool…" else visible.ifBlank { "Thinking locally…" }, status=if(choosingTool) "Agent planning" else "Generating")
                                    val now = android.os.SystemClock.elapsedRealtime()
                                    if (now - saved >= 300) { store.update(task!!, reply!!); saved = now }
                                }
                                check(raw.isNotEmpty()) { "The model returned no answer. Try a shorter prompt or conversation." }
                                val call = if(toolsEnabled) NanuToolRegistry.parseCall(raw.toString()) else null
                                if(call == null) {
                                    reply = reply!!.copy(content=visibleText(raw.toString()).ifBlank { "The model returned no visible answer." })
                                    finalAnswer = true
                                } else {
                                    check(toolSteps < NanuToolRegistry.MAX_TOOL_STEPS) { "The local agent reached its tool-step limit." }
                                    val result = NanuToolRegistry.execute(this@LocalTaskService,request,call)
                                    toolSteps++
                                    reply = reply!!.copy(content="Used ${result.name.replace('_',' ')} locally. Preparing answer…",status="Agent step $toolSteps/${NanuToolRegistry.MAX_TOOL_STEPS}")
                                    store.update(task!!,reply!!)
                                    nextPrompt = NanuToolRegistry.followUpPrompt(result)
                                }
                            }
                            val seconds = (android.os.SystemClock.elapsedRealtime() - started).coerceAtLeast(1) / 1000.0
                            reply = reply!!.copy(status=if(toolSteps>0) "Complete • $toolSteps local tool${if(toolSteps==1) "" else "s"}" else "Complete", generationStats=String.format(java.util.Locale.US, "%d tokens • %.1f tok/s • %.1fs", totalTokens, totalTokens / seconds, seconds))
                        } finally {
                            withContext(NonCancellable) { runCatching { engine.cleanUp() } }
                        }
                    }
                }
                store.update(task!!, reply!!, "complete")
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    images.cancel()
                    if (task != null && reply != null) store.update(task!!, reply!!.copy(status=if (e is TimeoutCancellationException) "Time limit reached — tap Regenerate to retry" else "Stopped"), "stopped")
                }
            } catch (e: Exception) {
                if (task != null && reply != null) store.update(task!!, reply!!.copy(status="Failed: ${e.message ?: "Unknown error"}"), "failed")
            } finally {
                if (wakeLock?.isHeld == true) wakeLock?.release()
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                    active.value = false
                }
            }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object {
        private const val CHANNEL = "local_ai_tasks"
        private const val STOP = "com.nanu.localai.STOP_TASK"
        val active = MutableStateFlow(false)
        suspend fun recover(context: Context) {
            if (!active.value) ChatStore.get(context).recoverInterrupted()
        }
        fun start(context: Context, task: String, conversation: String) {
            active.value = true
            try {
                ContextCompat.startForegroundService(context, Intent(context, LocalTaskService::class.java).putExtra("task", task).putExtra("conversation", conversation))
            } catch (e: RuntimeException) { active.value = false; throw e }
        }
        suspend fun submit(context: Context, conversation: String, user: Message, assistant: Message, request: String, mode: String): String = withContext(NonCancellable) {
            val store = ChatStore.get(context)
            val task = store.enqueue(conversation, user, assistant, request, mode)
            try { start(context.applicationContext, task, conversation) }
            catch (e: RuntimeException) { store.failQueued(task); throw e }
            task
        }
        fun stop(context: Context) { context.startService(Intent(context, LocalTaskService::class.java).setAction(STOP)) }
        fun visibleText(raw: String): String {
            val cleaned = raw.replace(Regex("(?s)<think>.*?</think>"), "")
            return cleaned.substringBefore("<think>").replace("</think>", "").trimStart()
        }
    }
}
