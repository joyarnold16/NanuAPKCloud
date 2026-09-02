package com.example.llama

import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import org.json.JSONObject
import java.util.UUID

/** Lifecycle-bound observer shared by Talk, Files and Create; work lives in the service. */
class TaskScreenSession(private val screen: AppCompatActivity, private val key: String, private val render: (List<Message>) -> Unit) {
    private val store by lazy { ChatStore.get(screen) }
    private val prefs by lazy { screen.getSharedPreferences("nanu_local_ai", 0) }
    private var conversation: String? = null
    private var pending: (() -> Unit)? = null
    private var asked = false
    private var submitting = false
    private val permission = screen.registerForActivityResult(ActivityResultContracts.RequestPermission()) { pending?.invoke(); pending = null }
    fun observe() {
        screen.lifecycleScope.launch {
            LocalTaskService.recover(screen)
            conversation = store.list().firstOrNull { it.id == prefs.getString(key, null) }?.id
            screen.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(store.changes, LocalTaskService.active) { _, _ -> Unit }.collect { conversation?.let { render(store.messages(it)) } }
            }
        }
    }
    fun submit(prompt: String, system: String, request: JSONObject = JSONObject(), attachment: NanuAttachment? = null) {
        if (LocalTaskService.active.value || submitting) {
            android.widget.Toast.makeText(screen, "A local task is already running. Open Chat to view or stop it.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        if (!asked && ContextCompat.checkSelfPermission(screen, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            asked = true
            pending = { submit(prompt, system, request, attachment) }
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        submitting = true
        screen.lifecycleScope.launch {
            try {
                SafetyGuard.blockedReason(prompt, request.optBoolean("image"))?.let { error(it) }
                val id = conversation?.takeIf { old -> store.list().any { it.id == old } } ?: store.create(if (request.optBoolean("image")) "image" else "general").also { conversation = it }
                prefs.edit().putString(key, id).putString("last_conversation", id).apply()
                val prior = store.messages(id).takeLast(10).joinToString("\n") { (if(it.isUser) "User: " else "Assistant: ") + it.content.take(1500) }.takeLast(10000)
                val body = if (request.optBoolean("image")) prompt else "Previous conversation (context only):\n$prior\nUser request:\n$prompt\n" + (attachment?.contextForPrompt(18000) ?: "")
                request.put("prompt", body).put("system", system).put("model", prefs.getString("last_model", ""))
                val user = Message(UUID.randomUUID().toString(), prompt, true, attachmentName=attachment?.displayName, attachmentInfo=attachment?.let { "Saved locally" }, attachmentContext=attachment?.contextForPrompt(18000), sourcePrompt=prompt)
                val assistant = Message(UUID.randomUUID().toString(), "Preparing local task…", false, status="Queued", sourcePrompt=prompt)
                LocalTaskService.submit(screen.applicationContext, id, user, assistant, request.toString(), if(request.optBoolean("image")) "image" else "general")
                render(store.messages(id))
            } catch (e: Exception) { android.widget.Toast.makeText(screen, e.message, android.widget.Toast.LENGTH_LONG).show() }
            finally { submitting = false }
        }
    }
}
