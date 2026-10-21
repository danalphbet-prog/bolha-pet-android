package com.bolhapet.app

/** Extracts the Qwen's emotion directive without exposing it in chat. */
internal class EmotionParser {
    data class Part(val emotion: String? = null, val text: String = "")
    private val pending = StringBuilder()
    private var headerDone = false
    private val accepted = setOf(
        "neutro", "feliz", "alegria", "triste", "espanto", "curioso", "bravo", "sono",
        "confuso", "piscadela", "amor", "assustado", "chorando", "apaixonado", "tonto",
        "maroto", "pensativo", "envergonhado", "emburrado", "gargalhada", "furioso",
        "entediado", "nojinho", "beijinho"
    )
    private val pattern = Regex("^\\[\\[emocao:([a-z]+)]]")

    fun consume(chunk: String): Part {
        if (headerDone) return Part(text = chunk)
        pending.append(chunk)
        val value = pending.toString().trimStart()
        if (value.startsWith("[[")) {
            val end = value.indexOf("]]", startIndex = 2)
            if (end >= 0) {
                val found = pattern.find(value)?.takeIf { it.range.first == 0 }
                headerDone = true
                val emotion = found?.groupValues?.getOrNull(1)?.takeIf { it in accepted }
                return Part(emotion, value.substring(end + 2).trimStart())
            }
            if (value.length <= 80) return Part()
        } else if (value.isEmpty() || (value.startsWith("[") && value.length < 10)) {
            return Part()
        }
        headerDone = true
        return Part(text = pending.toString())
    }

    fun flush(): String = if (headerDone) "" else pending.toString().also { headerDone = true }
}
