package me.kafuuneko.rpclient.libs.search

/** 搜索跳转只传稳定源标识和短查询，不在 Bundle 传完整正文。 */
object SearchNavigation {
    const val EXTRA_SOURCE_ID = "search_source_id"
    const val EXTRA_QUERY = "search_query"
}
