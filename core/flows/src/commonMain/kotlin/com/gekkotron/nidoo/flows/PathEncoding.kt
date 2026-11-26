package com.gekkotron.nidoo.flows

private const val HEX = "0123456789ABCDEF"

/** Same output as `URLEncoder.encode(value, UTF_8).replace("+", "%20")`: only `A-Z a-z 0-9 . - * _` stay, every other UTF-8 byte is `%XX`. */
internal fun encodePathPart(value: String): String = buildString {
    for (b in value.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        val ch = c.toChar()
        if (c < 0x80 && (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '.' || ch == '-' || ch == '*' || ch == '_')) {
            append(ch)
        } else {
            append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
        }
    }
}
