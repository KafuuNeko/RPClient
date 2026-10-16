package me.kafuuneko.rpclient.feature.characteredit.model

import me.kafuuneko.rpclient.libs.room.model.PreparedFile

/** 图片索引与暂存凭据一同切换，防止新索引误读上一张暂存。 */
internal data class BackgroundDraft(val uuid: String?, val prepared: PreparedFile? = null)
