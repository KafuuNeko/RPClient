package me.kafuuneko.rpclient.libs.character

/** 角色卡文件格式，供导出选择、系统文件创建器与归档条目共用。 */
enum class CharacterCardExportFormat(val extension: String, val mimeType: String) {
    Png("png", "image/png"),
    Json("json", "application/json")
}
