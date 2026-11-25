package com.aura.ai.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.ai.data.ApiException
import com.aura.ai.data.Attachment
import com.aura.ai.data.AuraRepository
import com.aura.ai.data.Chat
import com.aura.ai.data.FileLoader
import com.aura.ai.data.Message
import com.aura.ai.data.MessageStatus
import com.aura.ai.data.StreamEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

data class PendingFile(
    val localId: String,
    val name: String,
    val mime: String = "",
    val size: Long = 0,
    val preview: ByteArray? = null,
    val uploading: Boolean = true,
    val attachment: Attachment? = null,
    val error: String? = null,
)

class ChatViewModel(
    private val repo: AuraRepository,
    chatId: String?,
    private val mode: String,
    initialText: String,
) : ViewModel() {

    companion object {
        const val MAX_FILES = 5
    }

    var chat by mutableStateOf<Chat?>(chatId?.let { id -> repo.chats.value.find { it.id == id } })
        private set
    val messages = mutableStateListOf<Message>()
    val pending = mutableStateListOf<PendingFile>()
    var input by mutableStateOf(initialText)
    var streaming by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var limitReached by mutableStateOf(false)

    private var streamingId: String? = null
    private var job: Job? = null

    val currentMode: String get() = chat?.mode ?: mode
    val canSend: Boolean
        get() = !streaming && pending.none { it.uploading } &&
            (input.isNotBlank() || pending.any { it.attachment != null })

    init {
        if (chatId != null) {
            messages.addAll(repo.cachedMessages(chatId))
            reload(chatId)
        }
        repo.pendingDraft?.let { draft ->
            repo.pendingDraft = null
            if (draft.text.isNotBlank()) input = draft.text
            if (draft.autoSend && draft.text.isNotBlank()) send()
        }
    }

    private fun reload(id: String) = viewModelScope.launch {
        loading = messages.isEmpty()
        try {
            val res = repo.loadMessages(id)
            chat = res.chat
            if (!streaming) {
                messages.clear()
                messages.addAll(res.messages)
            }
        } catch (e: ApiException) {
            if (messages.isEmpty()) error = e.message
        } finally {
            loading = false
        }
    }

    fun send(textOverride: String? = null) {
        if (textOverride != null) input = textOverride
        if (!canSend) return
        val account = repo.account.value
        if (account != null && account.limits.dailyMessages > 0 && account.usage.messagesToday >= account.limits.dailyMessages) {
            limitReached = true
            return
        }
        val text = input.trim()
        val files = pending.mapNotNull { it.attachment }
        input = ""
        pending.clear()
        error = null
        limitReached = false
        val now = System.currentTimeMillis()
        val tempUser = Message(id = "local-u-$now", role = "user", content = text, createdAt = now, attachments = files)
        messages.add(tempUser)
        runTurn(text, files, tempUser)
    }

    /** Asks Aura to answer the last message again (after an error or a stop). */
    fun retry() {
        if (streaming) return
        val last = messages.lastOrNull() ?: return
        if (!last.isUser) messages.removeAt(messages.lastIndex)
        error = null
        runTurn(null, emptyList(), null)
    }

    fun stop() {
        job?.cancel()
    }

    private fun runTurn(text: String?, files: List<Attachment>, tempUser: Message?) {
        streaming = true
        val placeholder = Message(id = "local-a-${System.currentTimeMillis()}", role = "assistant", content = "", status = MessageStatus.STREAMING)
        messages.add(placeholder)
        streamingId = placeholder.id

        job = viewModelScope.launch {
            try {
                val current = chat ?: repo.createChat(mode).also { chat = it }
                repo.api.streamReply(current.id, text, files.map { it.id }).collect { event ->
                    when (event) {
                        is StreamEvent.UserSaved -> replace(tempUser?.id, event.message)
                        is StreamEvent.AssistantStarted -> {
                            update(streamingId) { it.copy(id = event.id) }
                            streamingId = event.id
                        }
                        is StreamEvent.Delta -> update(streamingId) { it.copy(content = it.content + event.text) }
                        is StreamEvent.Failed -> error = event.message
                        is StreamEvent.Done -> {
                            replace(streamingId, event.message)
                            chat = event.chat
                            repo.upsertChat(event.chat)
                            repo.bumpUsage(messages = 1)
                        }
                    }
                }
            } catch (e: CancellationException) {
                update(streamingId) { it.copy(status = MessageStatus.STOPPED) }
                throw e
            } catch (e: ApiException) {
                if (e.isLimit) {
                    limitReached = true
                    messages.removeAll { it.id == streamingId || it.id == tempUser?.id }
                    if (text != null) input = text
                } else {
                    update(streamingId) { it.copy(status = MessageStatus.ERROR) }
                    error = e.message
                }
            } finally {
                streaming = false
                streamingId = null
                chat?.let { repo.saveMessages(it.id, messages.toList()) }
            }
        }
    }

    private fun indexOf(id: String?) = if (id == null) -1 else messages.indexOfFirst { it.id == id }

    private fun replace(id: String?, message: Message) {
        val i = indexOf(id)
        if (i >= 0) messages[i] = message
    }

    private fun update(id: String?, change: (Message) -> Message) {
        val i = indexOf(id)
        if (i >= 0) messages[i] = change(messages[i])
    }

    // ----- attachments -----

    fun attach(context: Context, uri: Uri) {
        if (pending.size >= MAX_FILES) {
            error = "Можно приложить не больше $MAX_FILES файлов"
            return
        }
        val localId = UUID.randomUUID().toString()
        pending.add(PendingFile(localId, name = "Загрузка…"))
        viewModelScope.launch {
            try {
                val file = FileLoader.read(context.applicationContext, uri)
                updatePending(localId) {
                    it.copy(name = file.name, mime = file.mime, size = file.bytes.size.toLong(), preview = if (file.mime.startsWith("image/")) file.bytes else null)
                }
                val uploaded = repo.api.upload(file.bytes, file.mime, file.name)
                updatePending(localId) { it.copy(uploading = false, attachment = uploaded) }
            } catch (e: ApiException) {
                updatePending(localId) { it.copy(uploading = false, error = e.message) }
            }
        }
    }

    fun removePending(localId: String) {
        pending.removeAll { it.localId == localId }
    }

    private fun updatePending(localId: String, change: (PendingFile) -> PendingFile) {
        val i = pending.indexOfFirst { it.localId == localId }
        if (i >= 0) pending[i] = change(pending[i])
    }

    // ----- chat actions -----

    fun rename(title: String) = viewModelScope.launch {
        val c = chat ?: return@launch
        try {
            repo.renameChat(c.id, title)
            chat = c.copy(title = title)
        } catch (e: ApiException) {
            error = e.message
        }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        val c = chat ?: return@launch onDone()
        try {
            repo.deleteChat(c.id)
            onDone()
        } catch (e: ApiException) {
            error = e.message
        }
    }
}
