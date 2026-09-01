package me.kafuuneko.rpclient.libs.room.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.model.SquareCropSelection
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.FileEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * 文件存储库，提供文件的保存、获取和删除功能。
 *
 * 该存储库将文件保存在应用的私有数据目录下（repository/），
 * 并使用文件的 SHA-256 哈希值作为文件名，以确保相同内容的文件不会被重复保存。
 */
class FileRepository(
    private val mContext: Context,
    appDatabase: AppDatabase
) {
    private val mFileDao = appDatabase.getFileDao()

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
        } ?: throw IllegalArgumentException("Cannot open input stream from URI: $uri")
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
    suspend fun saveFile(file: File, mimeType: String? = null): String = withContext(Dispatchers.IO) {
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
        val tempFile = withContext(Dispatchers.IO) {
            File.createTempFile("temp_", ".tmp", mRepositoryDir)
        }
        val hash = try {
            val digest = MessageDigest.getInstance("SHA-256")
            withContext(Dispatchers.IO) {
                FileOutputStream(tempFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                    }
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }

        val targetFile = File(mRepositoryDir, hash)
        if (!targetFile.exists()) {
            tempFile.renameTo(targetFile)
        } else {
            tempFile.delete()
        }

        val uuid = UUID.randomUUID().toString()
        mFileDao.insert(FileEntity(uuid = uuid, hash = hash, mimeType = mimeType))
        return uuid
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
        requestedHeightPx = AVATAR_DECODE_DIMENSION
    )

    /**
     * 解码供交互裁剪使用的图片，并在进入 UI 前应用 EXIF 方向。
     *
     * 解码长边受到限制，避免超大相册图片在裁剪页造成不必要的内存压力。
     */
    suspend fun loadBitmapForCrop(
        uri: Uri,
        maxDimensionPx: Int = MAX_CROP_SOURCE_DIMENSION
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (maxDimensionPx !in 1..MAX_THUMBNAIL_DIMENSION) return@withContext null
        // 预先读取原始图片宽高边界
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsInput = mContext.contentResolver.openInputStream(uri) ?: return@withContext null
        boundsInput.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val sourceWidth = bounds.outWidth.takeIf { it > 0 } ?: return@withContext null
        val sourceHeight = bounds.outHeight.takeIf { it > 0 } ?: return@withContext null
        // 计算长边下采样采样率
        val sampleSize = calculateLongEdgeSampleSize(sourceWidth, sourceHeight, maxDimensionPx)
        // 采样解码为 ARGB_8888 格式
        val decoded = mContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(
                it,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
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
        outputSizePx: Int = AVATAR_OUTPUT_DIMENSION
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
            .coerceIn(1, shortEdge)
        val centerX = selection.centerX.coerceIn(0f, 1f) * transformed.width
        val centerY = selection.centerY.coerceIn(0f, 1f) * transformed.height
        val cropLeft = (centerX - cropSize / 2f)
            .toInt()
            .coerceIn(0, transformed.width - cropSize)
        val cropTop = (centerY - cropSize / 2f)
            .toInt()
            .coerceIn(0, transformed.height - cropSize)
        // 截取正方形区域
        val cropped = Bitmap.createBitmap(transformed, cropLeft, cropTop, cropSize, cropSize)
        // 等比缩放至目标输出分辨率
        val output = if (cropped.width == outputSizePx) {
            cropped
        } else {
            cropped.scale(outputSizePx, outputSizePx, filter = true)
        }
        // 根据透明度选择 PNG 或高质量 JPEG 压缩
        val hasAlpha = transformed.hasAlpha()
        val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val mimeType = if (hasAlpha) "image/png" else "image/jpeg"
        try {
            ByteArrayOutputStream().use { bytes ->
                check(output.compress(format, AVATAR_JPEG_QUALITY, bytes))
                ByteArrayInputStream(bytes.toByteArray()).use { saveStream(it, mimeType) }
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
        requestedHeightPx: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (requestedWidthPx !in 1..MAX_THUMBNAIL_DIMENSION ||
            requestedHeightPx !in 1..MAX_THUMBNAIL_DIMENSION
        ) {
            return@withContext null
        }
        val file = getFile(uuid) ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sourceWidth = bounds.outWidth.takeIf { it > 0 } ?: return@withContext null
        val sourceHeight = bounds.outHeight.takeIf { it > 0 } ?: return@withContext null
        val sampleSize = calculateInSampleSize(
            sourceWidth,
            sourceHeight,
            requestedWidthPx,
            requestedHeightPx
        )
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return@withContext null
        if (decoded.width <= requestedWidthPx && decoded.height <= requestedHeightPx) {
            return@withContext decoded
        }
        val scale = minOf(
            requestedWidthPx.toDouble() / decoded.width.toDouble(),
            requestedHeightPx.toDouble() / decoded.height.toDouble()
        )
        val targetWidth = (decoded.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (decoded.height * scale).toInt().coerceAtLeast(1)
        val scaled = decoded.scale(targetWidth, targetHeight, filter = true)
        if (scaled !== decoded) decoded.recycle()
        scaled
    }

    /**
     * 根据 UUID 删除对应的文件记录。
     *
     * 删除记录后，会自动检查是否还有其他 UUID 引用了同一个物理文件（哈希值相同）。
     * 如果没有其他引用，则会自动清理对应的物理文件，释放存储空间。
     *
     * @param uuid 要删除的文件的唯一标识符。
     */
    suspend fun deleteFile(uuid: String) = withContext(Dispatchers.IO) {
        val entity = mFileDao.getByUuid(uuid) ?: return@withContext
        mFileDao.deleteByUuid(uuid)
        
        val count = mFileDao.countByHash(entity.hash)
        if (count == 0) {
            val file = File(mRepositoryDir, entity.hash)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private fun calculateInSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        requestedWidth: Int,
        requestedHeight: Int
    ): Int {
        val width = sourceWidth.toLong()
        val height = sourceHeight.toLong()
        var sampleSize = 1L
        while (
            sampleSize <= Int.MAX_VALUE / 2L &&
            width / (sampleSize * 2L) >= requestedWidth.toLong() &&
            height / (sampleSize * 2L) >= requestedHeight.toLong()
        ) {
            sampleSize *= 2L
        }
        return sampleSize.toInt().coerceAtLeast(1)
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
                ExifInterface.ORIENTATION_NORMAL
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

    private companion object {
        const val MAX_THUMBNAIL_DIMENSION = 4_096
        const val MAX_CROP_SOURCE_DIMENSION = 2_048
        const val AVATAR_DECODE_DIMENSION = 512
        const val AVATAR_OUTPUT_DIMENSION = 1_024
        const val AVATAR_JPEG_QUALITY = 92
    }
}
