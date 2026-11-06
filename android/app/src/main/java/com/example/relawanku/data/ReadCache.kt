package com.example.relawanku.data

import okhttp3.HttpUrl

/**
 * Session-only cache. UI state owns this instance; no credentials or responses are written to disk.
 */
class ReadCache(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val capacity: Int = 64,
) {
    private data class Key(val scope: String, val url: String)

    private data class Entry(val path: String, val value: Any, val expires: Long)

    private val entries = linkedMapOf<Key, Entry>()
    var revision = 0L
        private set

    private fun path(url: HttpUrl) = url.encodedPath.substringAfter("/api/", "")

    private fun cacheable(path: String): Boolean =
        (path == "dashboard" ||
            path == "history" ||
            path == "my/registrations" ||
            path == "activities" ||
            path.startsWith("activities/")) &&
            !path.endsWith("/attendances") &&
            !path.contains("attendance-sessions")

    @Suppress("UNCHECKED_CAST")
    fun <T> get(scope: String, url: HttpUrl): T? {
        val key = Key(scope, url.toString())
        val entry = entries[key] ?: return null
        if (clock() >= entry.expires) {
            entries.remove(key)
            return null
        }
        return entry.value as T
    }

    fun put(scope: String, url: HttpUrl, value: Any, startedAtRevision: Long = revision) {
        val path = path(url)
        if (startedAtRevision != revision || !cacheable(path)) return
        entries.entries.removeAll { clock() >= it.value.expires }
        val key = Key(scope, url.toString())
        if (!entries.containsKey(key) && entries.size >= capacity)
            entries.remove(entries.keys.first())
        entries[key] = Entry(path, value, clock() + 30_000)
    }

    private fun invalidate(matches: (String) -> Boolean) {
        revision++
        entries.entries.removeAll { matches(it.value.path) }
    }

    fun clear() {
        revision++
        entries.clear()
    }

    fun afterMutation(url: HttpUrl) {
        val path = path(url)
        if (!path.startsWith("activities")) {
            clear()
            return
        }
        val parts = path.split('/')
        val eventRoot = parts.take(2).joinToString("/")
        val resource = parts.getOrNull(2)
        when (resource) {
            "tasks" ->
                invalidate {
                    it == "dashboard" || it == "history" || it.startsWith("$eventRoot/tasks")
                }
            "documentations" ->
                invalidate { it == "history" || it.startsWith("$eventRoot/documentations") }
            else ->
                invalidate {
                    it == "dashboard" ||
                        it == "history" ||
                        it == "my/registrations" ||
                        it.startsWith("activities")
                }
        }
    }

    fun refresh(screen: String, tab: String, event: Long) {
        val root = "activities/$event"
        invalidate { path ->
            when {
                screen == "detail" -> path == root || path.startsWith("$root/")
                screen == "history" -> path == "history"
                screen == "main" && tab == "home" -> path == "dashboard" || path == "activities"
                screen == "main" && tab == "explore" -> path == "activities"
                screen == "main" && tab == "joined" -> path == "my/registrations"
                else -> false
            }
        }
    }
}
