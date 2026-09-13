package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.Dispatchers
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

/** 已冻结的 HTTP JSON；日志仅含元数据，临时请求文件由请求结束统一释放。 */
internal data class PreparedWireBody(val body: RequestBody, val logJson: String, val file: File? = null) {
    /** 地址或自定义请求头校验失败时也释放已编码文件，成功后移交给网络调用。 */
    fun toHttpRequest(build: () -> Request): LLMHttpRequest = try {
        LLMHttpRequest(build(), logJson, file)
    } catch (error: Throwable) {
        file?.delete()
        throw error
    }
}

/**
 * 三协议共用图片编码器。
 * - JSON 树仅含随机图片占位符，实际 Base64 逐图流式写入有界临时文件。
 * - Patch 之后精确校验字节数，流式和非流式共用同一准备入口。
 */
internal class MultimodalWireCodec(private val provider: LLMProviderConfig) {
    private val mImages = mutableMapOf<String, LLMImageReference>()

    /** 根据协议生成内容数组，保持图文次序，纯文本 OpenAI / Anthropic 保持字符串。 */
    fun content(message: LLMMessage): Any {
        if (message.images.isEmpty() && provider.protocol != LLMProviderProtocol.Gemini) return message.content
        return JSONArray().also { array ->
            message.contentBlocks.forEach { block ->
                when (block) {
                    is LLMContentBlock.Text -> if (block.text.isNotBlank()) {
                        array.put(JSONObject().put("text", block.text).apply {
                            if (provider.protocol != LLMProviderProtocol.Gemini) put("type", "text")
                        })
                    }
                    is LLMContentBlock.Image -> array.put(image(block.reference))
                }
            }
        }
    }

    /** 图片结构只接受已准备资源，不允许高级 JSON 绕过图片生命周期。 */
    private fun image(reference: LLMImageReference): JSONObject {
        val marker = "rpclient-image-${UUID.randomUUID()}"
        mImages[marker] = reference
        return when (provider.protocol) {
            LLMProviderProtocol.OpenAICompatible -> JSONObject().put("type", "image_url")
                .put("image_url", JSONObject().put("url", marker))
            LLMProviderProtocol.Gemini -> JSONObject().put("inlineData", JSONObject()
                .put("mimeType", reference.mimeType).put("data", marker))
            LLMProviderProtocol.AnthropicMessages -> JSONObject().put("type", "image")
                .put("source", JSONObject().put("type", "base64")
                    .put("media_type", reference.mimeType).put("data", marker))
        }
    }

    /** 应用全部扩展后写出请求；任何准备失败都删除本次临时文件。 */
    suspend fun prepare(payload: JSONObject, request: LLMGenerationRequest, runtime: MessageImageRuntime?): PreparedWireBody {
        val json = payload.toString()
        if (mImages.isEmpty()) return PreparedWireBody(json.toRequestBody(JsonMediaType), json)
        if (provider.imageInputSetting == ImageInputSetting.Unsupported) throw ImageRequestException(ImageRequestFailure.Unsupported)
        if (mImages.size > MessageImagePolicy.MAX_IMAGES_PER_REQUEST) throw ImageRequestException(ImageRequestFailure.TooMany)
        require(request.messages.none { it.role == LLMMessageRole.System && it.images.isNotEmpty() }) {
            "系统指令不能包含用户图片"
        }
        val media = requireNotNull(runtime) { "图片处理服务不可用" }
        val file = media.createRequestFile()
        try {
            // 有界输出在写入过程中终止，避免超大 Patch 或图片占满磁盘。
            file.outputStream().buffered().use { output ->
                val bounded = LimitedOutputStream(output, MessageImagePolicy.MAX_REQUEST_BYTES)
                write(JsonParser.parseString(json), bounded, media)
            }
            val log = mImages.entries.fold(json) { value, (marker, ref) ->
                value.replace(marker, "[图片数据已省略，不能直接重放: ${ref.mimeType} ${ref.width}x${ref.height} ${ref.byteCount} bytes]")
            }
            return PreparedWireBody(file.asRequestBody(JsonMediaType), log, file)
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    /** 递归写 JSON 的小型结构，图片字符串直接串流，不生成完整 Base64 对象。 */
    private suspend fun write(element: JsonElement, output: OutputStream, runtime: MessageImageRuntime) {
        when {
            element.isJsonObject -> {
                withContext(Dispatchers.IO) { output.write('{'.code) }
                element.asJsonObject.entrySet().forEachIndexed { index, (key, value) ->
                    if (index > 0) output.write(','.code)
                    output.write(JsonPrimitive(key).toString().toByteArray(Charsets.UTF_8))
                    output.write(':'.code)
                    write(value, output, runtime)
                }
                withContext(Dispatchers.IO) { output.write('}'.code) }
            }
            element.isJsonArray -> {
                withContext(Dispatchers.IO) { output.write('['.code) }
                element.asJsonArray.forEachIndexed { index, value ->
                    if (index > 0) output.write(','.code)
                    write(value, output, runtime)
                }
                withContext(Dispatchers.IO) { output.write(']'.code) }
            }
            element.isJsonPrimitive && element.asJsonPrimitive.isString && element.asString in mImages -> {
                val reference = mImages.getValue(element.asString)
                withContext(Dispatchers.IO) {
                    output.write('"'.code)
                    if (provider.protocol == LLMProviderProtocol.OpenAICompatible) {
                        output.write("data:${reference.mimeType};base64,".toByteArray())
                    }
                }
                runtime.withSendFile(reference) { file ->
                    val nonClosing = object : FilterOutputStream(output) {
                        override fun close() { flush() }
                        override fun write(bytes: ByteArray, offset: Int, length: Int) { out.write(bytes, offset, length) }
                    }
                    Base64.getEncoder().wrap(nonClosing).use { encoded -> file.inputStream().use { it.copyTo(encoded) } }
                }
                withContext(Dispatchers.IO) { output.write('"'.code) }
            }
            else -> withContext(Dispatchers.IO) {
                output.write(element.toString().toByteArray(Charsets.UTF_8))
            }
        }
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
