package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.llm.ImageRequestException
import me.kafuuneko.rpclient.libs.llm.ImageRequestFailure
import me.kafuuneko.rpclient.libs.llm.model.ImageInputSetting
import me.kafuuneko.rpclient.libs.llm.model.LLMContentBlock
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.media.MessageImageRuntime
import me.kafuuneko.rpclient.libs.room.model.MessageImagePolicy
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.Base64
import java.util.UUID

/** 已冻结的 HTTP JSON；日志仅含元数据，临时请求文件由请求结束统一释放。 */
internal data class PreparedWireBody(val body: RequestBody, val logJson: String, val file: File? = null) {
    /** 地址或自定义请求头校验失败时也释放已编码文件，成功后移交给网络调用。 */
    fun toHttpRequest(build: () -> Request): LLMHttpRequest = try {
        LLMHttpRequest(request = build(), payloadJson = logJson, temporaryFile = file)
    } catch (error: Throwable) {
        file?.delete()
        throw error
    }
}

/**
 * 各协议共用的图片资源编码器。
 * - JSON 树仅含随机图片占位符，实际 Base64 逐图流式写入有界临时文件。
 * - Patch 之后精确校验字节数，流式和非流式共用同一准备入口。
 */
internal class MultimodalWireCodec(private val mProvider: LLMProviderConfig) {
    private val mImages = mutableMapOf<String, LLMImageReference>()

    /** 根据协议生成内容数组，保持图文次序，纯文本 OpenAI / Anthropic 保持字符串。 */
    fun content(message: LLMMessage): Any {
        if (message.images.isEmpty() && mProvider.protocol != LLMProviderProtocol.Gemini) return message.content
        return JSONArray().also { array ->
            message.contentBlocks.forEach { block ->
                when (block) {
                    is LLMContentBlock.Text -> if (block.text.isNotBlank()) {
                        array.put(
                            JSONObject().put("text", block.text).apply {
                            if (mProvider.protocol != LLMProviderProtocol.Gemini) {
                                put(
                                    "type",
                                    if (mProvider.protocol == LLMProviderProtocol.OpenAIResponses) "input_text" else "text",
                                )
                            }
                        },
                        )
                    }
                    is LLMContentBlock.Image -> array.put(image(block.reference))
                }
            }
        }
    }

    /** 图片结构只接受已准备资源，不允许高级 JSON 绕过图片生命周期。 */
    private fun image(reference: LLMImageReference): JSONObject {
        val marker = registerImage(reference)
        return when (mProvider.protocol) {
            LLMProviderProtocol.OpenAIResponses -> JSONObject().put("type", "input_image")
                .put("image_url", marker)
                .put("detail", "auto")
            LLMProviderProtocol.OpenAICompatible -> JSONObject().put("type", "image_url")
                .put("image_url", JSONObject().put("url", marker))
            LLMProviderProtocol.Gemini -> JSONObject().put(
                "inlineData",
                JSONObject()
                .put("mimeType", reference.mimeType).put("data", marker),
            )
            LLMProviderProtocol.AnthropicMessages -> JSONObject().put("type", "image")
                .put(
                    "source",
                    JSONObject().put("type", "base64")
                    .put("media_type", reference.mimeType)
                    .put("data", marker),
                )
        }
    }

    /** 登记本次请求持有的图片资源，允许独立协议编码器构造自己的 JSON 结构。 */
    fun registerImage(reference: LLMImageReference): String {
        val marker = "rpclient-image-${UUID.randomUUID()}"
        mImages[marker] = reference
        return marker
    }

    /** 应用全部扩展后写出请求；任何准备失败都删除本次临时文件。 */
    suspend fun prepare(payload: JSONObject, request: LLMGenerationRequest, runtime: MessageImageRuntime?): PreparedWireBody {
        return prepare(payload.toString(), request, runtime)
    }

    /** 将已完成协议编码的 JSON 写为有界请求体，图片占位符只在此处展开。 */
    suspend fun prepare(json: String, request: LLMGenerationRequest, runtime: MessageImageRuntime?): PreparedWireBody {
        if (mImages.isEmpty()) return PreparedWireBody(json.toRequestBody(JsonMediaType), json)
        if (mProvider.imageInputSetting == ImageInputSetting.Unsupported) throw ImageRequestException(ImageRequestFailure.Unsupported)
        if (mImages.size > MessageImagePolicy.MAX_IMAGES_PER_REQUEST) throw ImageRequestException(ImageRequestFailure.TooMany)
        require(request.messages.none { it.role == LLMMessageRole.System && it.images.isNotEmpty() }) {
            "System instructions cannot contain user images"
        }
        val media = requireNotNull(runtime) { "The image processing service is unavailable" }
        val file = media.createRequestFile()
        try {
            // 有界输出在写入过程中终止，避免超大 Patch 或图片占满磁盘。
            withContext(Dispatchers.IO) {
                file.outputStream().buffered().use { output ->
                    val bounded = LimitedOutputStream(output, MessageImagePolicy.MAX_REQUEST_BYTES)
                    write(JsonParser.parseString(json), bounded, media)
                }
            }
            val log = mImages.entries.fold(json) { value, (marker, ref) ->
                value.replace(
                    oldValue = marker,
                    newValue = "[image data omitted, cannot be replayed directly: " +
                    "${ref.mimeType} ${ref.width}x${ref.height} ${ref.byteCount} bytes]",
                )
            }
            return PreparedWireBody(body = file.asRequestBody(JsonMediaType), logJson = log, file = file)
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    /** 在调用方的 IO 上下文递归写 JSON，图片占位符由独立租约写出。 */
    private suspend fun write(element: JsonElement, output: OutputStream, runtime: MessageImageRuntime) {
        currentCoroutineContext().ensureActive()
        // 结构节点只输出标点与键，不为每个标点重复切换线程。
        when {
            element.isJsonObject -> {
                output.write('{'.code)
                element.asJsonObject.entrySet().forEachIndexed { index, (key, value) ->
                    if (index > 0) output.write(','.code)
                    output.write(JsonPrimitive(key).toString().toByteArray(Charsets.UTF_8))
                    output.write(':'.code)
                    write(value, output, runtime)
                }
                output.write('}'.code)
            }
            element.isJsonArray -> {
                output.write('['.code)
                element.asJsonArray.forEachIndexed { index, value ->
                    if (index > 0) output.write(','.code)
                    write(value, output, runtime)
                }
                output.write(']'.code)
            }
            // 仅本次登记的随机占位符可触发资源读取，普通字符串保持 JSON 转义。
            element.isJsonPrimitive && element.asJsonPrimitive.isString && element.asString in mImages ->
                writeImage(mImages.getValue(element.asString), output, runtime)
            else -> output.write(element.toString().toByteArray(Charsets.UTF_8))
        }
    }

    /** 持资源租约流式编码单张图片；关闭 Base64 包装流只刷新，不关闭外层 JSON。 */
    private suspend fun writeImage(reference: LLMImageReference, output: OutputStream, runtime: MessageImageRuntime) {
        output.write('"'.code)
        if (mProvider.protocol == LLMProviderProtocol.OpenAICompatible
            || mProvider.protocol == LLMProviderProtocol.OpenAIResponses) {
            output.write("data:${reference.mimeType};base64,".toByteArray(Charsets.UTF_8))
        }
        val context = currentCoroutineContext()
        // 文件版本一致性与租约由 Runtime 保证，编码器只持有当前单图缓冲区。
        runtime.withSendFile(reference) { file ->
            Base64.getEncoder().wrap(NonClosingOutputStream(output)).use { encoded ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        context.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        encoded.write(buffer, 0, count)
                    }
                }
            }
        }
        output.write('"'.code)
    }

}

/** 在写入前检查剩余字节，完整 JSON 不能超过客户端约束。 */
private class LimitedOutputStream(output: OutputStream, private val mMaximum: Long) : FilterOutputStream(output) {
    private var mCount = 0L
    override fun write(value: Int) {
        if (mCount >= mMaximum) throw ImageRequestException(ImageRequestFailure.TooLarge)
        out.write(value)
        mCount++
    }
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (length > mMaximum - mCount) throw ImageRequestException(ImageRequestFailure.TooLarge)
        out.write(bytes, offset, length)
        mCount += length
    }
}

/** Base64 结束时写出尾部填充，但 JSON 输出流仍由最外层 use 持有。 */
private class NonClosingOutputStream(output: OutputStream) : FilterOutputStream(output) {
    override fun close() { flush() }
    override fun write(bytes: ByteArray, offset: Int, length: Int) { out.write(bytes, offset, length) }
}
