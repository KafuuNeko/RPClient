package me.kafuuneko.rpclient.libs.memory

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.kafuuneko.rpclient.libs.macro.MacroScope
import me.kafuuneko.rpclient.libs.macro.MacroValue
import me.kafuuneko.rpclient.libs.prompt.model.PromptOmissionReason
import me.kafuuneko.rpclient.libs.prompt.model.PromptOmittedItem
import me.kafuuneko.rpclient.libs.prompt.model.PromptSource
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.libs.prompt.preset.PromptTemplateSnapshot
import me.kafuuneko.rpclient.libs.room.repository.MemoryRepository
import me.kafuuneko.rpclient.utils.stripThinkBlocks
import java.text.Normalizer
import java.util.Locale
import kotlin.math.sqrt

/** 事实只展开一次；预算重试复用文本，来源身份只供本地检查器使用。 */
data class MemoryPromptItem(val ref: MemoryRef, val revision: Long, val text: String)

/** 本次请求冻结的参考块格式、候选顺序、独立预算与读取失败诊断。 */
data class MemoryPromptInput(
    val items: List<MemoryPromptItem>,
    val templates: PromptTemplateSnapshot,
    val scope: MacroScope,
    val maxItems: Int,
    val maxTokens: Int,
    val omittedItems: List<PromptOmittedItem> = emptyList(),
)

/**
 * 普通回复的本地只读召回。
 * - SQL 权限过滤后再按词项、重要度和稳定 ID 排序。
 * - 本地读取限时，失败返回检查器诊断，外部协程取消照常传播。
 */
class MemoryRetriever(private val mRepository: MemoryRepository) {
    /**
     * 选择并展开当前角色的通用记忆，不进行提取、维护或模型调用。
     *
     * @param target 当前单聊或群聊
     * @param speakerId 实际发言角色
     * @param personaId 解析后的实际人设
     * @param texts 当前触发正文及最近至多两条可见正文
     * @param templates 本轮冻结的全局预设
     * @param characterName 当前角色名称
     * @param userName 当前人设名称
     * @param groupNames 当前群成员名称，仅用于模板展开
     * @param excludedMessageId 重生成排除的来源
     * @param maxItems 用户配置的条数上限
     * @param maxTokens 用户配置的独立 Token 上限
     * @return 可选参考数据；无权限或无条目时为空，超时及错误只携带检查器诊断
     */
    suspend fun prepare(
        target: MemoryTarget,
        speakerId: Long,
        personaId: Long,
        texts: List<String>,
        templates: PromptTemplateSnapshot,
        characterName: String,
        userName: String,
        groupNames: List<String> = emptyList(),
        excludedMessageId: Long = 0,
        maxItems: Int = 6,
        maxTokens: Int = 512,
    ): MemoryPromptInput? {
        if (maxItems <= 0 || maxTokens <= 0) return null
        // 冻结本轮范围字面量和独立预算，失败诊断不携带异常原文或数据库信息。
        val scope = MacroScope(
            mapOf(
                "char" to MacroValue.Literal(characterName),
                "user" to MacroValue.Literal(userName),
                "group" to MacroValue.Literal(groupNames.joinToString(", ")),
            ),
        )
        val input = MemoryPromptInput(
            items = emptyList(),
            templates = templates,
            scope = scope,
            maxItems = maxItems.coerceAtMost(100),
            maxTokens = maxTokens.coerceAtMost(32768),
        )
        return prepareWithFallback(input) {
            withContext(Dispatchers.IO) {
                // 先复核召回开关，再查询 SQL 权限过滤后的通用候选。
                val info = mRepository.targetInfo(target)
                if (!info.recall || !info.inherit) return@withContext emptyList()
                val candidates = mRepository.inherited(
                    target = target,
                    speakerId = speakerId,
                    personaId = personaId,
                    excludedMessageId = excludedMessageId,
                )
                // 排序和模板展开同属限时读取过程，逐条协作检查取消。
                val coroutine = currentCoroutineContext()
                val items = rank(candidates, texts) { coroutine.ensureActive() }
                    .mapNotNull { entry ->
                        coroutine.ensureActive()
                        val text = MemoryPromptRenderer.renderItem(
                            fact = entry.fields.content.body,
                            snapshot = templates,
                            scope = scope,
                        )
                        text.takeIf(String::isNotBlank)?.let { rendered ->
                            MemoryPromptItem(
                                ref = entry.ref,
                                revision = entry.fields.currentRevision,
                                text = rendered,
                            )
                        }
                    }
                coroutine.ensureActive()
                items
            }
        }
    }

    companion object {
        /**
         * 在普通召回的 250ms 限额内读取，失败时保留无记忆请求及脱敏诊断。
         *
         * @param input 本轮冻结的模板、作用域和预算
         * @param loadItems 授权读取、排序及展开过程
         * @return 可用候选或失败诊断；正常读取但无候选时为空
         */
        internal suspend fun prepareWithFallback(
            input: MemoryPromptInput,
            loadItems: suspend () -> List<MemoryPromptItem>,
        ): MemoryPromptInput? {
            // 只吸收本次限时读取的超时与普通错误，调用方取消必须继续传播。
            val prepared = try {
                withTimeoutOrNull(250) { input.copy(items = loadItems()) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                null
            }
            // 无候选是正常结果，不能误报为读取失败。
            if (prepared != null) {
                return prepared.takeIf { it.items.isNotEmpty() || it.omittedItems.isNotEmpty() }
            }
            return input.copy(
                items = emptyList(),
                omittedItems = input.omittedItems + PromptOmittedItem(
                    source = PromptSource(PromptSourceKind.Memory),
                    tokenCount = 0,
                    reason = PromptOmissionReason.MemoryUnavailable,
                ),
            )
        }

        /**
         * 对授权候选进行确定性排序，无有效词项时仅召回置顶条目。
         *
         * @param entries 已通过作用域与证据校验的候选记忆列表
         * @param texts 参与词项匹配的近期对话正文集合
         * @param checkActive 排序前执行的协作取消检查回调
         * @return 按置顶、长度平滑相关性、重要度与稳定 ID 排好序的候选列表
         */
        fun rank(
            entries: List<MemoryEntry>,
            texts: List<String>,
            checkActive: () -> Unit = {},
        ): List<MemoryEntry> {
            val query = texts.takeLast(3).flatMap { terms(it.stripThinkBlocks()) }.toSet()
            // 去重命中按正文长度平滑，主体命中加权，避免长文或重复词压制精准事实。
            return entries.map { entry ->
                checkActive()
                val docTerms = terms(entry.fields.content.body)
                val bodyHits = docTerms.intersect(query).size
                val subjectHits = terms(entry.fields.subjectKey).intersect(query).size
                val score = (bodyHits + 2.0 * subjectHits) /
                    sqrt(docTerms.size.coerceAtLeast(1).toDouble())
                entry to score
            }
                .filter { (entry, score) -> entry.fields.content.pinned || score > 0 }
                .sortedWith(
                    compareByDescending<Pair<MemoryEntry, Double>> { it.first.fields.content.pinned }
                        .thenByDescending { it.second }
                        .thenByDescending { it.first.fields.content.importance }
                        .thenBy { it.first.ref.id },
                )
                .map { it.first }
        }

        /** 统一 Unicode 与大小写，保留独立中日韩单字，连续片段使用相邻双字。 */
        internal fun terms(text: String): Set<String> {
            // 先执行 NFKC 归一化并转小写，消除全角半角与大小写差异。
            val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
            // 按 CJK 连续段与普通字母数字词切分，CJK 采用 2-gram 窗口，其余过滤单字符噪声。
            return Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]+|[\\p{L}\\p{N}&&[^\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]]+")
                .findAll(normalized)
                .flatMap { match ->
                    val word = match.value
                    val isCjk = word.any { ch ->
                        Character.UnicodeScript.of(ch.code) in setOf(
                            Character.UnicodeScript.HAN,
                            Character.UnicodeScript.HIRAGANA,
                            Character.UnicodeScript.KATAKANA,
                            Character.UnicodeScript.HANGUL,
                        )
                    }
                    if (isCjk) {
                        if (word.length == 1) sequenceOf(word) else word.windowed(2).asSequence()
                    } else {
                        sequenceOf(word).filter { it.length > 1 }
                    }
                }
                .toSet()
        }
    }
}
