package me.kafuuneko.rpclient.libs.agent.runtime

import com.google.gson.JsonObject

/** 检查点存储的真实多实现边界，协议载荷与结果不能依赖调试日志。 */
interface AgentCheckpointStore {
    /** 原子替换同一客户端键的完整载荷，返回内容 SHA-256。 */
    suspend fun write(key: String, payload: JsonObject): String

    /** 读取并核验指纹；缺失或损坏时返回 null，调用者禁止据此重放写入。 */
    suspend fun read(key: String, expectedDigest: String): JsonObject?

    /** 删除已完成运行不再需要的载荷。 */
    suspend fun delete(key: String)

    /** 全库恢复后清理本机旧载荷，防止跨库 ID 碰撞。 */
    suspend fun clear()
}
