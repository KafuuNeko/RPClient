package me.kafuuneko.rpclient.libs.room.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.FileEntity
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
}
