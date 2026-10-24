package me.kafuuneko.rpclient.libs.json

/**
 * 将文本拆为可独立布局的小块，不在 UTF-16 代理对之间断开。
 *
 * @param source 原始文本
 * @param blockSize 单块的目标字符数
 * @return 按原顺序拼接后与输入相同的文本块
 */
fun splitJsonText(source: String, blockSize: Int = 2048): List<String> {
    require(blockSize >= 2) { "Text block size must be at least two." }
    if (source.isEmpty()) return listOf("")
    val blocks = mutableListOf<String>()
    var start = 0
    // 优先在后半块的换行处分段；连续编码文本也始终受块大小限制。
    while (start < source.length) {
        var end = minOf(start + blockSize, source.length)
        if (end < source.length) {
            val newline = source.lastIndexOf('\n', end - 1)
            if (newline >= start + blockSize / 2) end = newline + 1
        }
        // 连续换行也限制单块高度，避免少量字符产生数千行布局。
        var lines = 0
        for (index in start until end) {
            if (source[index] == '\n' && ++lines >= 64) {
                end = index + 1
                break
            }
        }
        if (end < source.length && source[end - 1].isHighSurrogate() && source[end].isLowSurrogate()) end--
        blocks += source.substring(start, end)
        start = end
    }
    return blocks
}
