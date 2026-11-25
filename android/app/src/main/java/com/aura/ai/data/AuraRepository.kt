package com.aura.ai.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** Text and files typed on the home screen, handed over to a new chat. */
data class Draft(val text: String, val attachments: List<Attachment> = emptyList(), val autoSend: Boolean = true)

/**
 * Single source of truth for the app. The server keeps the history, so every device
 * signed in to the same account sees the same chats; a small on-disk cache keeps the
 * last copy available offline.
 */
class AuraRepository(context: Context, val session: SessionStore, val api: ApiClient) {

    private val cacheDir = File(context.filesDir, "cache").apply { mkdirs() }

    private val _loggedIn = MutableStateFlow(session.token != null)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _account = MutableStateFlow(session.cachedAccount)
    val account: StateFlow<Account?> = _account.asStateFlow()

    private val _chats = MutableStateFlow(readCache("chats.json", ListSerializer(Chat.serializer())) ?: emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private val _images = MutableStateFlow(readCache("images.json", ListSerializer(GeneratedImage.serializer())) ?: emptyList())
    val images: StateFlow<List<GeneratedImage>> = _images.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _lastSync = MutableStateFlow(session.lastSyncAt)
    val lastSync: StateFlow<Long> = _lastSync.asStateFlow()

    var pendingDraft: Draft? = null

    init {
        api.onUnauthorized = { signOutLocally() }
    }

    // ----- auth -----

    suspend fun login(email: String, password: String) = finishAuth(api.login(email.trim(), password))

    suspend fun register(name: String, email: String, password: String) =
        finishAuth(api.register(name.trim(), email.trim(), password))

    private suspend fun finishAuth(res: AuthResponse) {
        session.token = res.token
        setAccount(res.account())
        _loggedIn.value = true
        runCatching { sync() }
    }

    suspend fun logout() {
        runCatching { api.logout() }
        signOutLocally()
    }

    suspend fun deleteAccount() {
        api.deleteAccount()
        signOutLocally()
    }

    fun signOutLocally() {
        session.clearSession()
        _account.value = null
        _chats.value = emptyList()
        _images.value = emptyList()
        _lastSync.value = 0
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
        _loggedIn.value = false
    }

    private fun setAccount(account: Account) {
        _account.value = account
        session.cachedAccount = account
    }

    fun applyAccount(account: Account) = setAccount(account)

    // ----- sync -----

    /** Pulls profile, chat list and images from the server. */
    suspend fun sync() {
        if (session.token == null) return
        _syncing.value = true
        try {
            setAccount(api.me())
            setChats(api.chats())
            setImages(api.images())
            val now = System.currentTimeMillis()
            session.lastSyncAt = now
            _lastSync.value = now
        } finally {
            _syncing.value = false
        }
    }

    suspend fun refreshAccount() {
        setAccount(api.me())
    }

    // ----- profile -----

    suspend fun updateProfile(name: String? = null, responseStyle: String? = null, language: String? = null) {
        setAccount(api.updateProfile(name, responseStyle, language))
    }

    suspend fun changePassword(current: String, new: String) = api.changePassword(current, new)

    // ----- chats -----

    private fun setChats(list: List<Chat>) {
        _chats.value = list.sortedByDescending { it.updatedAt }
        writeCache("chats.json", ListSerializer(Chat.serializer()), _chats.value)
    }

    fun upsertChat(chat: Chat) {
        setChats(listOf(chat) + _chats.value.filter { it.id != chat.id })
    }

    suspend fun searchChats(query: String): List<Chat> = api.chats(query)

    suspend fun createChat(mode: String): Chat = api.createChat(mode).also { upsertChat(it) }

    suspend fun renameChat(id: String, title: String) {
        upsertChat(api.renameChat(id, title))
    }

    suspend fun deleteChat(id: String) {
        api.deleteChat(id)
        setChats(_chats.value.filter { it.id != id })
        File(cacheDir, "messages_$id.json").delete()
    }

    suspend fun clearHistory() {
        api.deleteAllChats()
        setChats(emptyList())
        cacheDir.listFiles()?.filter { it.name.startsWith("messages_") }?.forEach { it.delete() }
    }

    fun cachedMessages(chatId: String): List<Message> =
        readCache("messages_$chatId.json", ListSerializer(Message.serializer())) ?: emptyList()

    suspend fun loadMessages(chatId: String): MessagesResponse {
        val res = api.messages(chatId)
        saveMessages(chatId, res.messages)
        upsertChat(res.chat)
        return res
    }

    fun saveMessages(chatId: String, messages: List<Message>) {
        writeCache("messages_$chatId.json", ListSerializer(Message.serializer()), messages)
    }

    fun bumpUsage(messages: Int = 0, images: Int = 0) {
        _account.update { acc ->
            acc?.copy(usage = acc.usage.copy(messagesToday = acc.usage.messagesToday + messages, imagesToday = acc.usage.imagesToday + images))
        }
    }

    // ----- images -----

    private fun setImages(list: List<GeneratedImage>) {
        _images.value = list
        writeCache("images.json", ListSerializer(GeneratedImage.serializer()), list)
    }

    suspend fun generateImage(prompt: String, style: String, size: String): GeneratedImage {
        val image = api.generateImage(prompt, style, size)
        setImages(listOf(image) + _images.value)
        bumpUsage(images = 1)
        return image
    }

    suspend fun deleteImage(id: String) {
        api.deleteImage(id)
        setImages(_images.value.filter { it.id != id })
    }

    // ----- cache helpers -----

    private fun <T> readCache(name: String, serializer: KSerializer<T>): T? = runCatching {
        val file = File(cacheDir, name)
        if (file.exists()) AuraJson.decodeFromString(serializer, file.readText()) else null
    }.getOrNull()

    private fun <T> writeCache(name: String, serializer: KSerializer<T>, value: T) {
        runCatching { File(cacheDir, name).writeText(AuraJson.encodeToString(serializer, value)) }
    }

    suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}
