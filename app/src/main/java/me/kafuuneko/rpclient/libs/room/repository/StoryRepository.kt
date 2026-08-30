package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.withTransaction
import me.kafuuneko.rpclient.libs.defaults.DefaultNames
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.dao.getEntriesByIdsChunked
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.Story
import me.kafuuneko.rpclient.libs.room.entity.StoryChapter
import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter
import me.kafuuneko.rpclient.libs.room.entity.StoryLorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.StoryVolume
import me.kafuuneko.rpclient.libs.room.model.StoryChapterOverview
import me.kafuuneko.rpclient.libs.room.model.StoryOverview
import me.kafuuneko.rpclient.libs.story.storyTextHash

/** Story 角色关联的领域聚合数据。 */
data class StoryCharacterCandidate(
    /** 当前角色、成员或世界书条目的关联数据。 */
    val relation: StoryCharacter,
    /** 当前状态或操作关联的角色数据。 */
    val character: Character
)

/** Story 世界书条目关联与原始条目的领域聚合数据。 */
data class StoryLorebookEntryCandidate(
    /** 当前角色、成员或世界书条目的关联数据。 */
    val relation: StoryLorebookEntry,
    /** 当前流程正在处理的单个条目。 */
    val entry: LorebookEntry
)

/** 用户保存的一条 Story 角色关联配置。 */
data class StoryCharacterSelection(
    /** 关联角色的唯一 ID。 */
    val characterId: Long,
    /** 当前角色或条目的激活模式。 */
    val activationMode: Int
)

/** 用户保存的一条 Story 世界书关联配置。 */
data class StoryLorebookEntrySelection(
    /** 关联世界书条目的唯一 ID。 */
    val lorebookEntryId: Long
)

/** 可用于生成提交、撤销和恢复的 Story 条目级世界书状态快照。 */
data class StoryLorebookRuntimeState(
    /** 关联世界书条目的唯一 ID。 */
    val lorebookEntryId: Long,
    /** 故事世界书条目最近一次被直接命中的生成轮次。 */
    val activatedAtStep: Int? = null,
    /** 故事世界书条目粘滞激活保持到的生成轮次。 */
    val stickyUntilStep: Int? = null,
    /** 故事世界书条目冷却结束的生成轮次。 */
    val cooldownUntilStep: Int? = null,
    /** 用于比较世界书时序状态的稳定签名。 */
    val stateSignature: String? = null
)

/** 编辑器初始化所需的 Story、轻量结构和当前完整章节。 */
data class StoryEditorData(
    /** 当前页面展示或编辑的故事数据。 */
    val story: Story,
    /** 当前故事包含的卷结构列表。 */
    val volumes: List<StoryVolume>,
    /** 当前故事或卷包含的章节列表。 */
    val chapters: List<StoryChapterOverview>,
    /** 当前编辑器正在展示的章节数据。 */
    val currentChapter: StoryChapter
)

/** 一次章节正文保存后的两级新 revision。 */
data class StoryChapterWriteResult(
    /** 故事级数据用于并发校验的修订版本。 */
    val storyRevision: Long,
    /** 章节正文用于并发校验的修订版本。 */
    val chapterRevision: Long
)

/** 删除章节后必须立即切换到的相邻章节。 */
data class StoryChapterDeleteResult(
    /** 刚完成删除的章节 ID。 */
    val deletedChapterId: Long,
    /** 删除当前章节后需要切换到的备用章节 ID。 */
    val fallbackChapterId: Long
)

/** 等待通过 Story、章节、原文和世界书快照校验后原子应用的 AI 修改。 */
data class StoryGeneratedEdit(
    /** 当前操作关联的故事 ID。 */
    val storyId: Long,
    /** 当前操作关联的章节 ID。 */
    val chapterId: Long,
    /** 开始编辑时记录的故事修订版本。 */
    val baseStoryRevision: Long,
    /** 开始编辑时记录的章节修订版本。 */
    val baseChapterRevision: Long,
    /** 当前区间的起始位置，包含该位置。 */
    val start: Int,
    /** 当前区间的结束位置，不包含该位置。 */
    val end: Int,
    /** 生成开始时正文的哈希，用于避免覆盖后续编辑。 */
    val originalTextHash: String,
    /** 当前流程计算或执行后的结果。 */
    val result: String,
    /** 本轮扫描后需要持久化的世界书时序状态映射。 */
    val nextWorldInfoStates: List<StoryLorebookRuntimeState>,
    /** 本轮生成完成后应持久化的世界书时序轮次。 */
    val nextWorldInfoGenerationStep: Int? = null
)

/** 已原子应用的章节正文、两级 revision 与 Story 世界书状态。 */
data class StoryAppliedEdit(
    /** 当前对象承载的正文内容。 */
    val content: String,
    /** 故事级数据用于并发校验的修订版本。 */
    val storyRevision: Long,
    /** 章节正文用于并发校验的修订版本。 */
    val chapterRevision: Long,
    /** 当前世界书时序推进到的生成轮次。 */
    val worldInfoGenerationStep: Int,
    /** 按条目 ID 保存的世界书时序状态映射。 */
    val worldInfoStates: List<StoryLorebookRuntimeState>
)

/**
 * Story 聚合仓库。
 *
 * 正文以章节为加载和保存边界；分卷只负责结构分组。所有会改变 Story 聚合的写入都在事务中
 * 递增 [Story.revision]，AI 提交因此能拒绝基于旧设置、旧结构或旧世界书时序构建的结果。
 */
class StoryRepository(private val mAppDatabase: AppDatabase) {
    private val mStoryDao = mAppDatabase.getStoryDao()
    private val mStoryVolumeDao = mAppDatabase.getStoryVolumeDao()
    private val mStoryChapterDao = mAppDatabase.getStoryChapterDao()
    private val mStoryCharacterDao = mAppDatabase.getStoryCharacterDao()
    private val mStoryLorebookEntryDao = mAppDatabase.getStoryLorebookEntryDao()
    private val mCharacterDao = mAppDatabase.getCharacterDao()
    private val mLorebookEntryDao = mAppDatabase.getLorebookEntryDao()

    suspend fun getStoryOverviews(): List<StoryOverview> = mStoryDao.getStoryOverviews()

    suspend fun getStory(id: Long): Story? = mStoryDao.getStory(id)

    /** 加载轻量大纲，并只读取一个用户将要编辑的完整章节。 */
    suspend fun getStoryEditorData(
        storyId: Long,
        preferredChapterId: Long? = null
    ): StoryEditorData? = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId) ?: return@withTransaction null
        val volumes = mStoryVolumeDao.getByStoryId(storyId)
        val chapters = mStoryChapterDao.getOverviewsByStoryId(storyId)
        val preferred = preferredChapterId
            ?.let { mStoryChapterDao.getById(it) }
            ?.takeIf { it.storyId == storyId }
        val current = preferred ?: mStoryChapterDao.getLatestByStoryId(storyId)
            ?: return@withTransaction null
        StoryEditorData(story, volumes, chapters, current)
    }

    suspend fun getChapter(storyId: Long, chapterId: Long): StoryChapter? {
        return mStoryChapterDao.getById(chapterId)?.takeIf { it.storyId == storyId }
    }

    suspend fun getStoryCharacterCandidates(
        storyId: Long
    ): List<StoryCharacterCandidate> = mAppDatabase.withTransaction {
        mStoryCharacterDao.getByStoryId(storyId).mapNotNull { relation ->
            mCharacterDao.getCharacterById(relation.characterId)?.let { character ->
                StoryCharacterCandidate(relation, character)
            }
        }
    }

    suspend fun getStoryLorebookEntryCandidates(
        storyId: Long
    ): List<StoryLorebookEntryCandidate> = mAppDatabase.withTransaction {
        val relations = mStoryLorebookEntryDao.getByStoryId(storyId)
        if (relations.isEmpty()) return@withTransaction emptyList()
        // 批量读取条目，避免按故事关联关系逐条访问世界书表。
        val entriesById = mLorebookEntryDao
            .getEntriesByIdsChunked(relations.map { it.lorebookEntryId })
            .associateBy { it.id }
        // 按关联表原有顺序重建候选项，避免批量查询改变 Prompt 顺序。
        relations.mapNotNull { relation ->
            entriesById[relation.lorebookEntryId]?.let { entry ->
                StoryLorebookEntryCandidate(relation, entry)
            }
        }
    }

    suspend fun getStoryLorebookRuntimeStates(
        storyId: Long
    ): List<StoryLorebookRuntimeState> {
        return mStoryLorebookEntryDao.getByStoryId(storyId).map { it.toRuntimeState() }
    }

    suspend fun createStory(
        title: String,
        createTime: Long = System.currentTimeMillis()
    ): Long = createStoryWithConfiguration(
        title = title,
        lorebookSelections = emptyList(),
        characterSelections = emptyList(),
        createTime = createTime
    )

    /** 创建 Story 时在同一事务中建立唯一默认章节和引用配置。 */
    suspend fun createStoryWithConfiguration(
        title: String,
        lorebookSelections: List<StoryLorebookEntrySelection>,
        characterSelections: List<StoryCharacterSelection>,
        includeUserPersona: Boolean = false,
        initialChapterTitle: String = DefaultNames.STORY_CHAPTER,
        createTime: Long = System.currentTimeMillis()
    ): Long = mAppDatabase.withTransaction {
        val normalizedTitle = requireTitle(title, "Story title cannot be blank")
        val normalizedChapterTitle = requireTitle(
            initialChapterTitle,
            "Story chapter title cannot be blank"
        )
        val configuration = normalizeAndValidateConfiguration(
            lorebookSelections,
            characterSelections
        )
        val storyId = mStoryDao.insertOrReplace(
            Story(
                title = normalizedTitle,
                includeUserPersona = includeUserPersona,
                createTime = createTime,
                latestTime = createTime
            )
        )
        mStoryChapterDao.insert(
            StoryChapter(
                storyId = storyId,
                title = normalizedChapterTitle,
                sortOrder = 0,
                createTime = createTime,
                latestTime = createTime
            )
        )
        insertStoryCharacters(storyId, configuration.characterSelections)
        insertStoryLorebookEntries(storyId, configuration.lorebookSelections)
        storyId
    }

    /** 修改故事标题并推进修订版本号。 */
    suspend fun renameStory(
        id: Long,
        title: String,
        latestTime: Long = System.currentTimeMillis()
    ): Boolean = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(id) ?: return@withTransaction false
        val normalizedTitle = requireTitle(title, "Story title cannot be blank")
        mStoryDao.renameStory(id, story.revision, normalizedTitle, latestTime) == 1
    }

    /** 创建故事分卷，并放置在分卷列表末尾。 */
    suspend fun createVolume(
        storyId: Long,
        title: String,
        latestTime: Long = System.currentTimeMillis()
    ): Long = mAppDatabase.withTransaction {
        // 校验所属故事存在性
        val story = requireStory(storyId)
        // 插入分卷记录并设置顺序号
        val id = mStoryVolumeDao.insert(
            StoryVolume(
                storyId = storyId,
                title = requireTitle(title, "Story volume title cannot be blank"),
                sortOrder = mStoryVolumeDao.getByStoryId(storyId).size
            )
        )
        // 推进故事整体修订版本与活跃时间
        advanceStory(story, latestTime)
        id
    }

    /** 重命名指定故事分卷。 */
    suspend fun renameVolume(
        storyId: Long,
        volumeId: Long,
        title: String,
        latestTime: Long = System.currentTimeMillis()
    ): Boolean = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId) ?: return@withTransaction false
        val volume = mStoryVolumeDao.getById(volumeId)
            ?.takeIf { it.storyId == storyId }
            ?: return@withTransaction false
        // 更新分卷标题
        check(
            mStoryVolumeDao.rename(
                volume.id,
                storyId,
                requireTitle(title, "Story volume title cannot be blank")
            ) == 1
        )
        // 推进故事整体修订版本与活跃时间
        advanceStory(story, latestTime)
        true
    }

    /** 调整分卷在故事中的相对排列顺序。 */
    suspend fun moveVolume(
        storyId: Long,
        volumeId: Long,
        offset: Int,
        latestTime: Long = System.currentTimeMillis()
    ): Boolean = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId) ?: return@withTransaction false
        val volumes = mStoryVolumeDao.getByStoryId(storyId)
        val from = volumes.indexOfFirst { it.id == volumeId }
        val to = from + offset
        if (from < 0 || to !in volumes.indices) return@withTransaction false
        // 交换两分卷的序号
        check(mStoryVolumeDao.updateSortOrder(volumes[from].id, storyId, to) == 1)
        check(mStoryVolumeDao.updateSortOrder(volumes[to].id, storyId, from) == 1)
        advanceStory(story, latestTime)
        true
    }

    /** 删除分卷只解除结构分组，卷内章节按原顺序追加到未分卷区域。 */
    suspend fun deleteVolume(
        storyId: Long,
        volumeId: Long,
        latestTime: Long = System.currentTimeMillis()
    ): Boolean = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId) ?: return@withTransaction false
        val volume = mStoryVolumeDao.getById(volumeId)
            ?.takeIf { it.storyId == storyId }
            ?: return@withTransaction false
        // 获取未分卷区域已有章节数量
        val ungroupedCount = mStoryChapterDao.getByContainer(storyId, null).size
        // 将原卷内章节依次移出分卷并追加至未分卷末尾
        mStoryChapterDao.getByContainer(storyId, volumeId).forEachIndexed { index, chapter ->
            check(
                mStoryChapterDao.updateLocation(
                    chapter.id,
                    storyId,
                    null,
                    ungroupedCount + index
                ) == 1
            )
        }
        // 删除分卷记录并重排其余分卷
        check(mStoryVolumeDao.deleteById(volume.id, storyId) == 1)
        normalizeVolumeOrder(storyId)
        advanceStory(story, latestTime)
        true
    }

    /** 创建新章节并放置在指定容器末尾。 */
    suspend fun createChapter(
        storyId: Long,
        volumeId: Long?,
        title: String,
        latestTime: Long = System.currentTimeMillis()
    ): Long = mAppDatabase.withTransaction {
        val story = requireStory(storyId)
        validateVolume(storyId, volumeId)
        // 插入章节记录并计算同组末尾序号
        val id = mStoryChapterDao.insert(
            StoryChapter(
                storyId = storyId,
                volumeId = volumeId,
                title = requireTitle(title, "Story chapter title cannot be blank"),
                sortOrder = mStoryChapterDao.getByContainer(storyId, volumeId).size,
                createTime = latestTime,
                latestTime = latestTime
            )
        )
        // 推进故事整体修订版本与活跃时间
        advanceStory(story, latestTime)
        id
    }

    /** 重命名指定章节。 */
    suspend fun renameChapter(
        storyId: Long,
        chapterId: Long,
        title: String,
        latestTime: Long = System.currentTimeMillis()
    ): Boolean = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId) ?: return@withTransaction false
        val chapter = mStoryChapterDao.getById(chapterId)
            ?.takeIf { it.storyId == storyId }
            ?: return@withTransaction false
        // 更新章节标题
        check(
            mStoryChapterDao.rename(
                chapter.id,
                storyId,
                requireTitle(title, "Story chapter title cannot be blank"),
                latestTime
            ) == 1
        )
        // 推进故事整体修订版本与活跃时间
        advanceStory(story, latestTime)
        true
    }

    /** 在同卷或未分卷容器内移动章节相对顺序。 */
    suspend fun moveChapter(
        storyId: Long,
        chapterId: Long,
        offset: Int,
        latestTime: Long = System.currentTimeMillis()
    ): Boolean = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId) ?: return@withTransaction false
        val chapter = mStoryChapterDao.getById(chapterId)
            ?.takeIf { it.storyId == storyId }
            ?: return@withTransaction false
        val siblings = mStoryChapterDao.getByContainer(storyId, chapter.volumeId)
        val from = siblings.indexOfFirst { it.id == chapterId }
        val to = from + offset
        if (from < 0 || to !in siblings.indices) return@withTransaction false
        // 交换同组内两章节的顺序号
        check(mStoryChapterDao.updateSortOrder(siblings[from].id, storyId, to) == 1)
        check(mStoryChapterDao.updateSortOrder(siblings[to].id, storyId, from) == 1)
        advanceStory(story, latestTime)
        true
    }

    /**
     * 按给定主键顺序批量重排同卷或未分卷容器内的全部章节。
     *
     * @param storyId 所属故事 ID
     * @param volumeId 所属分卷 ID，为空时表示未分卷章节
     * @param orderedChapterIds 容器内全部章节的最终主键顺序
     * @param latestTime 本次结构变更时间
     * @return 容器快照有效且顺序成功提交时返回 true
     */
    suspend fun reorderChapters(
        storyId: Long,
        volumeId: Long?,
        orderedChapterIds: List<Long>,
        latestTime: Long = System.currentTimeMillis()
    ): Boolean = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId) ?: return@withTransaction false
        val siblings = mStoryChapterDao.getByContainer(storyId, volumeId)
        // 完整校验容器成员，避免拖动期间的并发结构变更覆盖章节归属或新增数据
        if (orderedChapterIds.distinct().size != orderedChapterIds.size) {
            return@withTransaction false
        }
        if (siblings.map { it.id }.toSet() != orderedChapterIds.toSet()) {
            return@withTransaction false
        }
        // 仅写入实际变化的序号，并以一次 Story 修订推进提交整次拖动结果
        val chapterById = siblings.associateBy { it.id }
        orderedChapterIds.forEachIndexed { index, chapterId ->
            val chapter = chapterById.getValue(chapterId)
            if (chapter.sortOrder != index) {
                check(mStoryChapterDao.updateSortOrder(chapterId, storyId, index) == 1)
            }
        }
        advanceStory(story, latestTime)
        true
    }

    /**
     * 批量重排故事内的全部章节（支持跨分卷移动与全书重排）。
     *
     * - 快照必须无重复地覆盖当前故事全部章节。
     * - 分卷必须属于当前故事，每个容器的排序值必须从零开始连续。
     * - 无实际位置变化时不推进故事修订号。
     *
     * @param storyId 所属故事 ID
     * @param placements 全部章节的最新归属与排序列表
     * @param latestTime 本次结构变更时间
     * @return 校验通过且事务成功提交时返回 true
     */
    suspend fun reorderAllChapters(
        storyId: Long,
        placements: List<StoryChapterPlacement>,
        latestTime: Long = System.currentTimeMillis()
    ): Boolean = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId) ?: return@withTransaction false
        val allChapters = mStoryChapterDao.getByStoryId(storyId)
        val existingIds = allChapters.map { it.id }.toSet()
        val availableVolumeIds = mStoryVolumeDao.getByStoryId(storyId).map { it.id }.toSet()
        // 完整快照必须精确覆盖现有章节，并且只能引用当前故事的分卷。
        if (!placements.isValidChapterPlacementSnapshot(existingIds, availableVolumeIds)) {
            return@withTransaction false
        }
        val chapterById = allChapters.associateBy { it.id }
        val changedPlacements = placements.filter { placement ->
            val current = chapterById.getValue(placement.chapterId)
            current.volumeId != placement.volumeId || current.sortOrder != placement.sortOrder
        }
        if (changedPlacements.isEmpty()) return@withTransaction true
        // 只写入发生变化的位置，全部成功后再推进故事修订号。
        changedPlacements.forEach { placement ->
            check(
                mStoryChapterDao.updateLocation(
                    id = placement.chapterId,
                    storyId = storyId,
                    volumeId = placement.volumeId,
                    sortOrder = placement.sortOrder
                ) == 1
            )
        }
        advanceStory(story, latestTime)
        true
    }

    /** 将章节移动到指定分卷或未分卷容器中。 */
    suspend fun moveChapterToVolume(
        storyId: Long,
        chapterId: Long,
        volumeId: Long?,
        latestTime: Long = System.currentTimeMillis()
    ): Boolean = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId) ?: return@withTransaction false
        val chapter = mStoryChapterDao.getById(chapterId)
            ?.takeIf { it.storyId == storyId }
            ?: return@withTransaction false
        validateVolume(storyId, volumeId)
        if (chapter.volumeId == volumeId) return@withTransaction true
        // 更新章节所属分卷并放置在目标分卷末尾
        val targetOrder = mStoryChapterDao.getByContainer(storyId, volumeId).size
        check(mStoryChapterDao.updateLocation(chapterId, storyId, volumeId, targetOrder) == 1)
        // 整理原容器内章节序号
        normalizeChapterOrder(storyId, chapter.volumeId)
        advanceStory(story, latestTime)
        true
    }

    /** 删除指定章节，故事至少需保留一个章节。 */
    suspend fun deleteChapter(
        storyId: Long,
        chapterId: Long,
        latestTime: Long = System.currentTimeMillis()
    ): StoryChapterDeleteResult? = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId) ?: return@withTransaction null
        val ordered = mStoryChapterDao.getOverviewsByStoryId(storyId)
        require(ordered.size > 1) { "Story must retain at least one chapter" }
        val index = ordered.indexOfFirst { it.id == chapterId }
        if (index < 0) return@withTransaction null
        val chapter = mStoryChapterDao.getById(chapterId) ?: return@withTransaction null
        // 确定删除后回退激活的相邻章节
        val fallback = ordered.getOrNull(index - 1) ?: ordered[index + 1]
        check(mStoryChapterDao.deleteById(chapterId, storyId) == 1)
        normalizeChapterOrder(storyId, chapter.volumeId)
        advanceStory(story, latestTime)
        StoryChapterDeleteResult(chapterId, fallback.id)
    }

    /** 以同一章节版本原子保存正文和持续续写引导，并推进 Story 聚合版本。 */
    suspend fun updateChapterDraft(
        storyId: Long,
        chapterId: Long,
        expectedChapterRevision: Long,
        content: String,
        continuationGuidance: String,
        latestTime: Long = System.currentTimeMillis()
    ): StoryChapterWriteResult? = mAppDatabase.withTransaction {
        val story = mStoryDao.getStory(storyId) ?: return@withTransaction null
        val chapter = mStoryChapterDao.getById(chapterId)
            ?.takeIf { it.storyId == storyId && it.contentRevision == expectedChapterRevision }
            ?: return@withTransaction null
        // 乐观锁原子更新章节可编辑内容与版本号
        if (
            mStoryChapterDao.updateDraft(
                chapter.id,
                storyId,
                expectedChapterRevision,
                content,
                continuationGuidance,
                latestTime
            ) != 1
        ) return@withTransaction null
        // 推进故事整体修订版本与活跃时间
        advanceStory(story, latestTime)
        StoryChapterWriteResult(story.revision + 1L, chapter.contentRevision + 1L)
    }

    /** 校验 Story、章节和世界书快照后，原子提交 AI 结果。 */
    suspend fun applyGeneratedEdit(edit: StoryGeneratedEdit): StoryAppliedEdit? {
        return mAppDatabase.withTransaction {
            // 校验故事与章节两级修订版本、原文哈希及世界书状态快照
            val snapshots = validateGeneratedEdit(edit) ?: return@withTransaction null
            val story = snapshots.first
            val chapter = snapshots.second
            // 替换正文目标区间
            val content = chapter.content.replaceRange(edit.start, edit.end, edit.result)
            val nextStep = edit.nextWorldInfoGenerationStep
                ?: (story.worldInfoGenerationStep + 1)
            // 乐观锁原子更新章节正文与故事生成步数
            check(
                mStoryChapterDao.updateContent(
                    chapter.id,
                    story.id,
                    chapter.contentRevision,
                    content,
                    System.currentTimeMillis()
                ) == 1
            )
            check(
                mStoryDao.updateGenerationState(
                    story.id,
                    story.revision,
                    nextStep,
                    System.currentTimeMillis()
                ) == 1
            )
            // 更新关联世界书条目的时序状态
            val currentRelations = mStoryLorebookEntryDao.getByStoryId(story.id)
            val nextRelations = currentRelations.withRuntimeStates(edit.nextWorldInfoStates)
            updateLorebookRuntimeStates(nextRelations)
            StoryAppliedEdit(
                content = content,
                storyRevision = story.revision + 1L,
                chapterRevision = chapter.contentRevision + 1L,
                worldInfoGenerationStep = nextStep,
                worldInfoStates = nextRelations.map { it.toRuntimeState() }
            )
        }
    }

    /** 会话内撤销当前章节修改，并恢复应用前的 Story 世界书状态。 */
    suspend fun revertGeneratedEdit(
        storyId: Long,
        chapterId: Long,
        expectedStoryRevision: Long,
        expectedChapterRevision: Long,
        start: Int,
        insertedText: String,
        replacedText: String,
        previousWorldInfoStates: List<StoryLorebookRuntimeState>,
        previousWorldInfoGenerationStep: Int
    ): StoryAppliedEdit? = mAppDatabase.withTransaction {
        // 校验故事与章节版本号
        val story = mStoryDao.getStory(storyId)
            ?.takeIf { it.revision == expectedStoryRevision }
            ?: return@withTransaction null
        val chapter = mStoryChapterDao.getById(chapterId)
            ?.takeIf { it.storyId == storyId && it.contentRevision == expectedChapterRevision }
            ?: return@withTransaction null
        // 校验待回滚文本区间有效性与哈希一致性
        val end = start + insertedText.length
        if (start !in 0..end || end > chapter.content.length) return@withTransaction null
        if (storyTextHash(chapter.content.substring(start, end)) != storyTextHash(insertedText)) {
            return@withTransaction null
        }
        val relations = mStoryLorebookEntryDao.getByStoryId(storyId)
        if (!relations.matches(previousWorldInfoStates)) return@withTransaction null
        // 恢复原文正文并写库
        val content = chapter.content.replaceRange(start, end, replacedText)
        check(
            mStoryChapterDao.updateContent(
                chapter.id,
                storyId,
                chapter.contentRevision,
                content,
                System.currentTimeMillis()
            ) == 1
        )
        check(
            mStoryDao.updateGenerationState(
                storyId,
                story.revision,
                previousWorldInfoGenerationStep,
                System.currentTimeMillis()
            ) == 1
        )
        // 恢复应用前的世界书条目时序状态
        val previousRelations = relations.withRuntimeStates(previousWorldInfoStates)
        updateLorebookRuntimeStates(previousRelations)
        StoryAppliedEdit(
            content = content,
            storyRevision = story.revision + 1L,
            chapterRevision = chapter.contentRevision + 1L,
            worldInfoGenerationStep = previousWorldInfoGenerationStep,
            worldInfoStates = previousRelations.map { it.toRuntimeState() }
        )
    }

    /** 保存 Story 级上下文和引用配置，并保留仍启用条目的时序状态。 */
    suspend fun updateStoryConfiguration(
        storyId: Long,
        memory: String,
        summary: String,
        authorNote: String,
        includeUserPersona: Boolean = false,
        lorebookSelections: List<StoryLorebookEntrySelection>,
        characterSelections: List<StoryCharacterSelection>,
        latestTime: Long = System.currentTimeMillis()
    ): Long = mAppDatabase.withTransaction {
        val story = requireStory(storyId)
        val configuration = normalizeAndValidateConfiguration(
            lorebookSelections,
            characterSelections
        )
        // 更新故事基础设定
        check(
            mStoryDao.updateStorySettings(
                id = storyId,
                expectedRevision = story.revision,
                memory = memory,
                summary = summary,
                authorNote = authorNote,
                includeUserPersona = includeUserPersona,
                latestTime = latestTime
            ) == 1
        ) { "Story settings update failed" }
        // 重新写入故事关联角色列表
        val previousEntries = mStoryLorebookEntryDao.getByStoryId(storyId)
            .associateBy { it.lorebookEntryId }
        mStoryCharacterDao.deleteByStoryId(storyId)
        insertStoryCharacters(storyId, configuration.characterSelections)
        // 重新写入故事关联世界书条目并保留原有条目的时序状态
        mStoryLorebookEntryDao.deleteByStoryId(storyId)
        val nextEntries = configuration.lorebookSelections.map { selection ->
            previousEntries[selection.lorebookEntryId] ?: StoryLorebookEntry(
                storyId = storyId,
                lorebookEntryId = selection.lorebookEntryId
            )
        }
        if (nextEntries.isNotEmpty()) mStoryLorebookEntryDao.insertAll(nextEntries)
        story.revision + 1L
    }

    /** 仅在故事与当前章节仍匹配生成快照时保存 Story 级滚动摘要。 */
    suspend fun saveGeneratedSummary(
        storyId: Long,
        chapterId: Long,
        expectedStoryRevision: Long,
        expectedChapterRevision: Long,
        content: String,
        latestTime: Long = System.currentTimeMillis()
    ): Long? = mAppDatabase.withTransaction {
        if (content.isBlank()) return@withTransaction null
        val story = mStoryDao.getStory(storyId)
            ?.takeIf { it.revision == expectedStoryRevision }
            ?: return@withTransaction null
        val chapter = mStoryChapterDao.getById(chapterId)
            ?.takeIf { it.storyId == storyId && it.contentRevision == expectedChapterRevision }
            ?: return@withTransaction null
        if (
            mStoryDao.updateSummary(
                storyId,
                story.revision,
                content,
                latestTime
            ) != 1
        ) return@withTransaction null
        story.revision + 1L
    }

    suspend fun deleteStory(id: Long) {
        mStoryDao.deleteStory(id)
    }

    private suspend fun validateGeneratedEdit(
        edit: StoryGeneratedEdit
    ): Pair<Story, StoryChapter>? {
        val story = mStoryDao.getStory(edit.storyId)
            ?.takeIf { it.revision == edit.baseStoryRevision }
            ?: return null
        val chapter = mStoryChapterDao.getById(edit.chapterId)
            ?.takeIf {
                it.storyId == edit.storyId && it.contentRevision == edit.baseChapterRevision
            }
            ?: return null
        if (edit.start !in 0..edit.end || edit.end > chapter.content.length) return null
        if (
            storyTextHash(chapter.content.substring(edit.start, edit.end)) != edit.originalTextHash
        ) return null
        val relations = mStoryLorebookEntryDao.getByStoryId(edit.storyId)
        if (!relations.matches(edit.nextWorldInfoStates)) return null
        return story to chapter
    }

    private suspend fun normalizeAndValidateConfiguration(
        lorebookSelections: List<StoryLorebookEntrySelection>,
        characterSelections: List<StoryCharacterSelection>
    ): NormalizedStoryConfiguration {
        require(characterSelections.distinctBy { it.characterId }.size == characterSelections.size) {
            "Story character selections must be unique"
        }
        require(
            characterSelections.count {
                it.activationMode == StoryCharacter.ACTIVATION_PRIMARY
            } <= 1
        ) { "Story can only have one primary character" }
        characterSelections.forEach { selection ->
            require(StoryCharacter.isValidActivationMode(selection.activationMode)) {
                "Unsupported character activation mode"
            }
            requireNotNull(mCharacterDao.getCharacterById(selection.characterId)) {
                "Character does not exist"
            }
        }
        require(lorebookSelections.distinctBy { it.lorebookEntryId }.size == lorebookSelections.size) {
            "Story lorebook entry selections must be unique"
        }
        val lorebookEntryIds = lorebookSelections.map { it.lorebookEntryId }
        val existingLorebookEntryIds = if (lorebookEntryIds.isEmpty()) {
            emptySet()
        } else {
            mLorebookEntryDao.getEntriesByIdsChunked(lorebookEntryIds)
                .mapTo(mutableSetOf()) { it.id }
        }
        require(existingLorebookEntryIds.size == lorebookEntryIds.size) {
            "Lorebook entry does not exist"
        }
        return NormalizedStoryConfiguration(lorebookSelections, characterSelections)
    }

    private suspend fun insertStoryCharacters(
        storyId: Long,
        selections: List<StoryCharacterSelection>
    ) {
        if (selections.isEmpty()) return
        mStoryCharacterDao.insertAll(
            selections.mapIndexed { index, selection ->
                StoryCharacter(
                    storyId = storyId,
                    characterId = selection.characterId,
                    sortOrder = index,
                    activationMode = selection.activationMode
                )
            }
        )
    }

    private suspend fun insertStoryLorebookEntries(
        storyId: Long,
        selections: List<StoryLorebookEntrySelection>
    ) {
        if (selections.isEmpty()) return
        mStoryLorebookEntryDao.insertAll(
            selections.map { selection ->
                StoryLorebookEntry(storyId, selection.lorebookEntryId)
            }
        )
    }

    private suspend fun updateLorebookRuntimeStates(entries: List<StoryLorebookEntry>) {
        if (entries.isEmpty()) return
        check(mStoryLorebookEntryDao.updateAll(entries) == entries.size) {
            "Story lorebook runtime state update failed"
        }
    }

    private suspend fun requireStory(storyId: Long): Story {
        return requireNotNull(mStoryDao.getStory(storyId)) { "Story does not exist" }
    }

    private suspend fun validateVolume(storyId: Long, volumeId: Long?) {
        if (volumeId == null) return
        require(mStoryVolumeDao.getById(volumeId)?.storyId == storyId) {
            "Story volume does not belong to the story"
        }
    }

    private suspend fun advanceStory(story: Story, latestTime: Long) {
        check(mStoryDao.advanceRevision(story.id, story.revision, latestTime) == 1) {
            "Story revision update failed"
        }
    }

    private suspend fun normalizeVolumeOrder(storyId: Long) {
        mStoryVolumeDao.getByStoryId(storyId).forEachIndexed { index, volume ->
            if (volume.sortOrder != index) {
                check(mStoryVolumeDao.updateSortOrder(volume.id, storyId, index) == 1)
            }
        }
    }

    private suspend fun normalizeChapterOrder(storyId: Long, volumeId: Long?) {
        mStoryChapterDao.getByContainer(storyId, volumeId).forEachIndexed { index, chapter ->
            if (chapter.sortOrder != index) {
                check(mStoryChapterDao.updateSortOrder(chapter.id, storyId, index) == 1)
            }
        }
    }

    private fun requireTitle(value: String, message: String): String {
        return value.trim().also { require(it.isNotEmpty()) { message } }
    }

    private data class NormalizedStoryConfiguration(
        /** 故事导入或编辑时保存的世界书选择状态。 */
        val lorebookSelections: List<StoryLorebookEntrySelection>,
        /** 故事导入或编辑时保存的角色选择状态。 */
        val characterSelections: List<StoryCharacterSelection>
    )

}

private fun StoryLorebookEntry.toRuntimeState(): StoryLorebookRuntimeState {
    return StoryLorebookRuntimeState(
        lorebookEntryId = lorebookEntryId,
        activatedAtStep = activatedAtStep,
        stickyUntilStep = stickyUntilStep,
        cooldownUntilStep = cooldownUntilStep,
        stateSignature = stateSignature
    )
}

private fun List<StoryLorebookEntry>.matches(
    states: List<StoryLorebookRuntimeState>
): Boolean {
    if (size != states.size) return false
    val stateById = states.associateBy { it.lorebookEntryId }
    return stateById.size == states.size && all { stateById.containsKey(it.lorebookEntryId) }
}

private fun List<StoryLorebookEntry>.withRuntimeStates(
    states: List<StoryLorebookRuntimeState>
): List<StoryLorebookEntry> {
    val stateById = states.associateBy { it.lorebookEntryId }
    return map { relation ->
        val state = requireNotNull(stateById[relation.lorebookEntryId])
        relation.copy(
            activatedAtStep = state.activatedAtStep,
            stickyUntilStep = state.stickyUntilStep,
            cooldownUntilStep = state.cooldownUntilStep,
            stateSignature = state.stateSignature
        )
    }
}

/** 章节归属分卷与排序位置载荷。 */
data class StoryChapterPlacement(
    /** 当前操作关联的章节 ID。 */
    val chapterId: Long,
    /** 当前操作关联的故事卷 ID。 */
    val volumeId: Long?,
    /** 当前对象用于稳定排序的顺序值。 */
    val sortOrder: Int
)

/** 验证完整章节位置快照的成员、分卷归属与容器内连续顺序。 */
private fun List<StoryChapterPlacement>.isValidChapterPlacementSnapshot(
    existingChapterIds: Set<Long>,
    availableVolumeIds: Set<Long>
): Boolean {
    if (size != existingChapterIds.size) return false
    if (map { it.chapterId }.toSet() != existingChapterIds) return false
    if (any { it.volumeId != null && it.volumeId !in availableVolumeIds }) return false
    return groupBy { it.volumeId }.values.all { placements ->
        placements.map { it.sortOrder }.sorted() == placements.indices.toList()
    }
}
