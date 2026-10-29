package com.example.download

/**
 * 视频文件名清洗工具。
 * 部分机型/文件系统不支持文件名中的 emoji 字符（全量或部分），会导致保存失败。
 * 处理顺序（与产品需求一致）：
 * 1. 去除 URL 格式的文本（如 https://xxxx.xxx/xx）
 * 2. 连续的 emoji 段替换为「一个」下划线
 * 3. 其余特殊字符替换为下划线
 */
object FilenameSanitizer {

    // URL 文本整体去除
    private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    // 常见 emoji 区段：杂符、装饰符号、表情、交通、扩展、旗帜、按键符号、VS16 变体、ZWJ 连接符
    // 注意：Java 正则中 BMP 字符用 \uXXXX，补充平面必须用 \x{XXXXX}，\u{...} 会抛 PatternSyntaxException
    private val EMOJI_REGEX = Regex(
        "[\\u2190-\\u2BFF\\uFE00-\\uFE0F\\u200D\\x{1F000}-\\x{1FAFF}\\x{1F1E6}-\\x{1F1FF}\\u2300-\\u23FF\\u2B50\\u203C\\u2049\\u3030]+"
    )

    // 文件名非法/易出问题字符
    private val SPECIAL_REGEX = Regex("""["/\\:*?<>|\r\n\t]""")

    // 折叠连续下划线（emoji 段替换后可能相邻）
    private val UNDERSCORE_RUN = Regex("_+")

    fun sanitize(text: String): String {
        var s = text.trim()
        s = URL_REGEX.replace(s, "")
        s = EMOJI_REGEX.replace(s, "_")
        s = SPECIAL_REGEX.replace(s, "_")
        s = UNDERSCORE_RUN.replace(s, "_")
        return s.trim().trim('_').trim()
    }
}
