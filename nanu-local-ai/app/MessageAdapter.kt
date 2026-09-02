package com.example.llama

import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView


data class Message(
    val id: String,
    val content: String,
    val isUser: Boolean,
    val attachmentName: String? = null,
    val attachmentInfo: String? = null,
    val imagePath: String? = null,
    val status: String? = null,
    val sourcePrompt: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

class MessageAdapter(
    private val messages: List<Message>,
    private val onCopy: (String) -> Unit,
    private val onSpeak: (Message) -> Unit,
    private val onRegenerate: (Message) -> Unit,
    private val onShare: (Message) -> Unit,
    private val onEditPrompt: (Message) -> Unit,
    private val onSaveImage: (Message) -> Unit,
    private val onReport: (Message) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var speakingMessageId: String? = null

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_ASSISTANT = 2
    }

    fun setSpeakingMessage(messageId: String?) {
        if (speakingMessageId == messageId) return
        speakingMessageId = messageId
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_ASSISTANT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            UserMessageViewHolder(inflater.inflate(R.layout.item_message_user, parent, false))
        } else {
            AssistantMessageViewHolder(inflater.inflate(R.layout.item_message_assistant, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        val item = holder.itemView
        val content = item.findViewById<TextView>(R.id.msg_content)
        item.findViewById<TextView>(R.id.msg_timestamp)?.text = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(message.createdAt))
        content.text = message.content
        content.setOnLongClickListener {
            onCopy(message.content)
            true
        }

        item.findViewById<TextView>(R.id.msg_attachment)?.apply {
            val label = message.attachmentName?.let { name ->
                val detail = message.attachmentInfo?.takeIf { it.isNotBlank() }
                if (detail == null) "📎  $name" else "📎  $name  •  $detail"
            }
            text = label.orEmpty()
            visibility = if (label == null) View.GONE else View.VISIBLE
        }

        item.findViewById<TextView>(R.id.msg_copy)?.setOnClickListener { onCopy(message.content) }

        if (!message.isUser) {
            item.findViewById<TextView>(R.id.msg_speak)?.apply {
                val active = speakingMessageId == message.id
                text = if (active) "Speaking • tap to stop" else "Speak"
                if (active) {
                    val radius = 12f * resources.displayMetrics.density
                    background = GradientDrawable().apply {
                        cornerRadius = radius
                        setColor(ContextCompat.getColor(context, R.color.nanu_accent))
                    }
                    setTextColor(ContextCompat.getColor(context, android.R.color.white))
                } else {
                    background = null
                    setTextColor(ContextCompat.getColor(context, R.color.nanu_muted))
                }
                setOnClickListener { onSpeak(message) }
            }
            item.findViewById<TextView>(R.id.msg_regenerate)?.setOnClickListener { onRegenerate(message) }
            item.findViewById<TextView>(R.id.msg_share)?.setOnClickListener { onShare(message) }
            item.findViewById<TextView>(R.id.msg_report)?.setOnClickListener { onReport(message) }

            item.findViewById<TextView>(R.id.msg_copy_code)?.apply {
                visibility = if (message.content.contains("```")) View.VISIBLE else View.GONE
                setOnClickListener { onCopy(extractCode(message.content)) }
            }

            val image = item.findViewById<ImageView>(R.id.msg_image)
            val imageActions = item.findViewById<View>(R.id.msg_image_actions)
            val status = item.findViewById<TextView>(R.id.msg_status)
            val path = message.imagePath
            if (!path.isNullOrBlank()) {
                val bitmap = BitmapFactory.decodeFile(path)
                image?.setImageBitmap(bitmap)
                image?.visibility = if (bitmap != null) View.VISIBLE else View.GONE
                imageActions?.visibility = View.VISIBLE
            } else {
                image?.setImageDrawable(null)
                image?.visibility = View.GONE
                imageActions?.visibility = View.GONE
            }

            status?.text = message.status.orEmpty()
            status?.visibility = if (message.status.isNullOrBlank()) View.GONE else View.VISIBLE

            item.findViewById<TextView>(R.id.image_save)?.setOnClickListener { onSaveImage(message) }
            item.findViewById<TextView>(R.id.image_share)?.setOnClickListener { onShare(message) }
            item.findViewById<TextView>(R.id.image_regenerate)?.setOnClickListener { onRegenerate(message) }
            item.findViewById<TextView>(R.id.image_edit)?.setOnClickListener { onEditPrompt(message) }
        }
    }

    override fun getItemCount(): Int = messages.size

    private fun extractCode(text: String): String {
        val match = Regex("```(?:[A-Za-z0-9_+.-]+)?\\s*([\\s\\S]*?)```", RegexOption.MULTILINE).find(text)
        return match?.groupValues?.getOrNull(1)?.trim() ?: text
    }

    class UserMessageViewHolder(view: View) : RecyclerView.ViewHolder(view)
    class AssistantMessageViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
