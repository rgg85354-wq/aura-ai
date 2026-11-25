package com.aura.ai.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val AuraJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    isLenient = true
}

@Serializable
data class Preferences(
    val responseStyle: String = "balanced",
    val language: String = "auto",
)

@Serializable
data class User(
    val id: String,
    val email: String,
    val name: String,
    val createdAt: Long = 0,
    val isPro: Boolean = false,
    val proUntil: Long? = null,
    val proPlatform: String? = null,
    val preferences: Preferences = Preferences(),
)

@Serializable
data class Usage(val messagesToday: Int = 0, val imagesToday: Int = 0)

@Serializable
data class Limits(val dailyMessages: Int = 0, val dailyImages: Int = 0)

@Serializable
data class Account(val user: User, val usage: Usage = Usage(), val limits: Limits = Limits())

@Serializable
data class AuthResponse(
    val token: String,
    val user: User,
    val usage: Usage = Usage(),
    val limits: Limits = Limits(),
) {
    fun account() = Account(user, usage, limits)
}

@Serializable
data class Chat(
    val id: String,
    val title: String,
    val mode: String = ChatMode.CHAT,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val preview: String = "",
    val messageCount: Int = 0,
)

object ChatMode {
    const val CHAT = "chat"
    const val CODE = "code"
    const val IDEAS = "ideas"
}

@Serializable
data class Attachment(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long = 0,
    val url: String,
) {
    val isImage get() = mime.startsWith("image/")
}

@Serializable
data class Message(
    val id: String,
    val chatId: String = "",
    val role: String,
    val content: String,
    val status: String = MessageStatus.COMPLETE,
    val createdAt: Long = 0,
    val attachments: List<Attachment> = emptyList(),
) {
    val isUser get() = role == "user"
}

object MessageStatus {
    const val COMPLETE = "complete"
    const val STREAMING = "streaming"
    const val STOPPED = "stopped"
    const val ERROR = "error"
    const val REFUSED = "refused"
    const val TRUNCATED = "truncated"
}

@Serializable
data class GeneratedImage(
    val id: String,
    val prompt: String,
    val style: String = "none",
    val size: String = "1024x1024",
    val createdAt: Long = 0,
    val url: String,
)

// ----- response wrappers -----

@Serializable data class ChatsResponse(val chats: List<Chat>)
@Serializable data class ChatResponse(val chat: Chat)
@Serializable data class MessagesResponse(val chat: Chat, val messages: List<Message>)
@Serializable data class FileResponse(val file: Attachment)
@Serializable data class ImagesResponse(val images: List<GeneratedImage>)
@Serializable data class ImageResponse(val image: GeneratedImage)
@Serializable data class ErrorInfo(val code: String = "error", val message: String = "Ошибка")
@Serializable data class ErrorBody(val error: ErrorInfo)
@Serializable data class StreamError(val code: String = "error", val message: String = "Ошибка", val messageId: String? = null)
@Serializable data class StreamStart(val id: String)
@Serializable data class StreamDelta(val text: String)
@Serializable data class StreamDone(val message: Message, val chat: Chat)

sealed interface StreamEvent {
    data class UserSaved(val message: Message) : StreamEvent
    data class AssistantStarted(val id: String) : StreamEvent
    data class Delta(val text: String) : StreamEvent
    data class Failed(val code: String, val message: String) : StreamEvent
    data class Done(val message: Message, val chat: Chat) : StreamEvent
}

class ApiException(val code: String, override val message: String, val status: Int = 0) : Exception(message) {
    val isLimit get() = code == "limit_reached"
}
