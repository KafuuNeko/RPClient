package me.kafuuneko.rpclient.libs.regex

/**
 * 将项目接受的 JavaScript 风格正则选项编译为 Kotlin Regex。
 *
 * Java 与 ECMAScript 的 code point escape 拼写不同；Unicode 模式下将 `\u{...}` 转换为
 * Java Pattern 等价的 `\x{...}`。其余表达式仍由底层 Pattern 校验，避免引入可执行脚本引擎。
 */
internal object JavaScriptRegexCompiler {
    fun compile(pattern: String, flags: Set<Char>): Regex {
        val options = buildSet {
            if ('i' in flags) add(RegexOption.IGNORE_CASE)
            if ('m' in flags) add(RegexOption.MULTILINE)
            if ('s' in flags) add(RegexOption.DOT_MATCHES_ALL)
        }
        val compatiblePattern = if ('u' in flags) {
            pattern.translateUnicodeCodePointEscapes()
        } else {
            pattern
        }
        return Regex(compatiblePattern, options)
    }

    private fun String.translateUnicodeCodePointEscapes(): String {
        return buildString(length) {
            var index = 0
            while (index < this@translateUnicodeCodePointEscapes.length) {
                if (!this@translateUnicodeCodePointEscapes.isCodePointEscapeAt(index)) {
                    append(this@translateUnicodeCodePointEscapes[index])
                    index += 1
                    continue
                }
                val closingBrace = this@translateUnicodeCodePointEscapes.indexOf(
                    char = '}',
                    startIndex = index + CODE_POINT_ESCAPE_PREFIX_LENGTH,
                )
                require(closingBrace >= 0) { "Invalid Unicode code point escape" }
                val digits = this@translateUnicodeCodePointEscapes.substring(
                    startIndex = index + CODE_POINT_ESCAPE_PREFIX_LENGTH,
                    endIndex = closingBrace,
                )
                val normalizedDigits = digits.normalizeCodePointDigits()
                append("\\x{")
                append(normalizedDigits)
                append('}')
                index = closingBrace + 1
            }
        }
    }

    private fun String.isCodePointEscapeAt(index: Int): Boolean {
        if (
            getOrNull(index) != '\\'
            || getOrNull(index + 1) != 'u'
            || getOrNull(index + 2) != '{'
        ) {
            return false
        }
        var precedingBackslashes = 0
        var cursor = index - 1
        while (cursor >= 0 && this[cursor] == '\\') {
            precedingBackslashes += 1
            cursor -= 1
        }
        return precedingBackslashes % 2 == 0
    }

    private fun String.normalizeCodePointDigits(): String {
        require(isNotEmpty() && all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) {
            "Invalid Unicode code point escape"
        }
        val normalized = trimStart('0').ifEmpty { "0" }
        val codePoint = normalized.toIntOrNull(radix = 16)
        require(codePoint != null && codePoint <= MAX_UNICODE_CODE_POINT) {
            "Invalid Unicode code point escape"
        }
        return normalized
    }

    private const val CODE_POINT_ESCAPE_PREFIX_LENGTH = 3
    private const val MAX_UNICODE_CODE_POINT = 0x10FFFF
}
