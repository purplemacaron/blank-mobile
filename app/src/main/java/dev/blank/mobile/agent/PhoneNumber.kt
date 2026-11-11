package dev.blank.mobile.agent

object PhoneNumber {
    fun normalize(raw: String): String? {
        if (raw.any { !it.isDigit() && it !in "+-(). " }) return null
        val plus = raw.startsWith("+")
        if (raw.count { it == '+' } > (if (plus) 1 else 0)) return null
        val digits = raw.filter { it.isDigit() }
        if (digits.length !in 2..15) return null
        return (if (plus) "+" else "") + digits
    }

    fun mask(number: String): String {
        val digits = number.filter { it.isDigit() }
        return if (digits.length <= 4) "•".repeat(digits.length)
        else "•".repeat(digits.length - 4) + digits.takeLast(4)
    }
}