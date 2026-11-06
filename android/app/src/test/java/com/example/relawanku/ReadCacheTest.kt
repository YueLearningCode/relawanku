package com.example.relawanku

import com.example.relawanku.data.ReadCache
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class ReadCacheTest {
    private var now = 0L
    private val cache = ReadCache({ now }, 3)

    private fun url(path: String) = "http://localhost/api/$path".toHttpUrl()

    private fun put(path: String, value: String = "value", scope: String = "user1") =
        cache.put(scope, url(path), value)

    private fun read(path: String, scope: String = "user1") = cache.get<String>(scope, url(path))

    @Test
    fun fresh_data_is_reused_but_expires_at_thirty_seconds() {
        put("activities")
        now = 29_999
        assertEquals("value", read("activities"))
        now = 30_000
        assertNull(read("activities"))
    }

    @Test
    fun account_server_filter_and_page_are_distinct() {
        put("activities?page=1&search=a")
        assertNull(read("activities?page=1&search=a", "user2"))
        assertNull(read("activities?page=2&search=a"))
        assertNull(read("activities?page=1&search=b"))
        assertNull(
            cache.get<String>("user1", "http://other/api/activities?page=1&search=a".toHttpUrl())
        )
    }

    @Test
    fun task_mutation_invalidates_tasks_and_dashboard_but_preserves_activity_list() {
        put("activities/1/tasks?page=1")
        put("dashboard")
        put("activities")
        cache.afterMutation(url("activities/1/tasks/2/status"))
        assertNull(read("activities/1/tasks?page=1"))
        assertNull(read("dashboard"))
        assertEquals("value", read("activities"))
    }

    @Test
    fun joining_invalidates_membership_and_activity_counts() {
        put("activities/1")
        put("my/registrations?page=1")
        put("activities")
        cache.afterMutation(url("activities/1/registrations"))
        assertNull(read("activities/1"))
        assertNull(read("my/registrations?page=1"))
        assertNull(read("activities"))
    }

    @Test
    fun documentation_mutation_keeps_unrelated_tasks() {
        put("activities/1/documentations?page=1")
        put("history?page=1")
        put("activities/1/tasks?page=1")
        cache.afterMutation(url("activities/1/documentations/2"))
        assertNull(read("activities/1/documentations?page=1"))
        assertNull(read("history?page=1"))
        assertEquals("value", read("activities/1/tasks?page=1"))
    }

    @Test
    fun manual_refresh_only_invalidates_the_selected_activity() {
        put("activities/1")
        put("activities/1/tasks?page=1")
        put("activities/10")
        cache.refresh("detail", "home", 1)
        assertNull(read("activities/1"))
        assertNull(read("activities/1/tasks?page=1"))
        assertEquals("value", read("activities/10"))
    }

    @Test
    fun a_read_started_before_mutation_cannot_repopulate_stale_data() {
        val started = cache.revision
        cache.afterMutation(url("activities/1"))
        cache.put("user1", url("activities/1"), "stale", started)
        assertNull(read("activities/1"))
    }

    @Test
    fun credentials_users_health_and_live_attendance_are_never_cached() {
        for (path in
            listOf(
                "auth/me",
                "auth/login",
                "users?page=1",
                "health",
                "activities/1/attendances",
                "activities/1/attendance-sessions",
            )) {
            put(path)
            assertNull("Must not cache $path", read(path))
        }
    }

    @Test
    fun entries_are_bounded_and_logout_or_account_mutation_clears_all() {
        (1..4).forEach { put("activities/$it") }
        assertNull(read("activities/1"))
        assertEquals("value", read("activities/4"))
        cache.afterMutation(url("auth/logout"))
        assertNull(read("activities/4"))
        put("activities")
        cache.clear()
        assertNull(read("activities"))
    }
}
