package com.example.relawanku

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.relawanku.data.ApiClient
import com.example.relawanku.data.ApiUser
import com.example.relawanku.data.SessionStore
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ReadCacheFlowTest {
    private fun await(counter: AtomicInteger, count: Int) {
        val end = System.currentTimeMillis() + 10000
        while (counter.get() < count && System.currentTimeMillis() < end) Thread.sleep(50)
        assertEquals(count, counter.get())
    }

    @Test
    fun repeated_reads_hit_network_once_then_refresh_and_writes_fetch_again() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = SessionStore(context)
        val previous = session.baseUrl
        val server = MockWebServer()
        server.start()
        val done = AtomicInteger()
        val payload =
            """{"data":[{"id":1,"title":"Cache fixture"}],"current_page":1,"last_page":1,"total":1}"""
        try {
            session.setBaseUrl(server.url("/api/").toString())
            session.clear()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { a ->
                    a.state.clear()
                    a.state.user = ApiUser(id = 1, role = "relawan")
                    a.session.save("cache-test-token", null)
                    a.api = ApiClient.create(a.session.baseUrl) { a.session.token }
                    a.navigate("login")
                }
                server.enqueue(MockResponse().setBody(payload))
                scenario.onActivity { a ->
                    a.request(a.api.activities(emptyMap())) { done.incrementAndGet() }
                }
                await(done, 1)
                scenario.onActivity { a ->
                    a.request(a.api.activities(emptyMap())) { done.incrementAndGet() }
                }
                await(done, 2)
                assertEquals("Second read should use cache", 1, server.requestCount)
                server.enqueue(MockResponse().setBody(payload))
                scenario.onActivity { a ->
                    a.state.reads.refresh("main", "explore", 0)
                    a.request(a.api.activities(emptyMap())) { done.incrementAndGet() }
                }
                await(done, 3)
                assertEquals("Refresh must reach server", 2, server.requestCount)
                server.enqueue(MockResponse().setBody("""{"data":{"id":2,"title":"Created"}}"""))
                scenario.onActivity { a ->
                    a.write(a.api.createActivity(mapOf("title" to "Created"))) {
                        done.incrementAndGet()
                    }
                }
                await(done, 4)
                server.enqueue(MockResponse().setBody(payload))
                scenario.onActivity { a ->
                    a.request(a.api.activities(emptyMap())) { done.incrementAndGet() }
                }
                await(done, 5)
                assertEquals("Mutation must invalidate activity list", 4, server.requestCount)
                server.enqueue(MockResponse().setBody(payload))
                scenario.onActivity { a ->
                    a.state.user = ApiUser(id = 2, role = "relawan")
                    a.request(a.api.activities(emptyMap())) { done.incrementAndGet() }
                }
                await(done, 6)
                assertEquals("Another account must not reuse the response", 5, server.requestCount)
                scenario.recreate()
                scenario.onActivity { a ->
                    a.request(a.api.activities(emptyMap())) { done.incrementAndGet() }
                }
                await(done, 7)
                assertEquals("Cache survives activity recreation", 5, server.requestCount)
            }
        } finally {
            session.setBaseUrl(previous)
            session.clear()
            server.shutdown()
        }
    }
}
