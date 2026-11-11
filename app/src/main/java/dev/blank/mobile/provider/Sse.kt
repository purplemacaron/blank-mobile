package dev.blank.mobile.provider

data class SseFrame(val event: String, val data: String)

class SseParser {
    private val buffer = StringBuilder()

    fun feed(chunk: String): List<SseFrame> {
        buffer.append(chunk.replace("\r\n", "\n"))
        val frames = mutableListOf<SseFrame>()
        var index = buffer.indexOf("\n\n")
        while (index >= 0) {
            val frame = buffer.substring(0, index)
            buffer.delete(0, index + 2)
            parseFrame(frame)?.let(frames::add)
            index = buffer.indexOf("\n\n")
        }
        return frames
    }

    fun flush(): List<SseFrame> {
        val rest = buffer.toString()
        buffer.clear()
        return listOfNotNull(parseFrame(rest))
    }

    private fun parseFrame(frame: String): SseFrame? {
        var event = "message"
        val data = mutableListOf<String>()
        for (line in frame.split("\n")) {
            if (line.startsWith("event:")) event = line.substring(6).trim()
            else if (line.startsWith("data:")) data.add(line.substring(5).removePrefix(" "))
        }
        if (data.isEmpty()) return null
        return SseFrame(event, data.joinToString("\n"))
    }
}