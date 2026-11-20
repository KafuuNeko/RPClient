package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.prompt.preset.PromptTemplateSnapshot
import me.kafuuneko.rpclient.libs.prompt.request.PromptRequestFinalizer
import me.kafuuneko.rpclient.libs.prompt.macro.PromptMacroResolver
import me.kafuuneko.rpclient.libs.prompt.worldbook.WorldBookActivator
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.groupchat.GroupChatGenerationMode
import me.kafuuneko.rpclient.libs.groupchat.GroupChatPromptBuildResult
import me.kafuuneko.rpclient.libs.groupchat.GroupChatPromptBuilder
import me.kafuuneko.rpclient.libs.groupchat.GroupChatPromptContext
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.prompt.model.PromptBuildContext
import me.kafuuneko.rpclient.libs.prompt.model.PromptGenerationMode
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMember
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.repository.GroupChatMemberData
import me.kafuuneko.rpclient.libs.memory.MemoryPromptInput
import me.kafuuneko.rpclient.libs.memory.MemoryPromptItem
import me.kafuuneko.rpclient.libs.memory.MemoryRef
import me.kafuuneko.rpclient.libs.memory.MemoryTable
import me.kafuuneko.rpclient.libs.macro.MacroScope
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

/** 改造前固定的普通构建结果；快照只能在明确审查兼容性后更新。 */
class NormalPromptBaselineTest {
    // 与应用一样共享 tokenizer 注册表，避免按协议样例反复加载同一编码词表。
    private val mFinalizer = PromptRequestFinalizer()

    /** 固定普通生成在各协议与模式下的既有请求、检查字段和世界书状态。 */
    @Test
    fun normalBuildersRetainRequestsAndWorldInfoAcrossProtocolsAndModes() {
        val actual = JsonObject()
        val gson = GsonBuilder().setPrettyPrinting().create()
        // 使用固定身份、时钟和已知文本，完整比较请求及既有检查字段，保留空白和内容顺序。
        LLMProviderProtocol.entries.forEach { protocol ->
            PromptPostProcessingMode.entries.forEach { processing ->
                PromptGenerationMode.entries.forEach { mode ->
                    val provider = provider(protocol, processing)
                    val name = "${protocol.name}/${processing.name}/${mode.name}"
                    actual.add("single/$name", gson.toJsonTree(single(provider, mode)))
                    actual.add("group/$name", gson.toJsonTree(group(provider, mode)))
                }
            }
        }
        // 固定快照沿用分段元数据引入前的结构；记忆分段追踪在模式门控用例中单独验证。
        val actualSnapshot = JsonParser.parseString(gson.toJson(actual)).asJsonObject
        actualSnapshot.entrySet().forEach { (_, result) ->
            result.asJsonObject.getAsJsonObject("inspection").getAsJsonArray("items")
                .forEach { item -> item.asJsonObject.remove("segments") }
        }
        val expected = requireNotNull(javaClass.getResourceAsStream("/normal-prompt-baseline.json"))
        expected.bufferedReader().use {
            assertEquals(JsonParser.parseReader(it), actualSnapshot)
        }
    }

    /** 真实构建器在所有协议中只为普通回复和重生成加入记忆，其他任务保持基线。 */
    @Test
    fun buildersGateMemoryByGenerationModeAndKeepHistoryOrder() {
        // 构造单条标识记忆，验证所有协议与后处理模式下的生成模式门控。
        val memory = MemoryPromptInput(
            items = listOf(
                MemoryPromptItem(
                    ref = MemoryRef(MemoryTable.Character, 1),
                    revision = 3,
                    text = "UNIQUE_MEMORY_BLOCK",
                ),
            ),
            templates = PromptTemplateSnapshot.defaults(),
            scope = MacroScope(),
            maxItems = 6,
            maxTokens = 512,
        )
        // 仅普通回复和重生成在历史消息之前注入记忆，且所有模式均保持世界书状态不变。
        LLMProviderProtocol.entries.forEach { protocol ->
            PromptPostProcessingMode.entries.forEach { processing ->
                PromptGenerationMode.entries.forEach { mode ->
                    val provider = provider(protocol, processing)
                    val singleBase = single(provider, mode)
                    val groupBase = group(provider, mode)
                    val single = single(provider, mode, memory)
                    val group = group(provider, mode, memory)
                    assertEquals(singleBase.worldInfoStateJson, single.worldInfoStateJson)
                    assertEquals(groupBase.worldInfoStateJson, group.worldInfoStateJson)
                    if (mode == PromptGenerationMode.Normal || mode == PromptGenerationMode.Regenerate) {
                        listOf(single.request, group.request).forEach { request ->
                            val text = request.messages.joinToString("\n") { it.content }
                            assertTrue(text.contains("UNIQUE_MEMORY_BLOCK"))
                            assertTrue(
                                text.indexOf("UNIQUE_MEMORY_BLOCK") < text.indexOf("Where is the key?"),
                            )
                        }
                        // 各协议后处理合并消息后，检查器仍须保留可定位的记忆来源正文。
                        listOf(single.inspection, group.inspection).forEach { inspection ->
                            assertTrue(inspection.items.flatMap { it.segments }.any { segment ->
                                segment.sources.any { it.kind == PromptSourceKind.Memory } &&
                                    segment.content.contains("UNIQUE_MEMORY_BLOCK")
                            })
                        }
                    } else {
                        assertEquals(singleBase.request, single.request)
                        assertEquals(groupBase.request, group.request)
                    }
                }
            }
        }
    }

    /** 同一份角色卡保留深度提示及宏，防止新增插槽改变历史相对位置。 */
    private fun character() = Character(
        id = 7,
        name = "Lyra",
        avatar = "",
        characterTags = "[]",
        description = "{{char}} keeps a notebook.",
        personality = "Patient",
        scenario = "An old library",
        firstMessages = "Welcome.",
        examplesOfDialogue = "<START>\nAlex: Hello\nLyra: Welcome back.",
        postHistoryInstructions = "Reply in the scene.",
        depthPromptPrompt = "Keep the key safe.",
        depthPromptDepth = 1,
    )

    private fun provider(protocol: LLMProviderProtocol, mode: PromptPostProcessingMode) =
        LLMProvider(
            id = 3,
            name = "Fixture",
            providerType = LLMProviderType.Custom,
            protocol = protocol,
            baseUrl = "https://example.invalid",
            model = "fixture-model",
            contextTokens = 4096,
            maxTokens = 256,
            promptPostProcessingMode = mode.ordinal,
            createTime = 1,
            updateTime = 1,
        )

    /** 单聊基线固定摘要、历史、尾部任务与当前输入的实际构建行为。 */
    private fun single(
        provider: LLMProvider,
        mode: PromptGenerationMode,
        memory: MemoryPromptInput? = null,
    ): PromptBuildResult {
        // 装配与线上一致的单聊构建器依赖链。
        val history = FormattedHistoryBuilder()
        val builder = ChatPromptBuilder(
            mPreferences = PromptTemplateSnapshot.defaults(),
            mMacroResolver = PromptMacroResolver(history),
            mHistoryBuilder = history,
            mWorldBookActivator = WorldBookActivator(),
            mRequestFinalizer = mFinalizer,
        )
        // 重生成历史本身由调用方排除原目标，此处固定同一已选择的历史快照。
        return builder.buildWithMetadata(
            PromptBuildContext(
                normalMemory = memory,
                userName = "Alex",
                userDescription = "A visitor",
                character = character(),
                session = ChatSession(11, 7, 1, 1, "[]", "Library", "Observe the weather."),
                summary = "The door was locked.",
                messages = listOf(
                    ChatMessage(21, 11, 1, ChatMessage.Source.User, "  Where is the key?  "),
                    ChatMessage(
                        22,
                        11,
                        2,
                        ChatMessage.Source.Char,
                        "<think>Inspect</think>On the desk.",
                    ),
                ),
                currentUserMessage = if (mode == PromptGenerationMode.Normal) {
                    "I look closer."
                } else {
                    null
                },
                candidateLorebookEntries = emptyList(),
                provider = provider,
                maxContextTokens = provider.contextTokens,
                maxResponseTokens = provider.maxTokens,
                generationMode = mode,
            ),
        )
    }

    /** 群聊独立构建，避免用单聊产物掩盖发言者前缀与群聊尾部规则。 */
    private fun group(
        provider: LLMProvider,
        mode: PromptGenerationMode,
        memory: MemoryPromptInput? = null,
    ): GroupChatPromptBuildResult {
        // 使用独立群聊构建器与固定多角色上下文执行完整元数据构建。
        val builder = GroupChatPromptBuilder(
            mPreferences = PromptTemplateSnapshot.defaults(),
            mRequestFinalizer = mFinalizer,
        )
        // 返回包含请求体、检查器与世界书状态的群聊基线结果。
        return builder.buildWithMetadata(
            GroupChatPromptContext(
                normalMemory = memory,
                session = GroupChatSession(11, "Library", 1, 1, "Alex", "A visitor"),
                members = listOf(GroupChatMemberData(GroupChatMember(11, 7, 0), character())),
                speaker = character(),
                provider = provider,
                summary = "The door was locked.",
                messages = listOf(
                    GroupChatMessage(
                        id = 21,
                        sessionId = 11,
                        createTime = 1,
                        source = GroupChatMessage.Source.User,
                        content = "Where is the key?",
                        speakerNameSnapshot = "Alex",
                    ),
                    GroupChatMessage(
                        id = 22,
                        sessionId = 11,
                        createTime = 2,
                        source = GroupChatMessage.Source.Character,
                        content = "On the desk.",
                        speakerCharacterId = 7,
                        speakerNameSnapshot = "Lyra",
                    ),
                ),
                generationMode = GroupChatGenerationMode.valueOf(mode.name),
            ),
        )
    }
}
