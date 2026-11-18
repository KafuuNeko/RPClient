package me.kafuuneko.rpclient.feature.llmprovideredit

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.kafuuneko.rpclient.feature.llmprovideredit.model.ProviderPreset
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditModelCatalogState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditTestState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiIntent
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiState
import me.kafuuneko.rpclient.libs.llm.model.GEMINI_INTERACTIONS_BASE_URL
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import okio.buffer
import okio.source
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** 用真实模型配置页及回环 HTTP 服务验证未保存的预设、分页和连接探针。 */
class GeminiInteractionsProviderTest {
    /** 目录与生成使用同一自定义前缀、密钥头和草稿，切换协议不会隐式回退。 */
    @Test
    fun nativePresetListsModelsAndTestsTheChosenModel(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
            val served = serveRequests(server)
            ActivityScenario.launch<LLMProviderEditActivity>(
                Intent(
                    context,
                    LLMProviderEditActivity::class.java
                )
            ).use { scenario ->
                lateinit var vm: LLMProviderEditViewModel
                scenario.onActivity {
                    vm = ViewModelProvider(it)[LLMProviderEditViewModel::class.java]
                }
                val states = vm.uiStateFlow.filterIsInstance<LLMProviderEditUiState.Normal>()
                withTimeout(15_000) {
                    states.first()
                    vm.emit(LLMProviderEditUiIntent.ChangeTemperature(""))
                    vm.emit(LLMProviderEditUiIntent.ChangeTopP(""))
                    vm.emit(LLMProviderEditUiIntent.ApplyPresetTemplate(ProviderPreset.GeminiInteractions))
                    val preset =
                        states.first { it.form.protocol == LLMProviderProtocol.GeminiInteractions }.form
                    assertEquals(GEMINI_INTERACTIONS_BASE_URL, preset.baseUrl)
                    assertEquals("{}", preset.requestBodyPatchJson.trim())
                    // 选择器使用分页结果；没有 Interactions 能力声明的可生成模型仍可显式试用。
                    vm.emit(LLMProviderEditUiIntent.ChangeBaseUrl("http://127.0.0.1:${server.localPort}/gateway/v1/"))
                    vm.emit(LLMProviderEditUiIntent.ConfirmApiKeyReplacement("fixture-key"))
                    vm.emit(LLMProviderEditUiIntent.ConfirmCustomHeadersReplacement("""{"X-Fixture":"gateway"}"""))
                    vm.emit(LLMProviderEditUiIntent.QueryModels)
                    val loaded =
                        states.first { it.modelCatalogState is LLMProviderEditModelCatalogState.Loaded }.modelCatalogState as LLMProviderEditModelCatalogState.Loaded
                    assertEquals(listOf("unknown", "native"), loaded.models.map { it.id })
                    vm.emit(LLMProviderEditUiIntent.ShowModelPicker)
                    vm.emit(LLMProviderEditUiIntent.SelectAvailableModel("native"))
                    // 使用编辑器的实际连接测试动作，不另建专用测试客户端。
                    vm.emit(LLMProviderEditUiIntent.TestClick)
                    val result =
                        states.first { it.testState is LLMProviderEditTestState.Success }.testState
                    assertEquals(LLMProviderEditTestState.Success("ok"), result)
                }
            }
            served.get(2, TimeUnit.SECONDS)
        }
    }

    /** 服务只接收合成数据，断言完整路径和头部以发现认证或路由串用。 */
    private fun serveRequests(server: ServerSocket): CompletableFuture<Unit> {
        val result = CompletableFuture<Unit>()
        thread(isDaemon = true, name = "interactions-provider-fixture") {
            try {
                val routes = listOf(
                    "GET /gateway/v1/models?pageSize=1000 HTTP/1.1",
                    "GET /gateway/v1/models?pageSize=1000&pageToken=next HTTP/1.1",
                    "POST /gateway/v1/interactions HTTP/1.1",
                )
                // 同一空闲监听口按序接收两页目录和最终探针；返回 JSON 经过真实传输层。
                for ((index, route) in routes.withIndex()) {
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val input = socket.getInputStream().source().buffer()
                        assertEquals(route, input.readUtf8LineStrict())
                        val headers = generateSequence {
                            input.readUtf8LineStrict().takeIf(String::isNotEmpty)
                        }
                            .associate {
                                it.substringBefore(':').lowercase() to it.substringAfter(
                                    ':'
                                ).trim()
                            }
                        assertEquals("fixture-key", headers["x-goog-api-key"])
                        assertEquals("gateway", headers["x-fixture"])
                        val response = when (index) {
                            0 -> """{"models":[{"name":"models/unknown"},{"name":"models/embedding","supportedGenerationMethods":["embedContent"]}],"nextPageToken":"next"}"""
                            1 -> """{"models":[{"name":"models/native","supportedActions":["createInteraction"]}]}"""
                            else -> {
                                val post = JSONObject(
                                    input.readUtf8(
                                        headers.getValue("content-length").toLong()
                                    )
                                )
                                assertEquals("native", post.getString("model"))
                                assertFalse(post.getBoolean("store"))
                                assertFalse(post.getBoolean("stream"))
                                assertFalse(post.has("tools"))
                                """{"status":"completed","steps":[{"type":"model_output","content":[{"type":"text","text":"ok"}]}]}"""
                            }
                        }
                        val bytes = response.toByteArray()
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(bytes)
                        }
                    }
                }
                result.complete(Unit)
            } catch (error: Throwable) {
                result.completeExceptionally(error)
            }
        }
        return result
    }
}
