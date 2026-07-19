package postmannen.ui

fun resizeColumns(leftWidth: Int, rightWidth: Int, deltaColumns: Int, minWidth: Int): Pair<Int, Int> {
    val total = leftWidth + rightWidth
    if (total < 2 * minWidth) return leftWidth to rightWidth
    val clampedLeft = (leftWidth + deltaColumns).coerceIn(minWidth, total - minWidth)
    return clampedLeft to (total - clampedLeft)
}
