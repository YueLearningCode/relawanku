package com.example.relawanku

import com.example.relawanku.data.ActivityDto
import com.example.relawanku.data.ApiClient
import com.example.relawanku.data.ApiFailure
import com.example.relawanku.data.BearerInterceptor
import com.example.relawanku.data.Dates
import com.example.relawanku.data.ServerAddress
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiContractTest {
    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun base() = server.url("/api/").toString()

    @Test
    fun login_is_json_without_bearer_and_parses_session() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"data":{"user":{"id":4,"name":"A","role":"koordinator"},"token":"secret","expires_at":"2026-12-01T08:00:00.000000Z"}}"""
                )
        )
        val result =
            ApiClient.create(base()) { "old-token" }
                .login(
                    mapOf(
                        "email" to "a@example.test",
                        "password" to "pass",
                        "device_name" to "Android",
                    )
                )
                .execute()
                .body()!!
                .data!!
        assertEquals("koordinator", result.user!!.role)
        assertEquals("secret", result.token)
        val request = server.takeRequest()
        assertEquals("/api/auth/login", request.path)
        assertNull(request.getHeader("Authorization"))
        assertEquals("application/json", request.getHeader("Accept"))
        assertTrue(request.body.readUtf8().contains("\"device_name\":\"Android\""))
    }

    @Test
    fun bearer_is_added_only_to_protected_same_origin_path() {
        val client =
            OkHttpClient.Builder().addInterceptor(BearerInterceptor(base()) { "abc" }).build()
        for (path in listOf("/api/auth/me", "/api/health", "/elsewhere")) {
            server.enqueue(MockResponse().setBody("{}"))
            client.newCall(Request.Builder().url(server.url(path)).build()).execute().close()
            assertEquals(
                if (path == "/api/auth/me") "Bearer abc" else null,
                server.takeRequest().getHeader("Authorization"),
            )
        }
        val other = MockWebServer()
        other.start()
        try {
            other.enqueue(MockResponse().setBody("{}"))
            client
                .newCall(Request.Builder().url(other.url("/api/auth/me")).build())
                .execute()
                .close()
            assertNull(other.takeRequest().getHeader("Authorization"))
        } finally {
            other.shutdown()
        }
    }

    @Test
    fun pagination_and_server_fields_are_preserved() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"data":[{"id":99,"title":"Bantu","active_registrations_count":7,"coordinator_id":4,"starts_at":"2026-11-06T08:00:00.000000Z"}],"current_page":2,"last_page":3,"total":45}"""
                )
        )
        val page =
            ApiClient.create(base()) { "abc" }
                .activities(mapOf("page" to "2", "search" to "Bantu orang"))
                .execute()
                .body()!!
        assertEquals(2, page.currentPage)
        assertEquals(45, page.total)
        assertEquals(7, page.data.first().participants)
        assertEquals(4L, page.data.first().coordinatorId)
        assertTrue(server.takeRequest().path!!.contains("search=Bantu%20orang"))
    }

    @Test
    fun bulk_delete_keeps_json_body() {
        server.enqueue(MockResponse().setBody("""{"message":"OK"}"""))
        assertTrue(
            ApiClient.create(base()) { "abc" }
                .deleteTasks(3, mapOf("ids" to listOf(1, 2)))
                .execute()
                .isSuccessful
        )
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/api/activities/3/tasks/bulk", req.path)
        assertEquals("{\"ids\":[1,2]}", req.body.readUtf8())
    }

    @Test
    fun photo_upload_uses_expected_multipart_field() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"data":{"id":1,"image_url":"http://localhost/storage/a.png"}}""")
        )
        val part =
            MultipartBody.Part.createFormData(
                "image",
                "photo.png",
                byteArrayOf(1, 2, 3).toRequestBody("image/png".toMediaType()),
            )
        val result = ApiClient.create(base()) { "abc" }.uploadPhoto(3, part, null).execute()
        assertEquals(1L, result.body()!!.data!!.id)
        val req = server.takeRequest()
        assertTrue(req.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        assertTrue(req.body.readUtf8().contains("name=\"image\"; filename=\"photo.png\""))
    }

    @Test
    fun validation_and_unexpected_errors_never_expose_server_trace() {
        val validation =
            ApiFailure.http(
                422,
                """{"message":"Invalid","errors":{"email":["Email sudah dipakai"]}}""",
            )
        assertEquals("Email sudah dipakai", validation.fields["email"]!!.first())
        assertEquals("Email sudah dipakai", validation.message)
        assertTrue(
            ApiFailure.http(500, """{"message":"SQLSTATE password=secret"}""")
                .message
                .contains("Server mengalami")
        )
        assertEquals(422, ApiFailure.http(422, """{"errors":[],"message":"Invalid"}""").status)
        assertEquals(502, ApiFailure.http(502, "<html>proxy</html>").status)
    }

    @Test
    fun addresses_and_local_media_use_device_reachable_origin() {
        assertEquals(
            "http://10.0.2.2:8000/api/",
            ServerAddress.normalize(" http://10.0.2.2:8000 ", true),
        )
        assertEquals(
            "http://192.168.1.5:8000/storage/a.jpg",
            ServerAddress.media("http://192.168.1.5:8000/api/", "http://localhost/storage/a.jpg"),
        )
        assertEquals(
            "https://cdn.example.test/a.jpg",
            ServerAddress.media("https://example.test/api/", "https://cdn.example.test/a.jpg"),
        )
        for (url in
        listOf(
            "http://example.test/api/",
            "https://user:pass@example.test",
            "https://example.test/api/?token=x",
        )) assertTrue(runCatching { ServerAddress.normalize(url, false) }.isFailure)
    }

    @Test
    fun fractional_utc_dates_and_registration_windows_work() {
        assertNotNull(Dates.parse("2026-11-06T08:30:00.123456Z"))
        val date = Dates.parse("2026-11-06T08:30:00+07:00")!!
        assertEquals(date.time, Dates.parse(Dates.iso(date))!!.time)
        assertNull(Dates.parse("not-a-date"))
        val event =
            ActivityDto(
                status = "open",
                quota = 2,
                participants = 1,
                startsAt = "2026-12-01T08:00:00Z",
                endsAt = "2026-12-01T10:00:00Z",
                registrationDeadline = "2026-11-30T08:00:00Z",
            )
        assertTrue(event.registrationOpen(Dates.parse("2026-11-06T08:00:00Z")!!))
        assertFalse(event.registrationOpen(Dates.parse("2026-12-01T08:30:00Z")!!))
        assertTrue(event.ongoing(Dates.parse("2026-12-01T08:30:00Z")!!))
    }

    @Test
    fun patch_preserves_explicit_null_for_clearing_nullable_fields() {
        server.enqueue(MockResponse().setBody("""{"data":{"id":4}}"""))
        ApiClient.create(base()) { "abc" }.caption(3, 4, mapOf("caption" to null)).execute()
        assertEquals("{\"caption\":null}", server.takeRequest().body.readUtf8())
    }
}
