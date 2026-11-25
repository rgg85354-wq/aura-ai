package com.aura.ai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** HTTP client for the Aura server. Every call throws [ApiException] with a readable message. */
class ApiClient(private val session: SessionStore) {

    /** Called when the server rejects the token, so the app can return to the sign-in screen. */
    var onUnauthorized: (() -> Unit)? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(200, TimeUnit.SECONDS) // image generation can take a while
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    // The server sends a heartbeat every 15 s while streaming.
    private val streamHttp = http.newBuilder().readTimeout(90, TimeUnit.SECONDS).build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun absoluteUrl(path: String): String =
        if (path.startsWith("http")) path else session.serverUrl.trimEnd('/') + path

    private fun request(path: String, auth: Boolean = true): Request.Builder =
        Request.Builder().url(absoluteUrl("/api$path")).apply {
            val token = session.token
            if (auth && token != null) header("Authorization", "Bearer $token")
        }

    private fun JsonObject.body(): RequestBody = toString().toRequestBody(jsonType)

    suspend fun <T> send(builder: Request.Builder, serializer: KSerializer<T>): T {
        val response = try {
            http.newCall(builder.build()).await()
        } catch (e: IOException) {
            throw networkError()
        }
        response.use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw errorFrom(res.code, text)
            return try {
                AuraJson.decodeFromString(serializer, text)
            } catch (e: Exception) {
                throw ApiException("bad_response", "Сервер вернул неожиданный ответ. Проверьте адрес сервера.")
            }
        }
    }

    private suspend fun sendNoContent(builder: Request.Builder) {
        val response = try {
            http.newCall(builder.build()).await()
        } catch (e: IOException) {
            throw networkError()
        }
        response.use { res -> if (!res.isSuccessful) throw errorFrom(res.code, res.body?.string().orEmpty()) }
    }

    private fun networkError() =
        ApiException("network", "Нет связи с сервером Aura. Проверьте интернет и адрес сервера в настройках.")

    private fun errorFrom(status: Int, text: String): ApiException {
        val info = runCatching { AuraJson.decodeFromString(ErrorBody.serializer(), text).error }.getOrNull()
        if (status == 401 && session.token != null) onUnauthorized?.invoke()
        return ApiException(info?.code ?: "http_$status", info?.message ?: "Ошибка сервера ($status)", status)
    }

    // ----- health -----

    suspend fun checkServer(url: String): Boolean {
        val req = Request.Builder().url(url.trimEnd('/') + "/api/health").build()
        return try {
            http.newBuilder().callTimeout(8, TimeUnit.SECONDS).build().newCall(req).await().use { it.isSuccessful }
        } catch (e: IOException) {
            false
        }
    }

    // ----- auth & profile -----

    suspend fun register(name: String, email: String, password: String): AuthResponse =
        send(
            request("/auth/register", auth = false).post(
                buildJsonObject {
                    put("name", name); put("email", email); put("password", password); put("device", "Android")
                }.body(),
            ),
            AuthResponse.serializer(),
        )

    suspend fun login(email: String, password: String): AuthResponse =
        send(
            request("/auth/login", auth = false).post(
                buildJsonObject { put("email", email); put("password", password); put("device", "Android") }.body(),
            ),
            AuthResponse.serializer(),
        )

    suspend fun logout() = sendNoContent(request("/auth/logout").post(ByteArray(0).toRequestBody()))

    suspend fun me(): Account = send(request("/me"), Account.serializer())

    suspend fun updateProfile(name: String? = null, responseStyle: String? = null, language: String? = null): Account =
        send(
            request("/me").patch(
                buildJsonObject {
                    name?.let { put("name", it) }
                    if (responseStyle != null || language != null) {
                        putJsonObject("preferences") {
                            responseStyle?.let { put("responseStyle", it) }
                            language?.let { put("language", it) }
                        }
                    }
                }.body(),
            ),
            Account.serializer(),
        )

    suspend fun changePassword(current: String, new: String) = sendNoContent(
        request("/me/password").post(buildJsonObject { put("currentPassword", current); put("newPassword", new) }.body()),
    )

    suspend fun deleteAccount() = sendNoContent(request("/me").delete())

    // ----- chats -----

    suspend fun chats(query: String = ""): List<Chat> {
        val q = if (query.isBlank()) "" else "?q=" + URLEncoder.encode(query, "UTF-8")
        return send(request("/chats$q"), ChatsResponse.serializer()).chats
    }

    suspend fun createChat(mode: String): Chat =
        send(request("/chats").post(buildJsonObject { put("mode", mode) }.body()), ChatResponse.serializer()).chat

    suspend fun renameChat(id: String, title: String): Chat =
        send(request("/chats/$id").patch(buildJsonObject { put("title", title) }.body()), ChatResponse.serializer()).chat

    suspend fun deleteChat(id: String) = sendNoContent(request("/chats/$id").delete())

    suspend fun deleteAllChats() = sendNoContent(request("/chats").delete())

    suspend fun messages(chatId: String): MessagesResponse =
        send(request("/chats/$chatId/messages"), MessagesResponse.serializer())

    /** Streams the assistant reply. Cancelling the collector stops generation on the server. */
    fun streamReply(chatId: String, text: String?, attachmentIds: List<String>): Flow<StreamEvent> = flow {
        val builder = if (text == null) {
            request("/chats/$chatId/regenerate").post(ByteArray(0).toRequestBody())
        } else {
            request("/chats/$chatId/messages").post(
                buildJsonObject {
                    put("text", text)
                    putJsonArray("attachmentIds") { attachmentIds.forEach { add(it) } }
                }.body(),
            )
        }
        val call = streamHttp.newCall(builder.header("Accept", "text/event-stream").build())
        currentCoroutineContext().job.invokeOnCompletion { call.cancel() }

        val response = try {
            call.execute()
        } catch (e: IOException) {
            throw networkError()
        }
        response.use { res ->
            if (!res.isSuccessful) throw errorFrom(res.code, res.body?.string().orEmpty())
            val source = res.body?.source() ?: throw networkError()
            var event = ""
            val data = StringBuilder()
            try {
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.isEmpty() -> {
                            if (event.isNotEmpty()) parseEvent(event, data.toString())?.let { emit(it) }
                            event = ""
                            data.clear()
                        }
                        line.startsWith("event:") -> event = line.substring(6).trim()
                        line.startsWith("data:") -> data.append(line.substring(5).trimStart())
                    }
                }
            } catch (e: IOException) {
                if (call.isCanceled()) return@flow
                throw ApiException("network", "Соединение прервалось во время ответа. Нажмите «Повторить».")
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun parseEvent(event: String, data: String): StreamEvent? = runCatching {
        when (event) {
            "user" -> StreamEvent.UserSaved(AuraJson.decodeFromString(Message.serializer(), data))
            "assistant" -> StreamEvent.AssistantStarted(AuraJson.decodeFromString(StreamStart.serializer(), data).id)
            "delta" -> StreamEvent.Delta(AuraJson.decodeFromString(StreamDelta.serializer(), data).text)
            "error" -> AuraJson.decodeFromString(StreamError.serializer(), data).let { StreamEvent.Failed(it.code, it.message) }
            "done" -> AuraJson.decodeFromString(StreamDone.serializer(), data).let { StreamEvent.Done(it.message, it.chat) }
            else -> null
        }
    }.getOrNull()

    // ----- files -----

    suspend fun upload(bytes: ByteArray, mime: String, name: String): Attachment =
        send(
            request("/uploads")
                .header("X-File-Name", URLEncoder.encode(name, "UTF-8").replace("+", "%20"))
                .post(bytes.toRequestBody(mime.toMediaType())),
            FileResponse.serializer(),
        ).file

    suspend fun download(path: String): ByteArray {
        val response = try {
            http.newCall(Request.Builder().url(absoluteUrl(path)).build()).await()
        } catch (e: IOException) {
            throw networkError()
        }
        response.use { res ->
            if (!res.isSuccessful) throw ApiException("download_failed", "Не удалось скачать файл")
            return res.body?.bytes() ?: throw ApiException("download_failed", "Не удалось скачать файл")
        }
    }

    // ----- images -----

    suspend fun images(): List<GeneratedImage> = send(request("/images"), ImagesResponse.serializer()).images

    suspend fun generateImage(prompt: String, style: String, size: String): GeneratedImage =
        send(
            request("/images").post(
                buildJsonObject { put("prompt", prompt); put("style", style); put("size", size) }.body(),
            ),
            ImageResponse.serializer(),
        ).image

    suspend fun deleteImage(id: String) = sendNoContent(request("/images/$id").delete())

    // ----- subscription -----

    suspend fun verifyGooglePurchase(productId: String, purchaseToken: String): Account =
        send(
            request("/subscription/verify").post(
                buildJsonObject {
                    put("platform", "google"); put("productId", productId); put("purchaseToken", purchaseToken)
                }.body(),
            ),
            Account.serializer(),
        )
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
