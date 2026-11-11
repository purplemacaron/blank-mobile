package dev.blank.mobile.data

fun maskKey(key: String): String {
    if (key.isEmpty()) return ""
    if (key.length <= 4) return "••••"
    return "••••" + key.takeLast(4)
}