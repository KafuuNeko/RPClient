package me.kafuuneko.rpclient.libs.media

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.room.repository.FileRepository

/**
 * 面向角色选择器与摘要卡片的按需头像缩略图加载器。
 *
 * 核心设计：
 * - 视口驱动解码：仅对当前弹窗可见角色及主表单已选角色执行下采样解码，避免海量角色卡一次载入全部原图。
 * - 有界内存缓存：采用 8 MiB 字节预算的 LRU 位图缓存，弹窗反复开关或上下滚动时直接命中缓存。
 * - 自动取消淘汰：列表滚动离开视口或弹窗关闭时取消不再需要的后台解码任务，防止内存与 IO 浪费。
 *
 * @param mFileRepository 提供租约保护的下采样解码能力
 * @param mScope 绑定 ViewModel 生命周期的主线程协程作用域
 * @param mOnAvatarsUpdated 发布已加载的可见及常驻头像快照，包含离开视口时的移除
 */
class CharacterAvatarLoader(
    private val mFileRepository: FileRepository,
    private val mScope: CoroutineScope,
    private val mOnAvatarsUpdated: (Map<Long, ImageBitmap>) -> Unit,
) {
    private val mCache = AvatarBitmapCache(MAX_AVATAR_CACHE_BYTES)
    private var mAvatarUuids: Map<Long, String> = emptyMap()
    private var mVisibleIds: Set<Long> = emptySet()
    private var mVisibleSizePx: Int = DEFAULT_THUMBNAIL_SIZE_PX
    private var mPinnedIds: Set<Long> = emptySet()
    private var mPinnedSizePx: Int = DEFAULT_THUMBNAIL_SIZE_PX
    private val mLoaded = mutableMapOf<Long, LoadedAvatar>()
    private val mJobs = mutableMapOf<Long, Job>()
    private val mJobKeys = mutableMapOf<Long, AvatarCacheKey>()
    private val mJobTokens = mutableMapOf<Long, Any>()
    private var mPublishedAvatars: Map<Long, ImageBitmap> = emptyMap()

    /**
     * 注册或刷新角色与头像文件 UUID 的映射，并清理已删除或已更换头像的旧状态。
     *
     * @param avatarUuids 角色 ID 到头像文件 UUID 的映射
     */
    fun registerCharacters(avatarUuids: Map<Long, String>) {
        mAvatarUuids = avatarUuids
        // 剔除已移除或头像 UUID 已变更的已加载项与进行中任务
        val staleIds = mLoaded.keys.filter { id ->
            val currentUuid = avatarUuids[id].orEmpty()
            currentUuid.isBlank() || mLoaded[id]?.key?.avatarUuid != currentUuid
        }
        staleIds.forEach { id ->
            mLoaded.remove(id)
            cancelJob(id)
        }
        val staleJobIds = mJobKeys.keys.filter { id ->
            val currentUuid = avatarUuids[id].orEmpty()
            currentUuid.isBlank() || mJobKeys[id]?.avatarUuid != currentUuid
        }
        staleJobIds.forEach(::cancelJob)
        reconcileLoads()
    }

    /**
     * 更新主表单常驻展示的角色 ID（如已选角色摘要卡），确保关闭弹窗后仍保留头像。
     *
     * @param characterIds 需要常驻展示头像的角色 ID 集合
     * @param targetSizePx 目标缩略图边长像素
     */
    fun updatePinnedCharacters(
        characterIds: Set<Long>,
        targetSizePx: Int = DEFAULT_THUMBNAIL_SIZE_PX,
    ) {
        mPinnedIds = characterIds
        mPinnedSizePx = targetSizePx.coerceIn(1, MAX_THUMBNAIL_DIMENSION)
        reconcileLoads()
    }

    /**
     * 更新选择器弹窗当前可见的角色窗口并触发按需下采样加载。
     *
     * @param characterIds 当前视口内可见的角色 ID 集合
     * @param targetSizePx 列表头像控件对应的目标像素边长
     */
    fun updateVisibleCharacters(characterIds: Set<Long>, targetSizePx: Int) {
        if (targetSizePx !in 1..MAX_THUMBNAIL_DIMENSION) return
        mVisibleIds = characterIds
        mVisibleSizePx = targetSizePx
        reconcileLoads()
    }

    /** 清空选择器弹窗可见窗口并取消仅服务于弹窗的未完成加载任务。 */
    fun clearVisibleCharacters() {
        if (mVisibleIds.isEmpty()) return
        mVisibleIds = emptySet()
        reconcileLoads()
    }

    /** 释放所有进行中的协程任务与内存缓存，应在 ViewModel 销毁时调用。 */
    fun clear() {
        // 先使令牌失效，取消触发的收尾不能再修改正在清理的任务表。
        val jobs = mJobs.values.toList()
        mJobTokens.clear()
        mJobs.clear()
        mJobKeys.clear()
        jobs.forEach(Job::cancel)
        mLoaded.clear()
        mVisibleIds = emptySet()
        mPinnedIds = emptySet()
        mAvatarUuids = emptyMap()
        mPublishedAvatars = emptyMap()
        mCache.clear()
    }

    /** 对比当前常驻与可见集合，取消越界任务、即时命中缓存并为缺失项启动后台解码。 */
    private fun reconcileLoads() {
        val desired = LinkedHashMap<Long, Int>()
        mPinnedIds.forEach { id -> desired[id] = mPinnedSizePx }
        mVisibleIds.forEach { id ->
            val existing = desired[id]
            desired[id] = if (existing == null) mVisibleSizePx else maxOf(existing, mVisibleSizePx)
        }
        // 取消不再处于视口且非常驻角色的解码任务
        val jobsToCancel = mJobs.keys.filter { id -> id !in desired }
        jobsToCancel.forEach(::cancelJob)
        // 移除不再活跃的引用，底层位图仍保留在 LRU 缓存中供快速复用
        mLoaded.keys.retainAll(desired.keys)

        desired.forEach { (characterId, sizePx) ->
            val uuid = mAvatarUuids[characterId].orEmpty()
            if (uuid.isBlank()) {
                mLoaded.remove(characterId)
                cancelJob(characterId)
                return@forEach
            }
            val key = AvatarCacheKey(uuid, sizePx, sizePx)
            if (mLoaded[characterId]?.key == key) return@forEach
            // 优先同步复用 LRU 缓存中的缩略图
            mCache.get(key)?.let { cached ->
                cancelJob(characterId)
                mLoaded[characterId] = LoadedAvatar(key, cached.asImageBitmap())
                return@forEach
            }
            // 同尺寸任务已在执行时直接等待结果
            if (mJobKeys[characterId] == key && mJobs[characterId]?.isActive == true) {
                return@forEach
            }
            launchDecodeJob(characterId = characterId, key = key)
        }
        publishAvatars()
    }

    /** 在 IO 线程下采样解码单个角色头像，完成后校验时效性并写入缓存。 */
    private fun launchDecodeJob(characterId: Long, key: AvatarCacheKey) {
        cancelJob(characterId)
        val token = Any()
        mJobTokens[characterId] = token
        mJobKeys[characterId] = key
        mJobs[characterId] = mScope.launch {
            try {
                // 在 IO 线程按目标尺寸下采样解码，避免读取完整原图占用内存
                val bitmap = withContext(Dispatchers.IO) {
                    mFileRepository.loadSampledBitmap(
                        uuid = key.avatarUuid,
                        requestedWidthPx = key.widthPx,
                        requestedHeightPx = key.heightPx,
                    )
                } ?: return@launch
                // 仅当任务仍有效且角色仍处于可见或常驻集合时更新状态
                val isCurrent = mJobTokens[characterId] === token &&
                    mAvatarUuids[characterId] == key.avatarUuid &&
                    (characterId in mVisibleIds || characterId in mPinnedIds)
                if (!isCurrent) return@launch
                mCache.put(key, bitmap)
                mLoaded[characterId] = LoadedAvatar(key, bitmap.asImageBitmap())
                publishAvatars()
            } finally {
                if (mJobTokens[characterId] === token) {
                    mJobTokens.remove(characterId)
                    mJobKeys.remove(characterId)
                    mJobs.remove(characterId)
                }
            }
        }
    }

    /** 仅发布本次可见及常驻角色的位图，已离屏的位图只由有界缓存持有。 */
    private fun publishAvatars() {
        val avatars = mLoaded.mapValues { (_, loaded) -> loaded.image }
        if (avatars == mPublishedAvatars) return
        mPublishedAvatars = avatars
        mOnAvatarsUpdated(avatars)
    }

    /** 取消并清理单个角色的解码任务状态。 */
    private fun cancelJob(characterId: Long) {
        mJobTokens.remove(characterId)
        mJobKeys.remove(characterId)
        mJobs.remove(characterId)?.cancel()
    }

    private data class AvatarCacheKey(
        val avatarUuid: String,
        val widthPx: Int,
        val heightPx: Int,
    )

    private data class LoadedAvatar(
        val key: AvatarCacheKey,
        val image: ImageBitmap,
    )

    /** 按字节容量上限淘汰最久未用缩略图的 LRU 位图缓存。 */
    private class AvatarBitmapCache(private val mMaxBytes: Long) {
        private val mEntries = LinkedHashMap<AvatarCacheKey, Bitmap>(16, 0.75f, true)
        private var mSizeBytes: Long = 0L

        /** 读取带指定尺寸的位图并更新其最近访问顺序。 */
        fun get(key: AvatarCacheKey): Bitmap? = mEntries[key]

        /** 缓存解码结果，并按字节预算淘汰最久未访问的位图。 */
        fun put(key: AvatarCacheKey, bitmap: Bitmap) {
            val bitmapBytes = bitmap.byteCount.toLong()
            if (bitmapBytes > mMaxBytes) return
            mEntries.put(key, bitmap)?.let { old -> mSizeBytes -= old.byteCount.toLong() }
            mSizeBytes += bitmapBytes
            val iterator = mEntries.entries.iterator()
            // 腾出空间时优先淘汰最久未访问的缩略图。
            while (mSizeBytes > mMaxBytes && iterator.hasNext()) {
                val eldest = iterator.next()
                mSizeBytes -= eldest.value.byteCount.toLong()
                iterator.remove()
            }
        }

        /** 释放缓存持有的全部位图引用。 */
        fun clear() {
            mEntries.clear()
            mSizeBytes = 0L
        }
    }

    companion object {
        private const val MAX_AVATAR_CACHE_BYTES = 8L * 1024L * 1024L
        private const val MAX_THUMBNAIL_DIMENSION = 4096
        private const val DEFAULT_THUMBNAIL_SIZE_PX = 144
    }
}
