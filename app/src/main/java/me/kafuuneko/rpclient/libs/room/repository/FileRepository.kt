package me.kafuuneko.rpclient.libs.room.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.CancellationSignal
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.llm.ImageRequestException
import me.kafuuneko.rpclient.libs.llm.ImageRequestFailure
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.FileEntity
import me.kafuuneko.rpclient.libs.room.model.MessageImagePolicy
import me.kafuuneko.rpclient.libs.room.model.PreparedFile
import me.kafuuneko.rpclient.libs.utils.withBlockingIoCancellation
import me.kafuuneko.rpclient.model.SquareCropSelection
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 文件存储库，提供文件的保存、获取和删除功能。
 *
 * 该存储库将文件保存在应用的私有数据目录下（repository/），
 * 并使用文件的 SHA-256 哈希值作为文件名，以确保相同内容的文件不会被重复保存。
 */
class FileRepository(
    private val mContext: Context,
    private val mAppDatabase: AppDatabase
) {
    private val mFileDao = mAppDatabase.getFileDao()

    /**
     * 私有存储目录，用于存放所有通过该 Repository 保存的文件。
     */
    private val mRepositoryDir: File by lazy {
        mContext.getDir("repository", Context.MODE_PRIVATE)
    }

    /**
     * 从给定的 [Uri] 保存文件。
     *
     * 会自动计算流的 SHA-256 哈希值，生成对应的 UUID，并在数据库中建立映射。
     * 如果相同哈希值的文件已存在，则不会重复保存物理文件。
     *
     * @param uri 要保存的文件的 Uri。
     * @param mimeType 文件的 MIME 类型（可选），如果不提供则尝试从 Uri 解析。
     * @return 保存成功后生成的 UUID。
     * @throws IllegalArgumentException 如果无法打开 Uri 对应的输入流。
     */
    suspend fun saveFile(uri: Uri, mimeType: String? = null): String = withContext(Dispatchers.IO) {
        val resolvedMimeType = mimeType ?: mContext.contentResolver.getType(uri)
        mContext.contentResolver.openInputStream(uri)?.use { inputStream ->
            saveStream(inputStream, resolvedMimeType)
        } ?: throw IllegalArgumentException("Could not read the selected file")
    }

    /**
     * 从给定的物理 [File] 保存文件。
     *
     * 会自动计算流的 SHA-256 哈希值，生成对应的 UUID，并在数据库中建立映射。
     * 如果相同哈希值的文件已存在，则不会重复保存物理文件。
     *
     * @param file 要保存的物理文件。
     * @param mimeType 文件的 MIME 类型（可选）。
     * @return 保存成功后生成的 UUID。
     */
    suspend fun saveFile(file: File, mimeType: String? = null): String =
        withContext(Dispatchers.IO) {
            FileInputStream(file).use { inputStream ->
                saveStream(inputStream, mimeType)
            }
        }

    /**
     * 从输入流保存数据到本地。
     *
     * 边读取数据边计算 SHA-256 哈希值，并将数据先写入临时文件。
     * 计算完成后，如果目标哈希文件不存在，则将临时文件重命名为目标文件。
     * 最后将 UUID 和哈希值的映射存入数据库。
     *
     * @param inputStream 要保存的数据输入流。
     * @param mimeType 文件的 MIME 类型。
     * @return 保存成功后生成的 UUID。
     */
    private suspend fun saveStream(inputStream: InputStream, mimeType: String?): String {
        val prepared = prepareStream(UUID.randomUUID().toString(), inputStream, mimeType, Long.MAX_VALUE)
        try {
            return mutate(listOf(prepared)) { prepared.file.uuid }
        } finally {
            releasePrepared(prepared)
        }
    }

    private val mStagingDir: File by lazy { File(mRepositoryDir, "staging").apply { mkdirs() } }
    private val mState by lazy { mStorageStates.getOrPut(mRepositoryDir.absolutePath) { StorageState() } }
    private val mActiveDrafts get() = mState.activeDrafts
    private val mLeases get() = mState.leases

    /** 将系统选择结果流式复制到草稿；无法读取时不泄露 URI。 */
    suspend fun prepareFile(ownerId: String, uri: Uri, mimeType: String? = null): PreparedFile {
        // 持有已完成结果，覆盖切回调用方调度器时被取消的窗口。
        var completed: PreparedFile? = null
        try {
            return withContext(Dispatchers.IO) {
                val signal = CancellationSignal()
                // 打开云端资源和读取字节分别注册取消动作，避免等 IO 返回才释放资源。
                withBlockingIoCancellation({ signal.cancel() }) {
                    val descriptor = mContext.contentResolver.openAssetFileDescriptor(uri, "r", signal)
                        ?: throw IllegalArgumentException("Could not read the selected image")
                    descriptor.use { assetFileDescriptor ->
                        val input = assetFileDescriptor.createInputStream()
                        input.use {
                            withBlockingIoCancellation({ input.close() }) {
                                prepareStreamInContext(
                                    ownerId = ownerId,
                                    input = input,
                                    mimeType = mimeType ?: mContext.contentResolver.getType(uri),
                                ).also { prepared -> completed = prepared }
                            }
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            completed?.let { releasePrepared(it) }
            throw error
        }
    }

    /** 流式准备原图；取消时主动关闭输入流，正常完成由调用方关闭。 */
    suspend fun prepareStream(
        ownerId: String,
        input: InputStream,
        mimeType: String?,
        maxBytes: Long = MessageImagePolicy.MAX_ORIGINAL_BYTES,
    ): PreparedFile {
        // 持有已完成结果，覆盖切回调用方调度器时被取消的窗口。
        var completed: PreparedFile? = null
        try {
            return withContext(Dispatchers.IO) {
                withBlockingIoCancellation({ input.close() }) {
                    prepareStreamInContext(ownerId, input, mimeType, maxBytes).also { preparedFile -> completed = preparedFile }
                }
            }
        } catch (error: Throwable) {
            completed?.let { releasePrepared(it) }
            throw error
        }
    }

    /** 有界复制并计算原图哈希；调用者负责关闭输入流，失败时清理本次独占暂存。 */
    private suspend fun prepareStreamInContext(
        ownerId: String,
        input: InputStream,
        mimeType: String?,
        maxBytes: Long = MessageImagePolicy.MAX_ORIGINAL_BYTES,
    ): PreparedFile {
        require(ownerId.isNotBlank() && maxBytes > 0)
        val handle = UUID.randomUUID().toString()
        val temporary = File(mStagingDir, handle)
        // 登记正在写入的草稿，防止恢复清理与流式复制竞争。
        mStorageMutex.withLock { mActiveDrafts.add(handle) }
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            withContext(Dispatchers.IO) {
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(count.toLong() <= maxBytes - size) { "The original file exceeds the size limit" }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        size += count
                    }
                    output.fd.sync()
                }
            }
            require(size > 0) { "The image file is empty" }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val prepared = PreparedFile(
                ownerId = ownerId,
                handle = handle,
                file = FileEntity(
                    uuid = handle,
                    hash = hash,
                    mimeType = mimeType,
                ),
                byteCount = size,
            )
            // 元数据最后落盘；没有完整元数据的文件不会被恢复为可提交草稿。
            withContext(Dispatchers.IO) { writePreparedMetadata(prepared) }
            return prepared
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                mStorageMutex.withLock { discardStaging(handle) }
            }
            throw error
        }
    }

    /**
     * 在通用暂存区生成文件，以最终字节建立待提交凭据。
     * - 写入者只生成本次独占文件并返回实际 MIME；失败或取消时统一回收。
     * - 不持文件锁调用写入者，允许其通过仓库保护读取源草稿。
     *
     * @param ownerId 草稿所有者。
     * @param write 将最终内容写入目标文件并返回 MIME 的操作。
     * @return 尚未入库的最终文件凭据。
     */
    suspend fun prepareGenerated(ownerId: String, write: suspend (File) -> String): PreparedFile {
        require(ownerId.isNotBlank()) { "The file owner cannot be empty" }
        val handle = UUID.randomUUID().toString()
        try {
            return withContext(Dispatchers.IO) {
                mStorageMutex.withLock { mActiveDrafts.add(handle) }
                val target = File(mStagingDir, handle)
                val mime = write(target)
                currentCoroutineContext().ensureActive()
                // 最终编码完成后计算哈希，去重和回收均以实际存储字节为准。
                require(target.isFile && target.length() > 0) { "The generated file is empty" }
                val digest = MessageDigest.getInstance("SHA-256")
                target.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                FileOutputStream(target, true).use { output -> output.fd.sync() }
                val hash = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                val prepared = PreparedFile(
                    ownerId = ownerId,
                    handle = handle,
                    file = FileEntity(
                        uuid = handle,
                        hash = hash,
                        mimeType = mime,
                    ),
                    byteCount = target.length(),
                )
                writePreparedMetadata(prepared)
                prepared
            }
        } catch (error: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) {
                mStorageMutex.withLock { discardStaging(handle) }
            }
            throw error
        }
    }

    /** 暂存字节完整落盘后发布凭据；所有入口共享相同恢复格式。 */
    private fun writePreparedMetadata(prepared: PreparedFile) {
        val metadata = Properties().apply {
            setProperty("owner", prepared.ownerId)
            setProperty("hash", prepared.file.hash)
            setProperty("size", prepared.byteCount.toString())
            prepared.file.mimeType?.let { text -> setProperty("mime", text) }
        }
        FileOutputStream(File(mStagingDir, "${prepared.handle}.meta")).use { output ->
            metadata.store(output, null)
            output.fd.sync()
        }
    }

    /** 根据草稿所有者和句柄恢复暂存引用；资源失效时返回 null，要求重新选择。 */
    suspend fun restorePrepared(ownerId: String, handle: String): PreparedFile? =
        withContext(Dispatchers.IO) {
            mStorageMutex.withLock {
                val prepared =
                    readPrepared(handle)?.takeIf { preparedFile -> preparedFile.ownerId == ownerId } ?: return@withLock null
                val file = File(mStagingDir, handle)
                if (!file.isFile || file.length() != prepared.byteCount || mFileDao.getByUuid(handle) != null) {
                    return@withLock null
                }
                mActiveDrafts.add(handle)
                prepared
            }
        }

    /** 放弃草稿只释放其暂存文件，绝不删除已提交的共享原图。 */
    suspend fun releasePrepared(prepared: PreparedFile) =
        withContext(NonCancellable + Dispatchers.IO) {
            mStorageMutex.withLock {
                if (readPrepared(prepared.handle) == prepared) discardStaging(prepared.handle)
            }
        }

    /**
     * 在统一文件锁内提交索引和调用方的消息事务。
     * - 原图先可靠落盘，数据库事务内不复制图片字节。
     * - 事务开始前接受取消；短事务及成功登记不可中断，避免提交成功后误删草稿资源。
     * - 失败后保留暂存，回收只依据真实剩余索引和读取租约。
     */
    internal suspend fun <T> mutate(
        prepared: List<PreparedFile> = emptyList(),
        block: suspend Mutation.() -> T,
    ): T = withContext(Dispatchers.IO) {
        mStorageMutex.withLock {
            require(
                value = prepared.map { preparedFile -> preparedFile.handle }
                .distinct().size == prepared.size,
            ) { "The same draft cannot be committed twice" }
            val mutation = Mutation()
            try {
                // 发布时仍保留 staging 副本，事务失败后可以直接重试。
                prepared.forEach { preparedFile ->
                    require(readPrepared(preparedFile.handle) == preparedFile) { "The image draft is stale or already committed" }
                    publish(preparedFile)
                    mutation.garbage.add(preparedFile.file.hash)
                }
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    val result = mAppDatabase.withTransaction {
                        prepared.forEach { preparedFile ->
                            require(mFileDao.getByUuid(preparedFile.file.uuid) == null) { "The image draft was already committed" }
                            mFileDao.insert(preparedFile.file)
                        }
                        mutation.block()
                    }
                    prepared.forEach { preparedFile -> discardStaging(preparedFile.handle) }
                    result
                }
            } finally {
                withContext(NonCancellable) { mutation.garbage.forEach { text -> collectHash(text) } }
            }
        }
    }

    /**
     * 在固定锁顺序下读取归档快照，保护分页之间的图文及文件不被并发删除。
     * @param block 只读导出操作，文件读取须使用快照入口，不能重新获取文件锁。
     */
    internal suspend fun <T> withReadSnapshot(block: suspend ReadSnapshot.() -> T): T =
        withContext(Dispatchers.IO) {
            mStorageMutex.withLock {
                mAppDatabase.withTransaction { ReadSnapshot().block() }
            }
        }

    /** 文件锁及数据库读取事务已由外层持有；仅供仓库导出消费文件。 */
    internal inner class ReadSnapshot {
        /** 在快照保护中消费文件与索引；hash 用于归档去重，物理缺失以 null 表达。 */
        suspend fun <T> withFile(uuid: String, block: (File?, FileEntity?) -> T): T {
            val entity = mFileDao.getByUuid(uuid)
            val file = entity?.let { File(mRepositoryDir, it.hash).takeIf(File::isFile) }
            return block(file, entity)
        }
    }

    /** 已持文件锁的事务操作；不得从中重新调用公开文件写入入口。 */
    internal inner class Mutation {
        val garbage = mutableSetOf<String>()

        /** 释放附件专属索引，并登记事务结束后可能需要回收的 hash。 */
        suspend fun removeFiles(uuids: List<String>) {
            uuids.distinct().chunked(900).forEach { batch ->
                val files = mFileDao.getByUuids(batch)
                garbage.addAll(files.map { fileEntity -> fileEntity.hash })
                mFileDao.deleteByUuids(batch)
            }
        }

        /** 分支仅创建独立索引，文件字节仍由原来的 hash 共享。 */
        suspend fun copyReference(uuid: String): FileEntity {
            val original = requireNotNull(mFileDao.getByUuid(uuid)) { "Missing image file index" }
            val copy = original.copy(uuid = UUID.randomUUID().toString())
            mFileDao.insert(copy)
            return copy
        }
    }

    /** 为非消息资源复制引用；附件复制由消息仓库在同一事务内调用底层能力。 */
    suspend fun copyFileReference(uuid: String): String = mutate { copyReference(uuid).uuid }

    /** 在文件锁内读取已签发暂存，避免图像验证与释放草稿竞争。 */
    suspend fun <T> withPreparedFile(prepared: PreparedFile, block: suspend (File) -> T): T =
        withContext(Dispatchers.IO) {
            mStorageMutex.withLock {
                require(readPrepared(prepared.handle) == prepared) { "The image draft is stale, select the image again" }
                block(File(mStagingDir, prepared.handle))
            }
        }

    /** 持租约读取原图；使用结束或协程取消后释放保护并尝试回收。 */
    suspend fun <T> withFileLease(uuid: String, block: suspend (File) -> T): T =
        readWithLease(uuid, { throw ImageRequestException(ImageRequestFailure.Missing) }, block)

    /** 缺失判断与租约登记在同一锁内完成，兼容头像返回 null 和发送请求明确失败两种语义。 */
    private suspend fun <T> readWithLease(
        uuid: String,
        onMissing: () -> T,
        block: suspend (File) -> T,
    ): T = withContext(Dispatchers.IO) {
        // 读取索引和登记租约必须与删除共用文件锁。
        val entity = mStorageMutex.withLock {
            val entity = mFileDao.getByUuid(uuid) ?: return@withLock null
            if (!File(mRepositoryDir, entity.hash).isFile) return@withLock null
            mLeases[entity.hash] = mLeases.getOrDefault(key = entity.hash, defaultValue = 0) + 1
            entity
        } ?: return@withContext onMissing()
        try {
            block(File(mRepositoryDir, entity.hash))
        } finally {
            withContext(NonCancellable) {
                mStorageMutex.withLock {
                    val remaining = mLeases.getValue(entity.hash) - 1
                    if (remaining == 0) mLeases.remove(entity.hash) else mLeases[entity.hash] =
                        remaining
                    collectHash(entity.hash)
                }
            }
        }
    }

    /** 启动及恢复时回收孤立 hash 和过期草稿；近期草稿保留一天供状态恢复，不删头像索引。 */
    suspend fun cleanupAbandonedFiles(now: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {
            mStorageMutex.withLock {
                // 已有文件索引全部视为有效所有权，不能按“没有附件关系”删除头像。
                mRepositoryDir.listFiles()?.filter { file -> file.name.matches(Regex("[0-9a-f]{64}")) }
                    ?.forEach { file -> collectHash(file.name) }
                mStagingDir.listFiles()?.forEach { file ->
                    val handle = file.name.removeSuffix(".meta")
                    if (handle !in mActiveDrafts && now - file.lastModified() > STAGING_RETENTION_MILLIS) {
                        discardStaging(handle)
                    }
                }
                // 旧版中断保存留下的临时文件没有索引，也没有可恢复草稿凭据。
                mRepositoryDir.listFiles()
                    ?.filter { file -> file.name.startsWith("temp_") || file.name.endsWith(".publishing") }
                    ?.forEach { file -> file.delete() }
            }
        }

    /** 校验句柄格式，防止恢复状态将任意路径作为私有草稿读取。 */
    private fun readPrepared(handle: String): PreparedFile? {
        if (!handle.matches(Regex("[0-9a-f-]{36}"))) return null
        val metadata = File(mStagingDir, "$handle.meta")
        if (!metadata.isFile) return null
        return runCatching {
            val values = Properties().apply { metadata.inputStream().use { fileInputStream -> load(fileInputStream) } }
            require(values.getProperty("hash").matches(Regex("[0-9a-f]{64}")))
            require(value = values.getProperty("owner").isNotBlank() && values.getProperty("size").toLong() > 0)
            PreparedFile(
                ownerId = values.getProperty("owner"),
                handle = handle,
                file = FileEntity(
                    uuid = handle,
                    hash = values.getProperty("hash"),
                    mimeType = values.getProperty("mime"),
                ),
                byteCount = values.getProperty("size").toLong(),
            )
        }.getOrNull()
    }

    /** 复制到同目录临时文件并同步落盘，检查原图哈希和最终原子重命名的结果。 */
    private suspend fun publish(prepared: PreparedFile) {
        val source = File(mStagingDir, prepared.handle)
        require(source.isFile && source.length() == prepared.byteCount) { "The image draft file is missing or damaged" }
        val target = File(mRepositoryDir, prepared.file.hash)
        val temporary = File(mRepositoryDir, "${prepared.handle}.publishing")
        try {
            // 即使已有相同 hash，也重新校验暂存内容，避免把损坏草稿绑定到其他原图。
            val digest = MessageDigest.getInstance("SHA-256")
            source.inputStream().use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            require(digest.digest().joinToString("") { "%02x".format(it) } == prepared.file.hash) {
                "Image draft verification failed"
            }
            if (!target.exists()) check(temporary.renameTo(target)) { "Could not commit the original image file" }
            else check(target.isFile && target.length() == prepared.byteCount) { "The existing original image file is damaged" }
        } finally {
            temporary.delete()
        }
    }

    /** 只回收数据库和所有读取者都已释放的物理资源。 */
    private suspend fun collectHash(hash: String) {
        if (mLeases.getOrDefault(key = hash, defaultValue = 0) == 0 && mFileDao.countByHash(hash) == 0) {
            File(mRepositoryDir, hash).delete()
        }
    }

    /** 删除操作独占的暂存字节及元数据；失败残留由恢复清理再次处理。 */
    private fun discardStaging(handle: String) {
        File(mStagingDir, handle).delete()
        File(mStagingDir, "$handle.meta").delete()
        mActiveDrafts.remove(handle)
    }

    /**
     * 根据 UUID 获取对应的文件实体记录（包含哈希值和 MIME 类型等信息）。
     *
     * @param uuid 文件的唯一标识符。
     * @return 对应的 [FileEntity] 对象，如果记录不存在则返回 null。
     */
    suspend fun getFileEntity(uuid: String): FileEntity? = withContext(Dispatchers.IO) {
        mFileDao.getByUuid(uuid)
    }

    /**
     * 根据 UUID 获取对应的物理文件对象。
     *
     * @param uuid 文件的唯一标识符。
     * @return 对应的物理 [File] 对象，如果文件或记录不存在则返回 null。
     */
    suspend fun getFile(uuid: String): File? = withContext(Dispatchers.IO) {
        val entity = mFileDao.getByUuid(uuid) ?: return@withContext null
        val file = File(mRepositoryDir, entity.hash)
        if (file.exists()) file else null
    }

    /**
     * 解码供界面显示的头像，并限制输出尺寸以避免将原图完整载入内存。
     */
    suspend fun loadAvatarBitmap(uuid: String): Bitmap? = loadSampledBitmap(
        uuid = uuid,
        requestedWidthPx = AVATAR_DECODE_DIMENSION,
        requestedHeightPx = AVATAR_DECODE_DIMENSION,
    )

    /**
     * 解码供交互裁剪使用的图片，并在进入 UI 前应用 EXIF 方向。
     *
     * 解码长边受到限制，避免超大相册图片在裁剪页造成不必要的内存压力。
     */
    suspend fun loadBitmapForCrop(
        uri: Uri,
        maxDimensionPx: Int = MAX_CROP_SOURCE_DIMENSION,
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (maxDimensionPx !in 1..MAX_THUMBNAIL_DIMENSION) return@withContext null
        // 预先读取原始图片宽高边界
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsInput = mContext.contentResolver.openInputStream(uri) ?: return@withContext null
        boundsInput.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        val sourceWidth = bounds.outWidth.takeIf { value -> value > 0 } ?: return@withContext null
        val sourceHeight = bounds.outHeight.takeIf { value -> value > 0 } ?: return@withContext null
        // 计算长边下采样采样率
        val sampleSize = calculateLongEdgeSampleSize(sourceWidth, sourceHeight, maxDimensionPx)
        // 采样解码为 ARGB_8888 格式
        val decoded = mContext.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(
                input,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        } ?: return@withContext null
        // 读取并应用 EXIF 旋转与翻转
        applyExifOrientation(decoded, readExifOrientation(uri))
    }

    /**
     * 将选区生成固定尺寸的正方形头像并保存到文件仓库。
     *
     * 支持旋转与水平镜像变换；透明图片使用 PNG，普通照片使用高质量 JPEG。
     */
    suspend fun saveSquareCrop(
        bitmap: Bitmap,
        selection: SquareCropSelection,
        outputSizePx: Int = AVATAR_OUTPUT_DIMENSION,
    ): String = withContext(Dispatchers.IO) {
        require(outputSizePx in 1..MAX_THUMBNAIL_DIMENSION)
        // 构建旋转与镜像矩阵
        val matrix = Matrix().apply {
            if (selection.isFlippedHorizontal) postScale(-1f, 1f)
            if (selection.rotationDegrees != 0) postRotate(selection.rotationDegrees.toFloat())
        }
        val transformed = if (matrix.isIdentity) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        // 计算裁剪框尺寸与像素坐标
        val shortEdge = minOf(transformed.width, transformed.height)
        val cropSize = (shortEdge * selection.sizeFractionOfShortEdge)
            .toInt()
            .coerceIn(minimumValue = 1, maximumValue = shortEdge)
        val centerX = selection.centerX.coerceIn(minimumValue = 0f, maximumValue = 1f) * transformed.width
        val centerY = selection.centerY.coerceIn(minimumValue = 0f, maximumValue = 1f) * transformed.height
        val cropLeft = (centerX - cropSize / 2f)
            .toInt()
            .coerceIn(minimumValue = 0, maximumValue = transformed.width - cropSize)
        val cropTop = (centerY - cropSize / 2f)
            .toInt()
            .coerceIn(minimumValue = 0, maximumValue = transformed.height - cropSize)
        // 截取正方形区域
        val cropped = Bitmap.createBitmap(transformed, cropLeft, cropTop, cropSize, cropSize)
        // 等比缩放至目标输出分辨率
        val output = if (cropped.width == outputSizePx) {
            cropped
        } else {
            cropped.scale(width = outputSizePx, height = outputSizePx, filter = true)
        }
        // 根据透明度选择 PNG 或高质量 JPEG 压缩
        val hasAlpha = transformed.hasAlpha()
        val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val mimeType = if (hasAlpha) "image/png" else "image/jpeg"
        try {
            ByteArrayOutputStream().use { bytes ->
                check(output.compress(format, AVATAR_JPEG_QUALITY, bytes))
                ByteArrayInputStream(bytes.toByteArray()).use { byteArrayInputStream -> saveStream(
                    inputStream = byteArrayInputStream,
                    mimeType = mimeType,
                ) }
            }
        } finally {
            if (output !== cropped) output.recycle()
            if (cropped !== transformed && cropped !== bitmap) cropped.recycle()
            if (transformed !== bitmap) transformed.recycle()
        }
    }

    /**
     * 按目标边界采样并缩放私有存储图片，避免列表缩略图先解码完整原图。
     *
     * 目标尺寸必须为正数且不超过 4096；损坏文件或无效图片边界返回 null。
     */
    suspend fun loadSampledBitmap(
        uuid: String,
        requestedWidthPx: Int,
        requestedHeightPx: Int,
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (requestedWidthPx !in 1..MAX_THUMBNAIL_DIMENSION
            || requestedHeightPx !in 1..MAX_THUMBNAIL_DIMENSION
        ) {
            return@withContext null
        }
        readWithLease(uuid, { null }) { file ->
            decodeSampledBitmap(file, requestedWidthPx, requestedHeightPx)
        }
    }

    /** 在已有租约保护下解码头像，物理回收不能在边界读取和解码之间发生。 */
    private fun decodeSampledBitmap(
        file: File,
        requestedWidthPx: Int,
        requestedHeightPx: Int,
    ): Bitmap? {
        // 先读取尺寸，采样解码仍处于文件租约保护内。
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sourceWidth = bounds.outWidth.takeIf { it > 0 } ?: return null
        val sourceHeight = bounds.outHeight.takeIf { it > 0 } ?: return null
        val sampleSize = calculateInSampleSize(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            requestedWidth = requestedWidthPx,
            requestedHeight = requestedHeightPx,
        )
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return null
        if (decoded.width <= requestedWidthPx && decoded.height <= requestedHeightPx) {
            return decoded
        }
        val scale = minOf(
            a = requestedWidthPx.toDouble() / decoded.width.toDouble(),
            b = requestedHeightPx.toDouble() / decoded.height.toDouble(),
        )
        val targetWidth = (decoded.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (decoded.height * scale).toInt().coerceAtLeast(1)
        val scaled = decoded.scale(width = targetWidth, height = targetHeight, filter = true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    /**
     * 根据 UUID 删除对应的文件记录。
     *
     * 删除记录后，会自动检查是否还有其他 UUID 引用了同一个物理文件（哈希值相同）。
     * 如果没有其他引用，则会自动清理对应的物理文件，释放存储空间。
     *
     * @param uuid 要删除的文件的唯一标识符。
     */
    suspend fun deleteFile(uuid: String) {
        mutate { removeFiles(listOf(uuid)) }
    }

    private fun calculateInSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        requestedWidth: Int,
        requestedHeight: Int,
    ): Int {
        val width = sourceWidth.toLong()
        val height = sourceHeight.toLong()
        var sampleSize = 1L
        while (
            sampleSize <= Int.MAX_VALUE / 2L
            && width / (sampleSize * 2L) >= requestedWidth.toLong()
            && height / (sampleSize * 2L) >= requestedHeight.toLong()
        ) {
            sampleSize *= 2L
        }
        return sampleSize.toInt()
    }

    private fun calculateLongEdgeSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        val longEdge = maxOf(width, height)
        while (longEdge / (sampleSize * 2) >= maxDimension) sampleSize *= 2
        return sampleSize
    }

    private fun readExifOrientation(uri: Uri): Int = runCatching {
        mContext.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (oriented !== bitmap) bitmap.recycle()
        return oriented
    }

    /** 同目录的多个仓库实例共用保护状态，所有访问由文件锁串行化。 */
    private class StorageState {
        val activeDrafts = mutableSetOf<String>()
        val leases = mutableMapOf<String, Int>()
    }

    private companion object {
        val mStorageStates = ConcurrentHashMap<String, StorageState>()
        val mStorageMutex = Mutex()
        const val STAGING_RETENTION_MILLIS = 24L * 60 * 60 * 1000
        const val MAX_THUMBNAIL_DIMENSION = 4_096
        const val MAX_CROP_SOURCE_DIMENSION = 2_048
        const val AVATAR_DECODE_DIMENSION = 512
        const val AVATAR_OUTPUT_DIMENSION = 1_024
        const val AVATAR_JPEG_QUALITY = 92
    }
}
