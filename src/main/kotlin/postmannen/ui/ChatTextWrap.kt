package postmannen.ui

fun wrapText(text: String, width: Int): String {
    if (width <= 0) return text
    return text.split("\n").joinToString("\n") { line -> wrapLine(line, width) }
}

private fun wrapLine(line: String, width: Int): String {
    if (line.length <= width) return line
    val wrapped = StringBuilder()
    var current = StringBuilder()
    for (word in line.split(" ")) {
        var remaining = word
        while (remaining.length > width) {
            if (current.isNotEmpty()) {
                wrapped.append(current).append('\n')
                current = StringBuilder()
            }
            wrapped.append(remaining.take(width)).append('\n')
            remaining = remaining.drop(width)
        }
        val candidateLength = if (current.isEmpty()) remaining.length else current.length + 1 + remaining.length
        if (candidateLength > width) {
            wrapped.append(current).append('\n')
            current = StringBuilder(remaining)
        } else {
            if (current.isNotEmpty()) current.append(' ')
            current.append(remaining)
        }
    }
    wrapped.append(current)
    return wrapped.toString()
}
