package me.kafuuneko.rpclient.libs.room.model

/** 摘要提交的乐观并发快照；只包含正文和附件元数据，不含图片字节。 */
data class SummaryInputSnapshot(val messages: List<MessageWithImages>, val summaryVersion: String)
