package app.map.android

object MusicFormats {
    private val extensions = setOf(
        "mp3", "m4a", "m4b", "aac", "flac", "wav", "ogg", "oga", "opus",
        "amr", "3gp", "3gpp", "webm", "mka", "aif", "aiff", "aifc", "mid", "midi"
    )

    fun isAudio(name: String, mime: String): Boolean =
        mime.substringBefore(';').lowercase().startsWith("audio/") || extension(name, mime) in extensions

    fun extension(name: String, mime: String): String =
        name.substringAfterLast('.', mime.substringAfter('/', "audio")).lowercase()
}
