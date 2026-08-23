package net.rwhps.plugin.baiduchatfilter

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class BaiduClientTest {

    // ---------------- 响应解析 ----------------

    @Test
    fun `parse pass response`() {
        val client = BaiduClient("ak", "sk", 5)
        val result = client.parseCheckResult("""{"conclusionType":1,"conclusion":"合规","data":[]}""")

        assertEquals(BaiduCheckResult.TYPE_PASS, result?.conclusionType)
        assertEquals("合规", result?.conclusion)
        assertTrue(result?.hits.isNullOrEmpty())
    }

    @Test
    fun `parse block response with hits`() {
        val client = BaiduClient("ak", "sk", 5)
        val body = """
            {
              "conclusionType": 2,
              "conclusion": "不合规",
              "data": [
                {
                  "msg": "存在色情内容",
                  "conclusionType": 2,
                  "hits": [
                    {"probability": 0.9, "datasetName": "色情词库", "words": ["测试词"]}
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = client.parseCheckResult(body)

        assertEquals(BaiduCheckResult.TYPE_BLOCK, result?.conclusionType)
        assertEquals("不合规", result?.conclusion)
        // msg 与 words 均进入 hits
        assertTrue(result?.hits?.contains("存在色情内容") == true)
        assertTrue(result?.hits?.contains("测试词") == true)
    }

    @Test
    fun `parse suspicious response`() {
        val client = BaiduClient("ak", "sk", 5)
        val result = client.parseCheckResult("""{"conclusionType":3,"conclusion":"疑似","data":[]}""")

        assertEquals(BaiduCheckResult.TYPE_SUSPICIOUS, result?.conclusionType)
    }

    @Test
    fun `parse response with error_code returns null`() {
        val client = BaiduClient("ak", "sk", 5)
        val result = client.parseCheckResult("""{"error_code":17,"error_msg":"Open api daily request limit reached"}""")

        assertNull(result)
    }

    @Test
    fun `parse response missing conclusionType returns null`() {
        val client = BaiduClient("ak", "sk", 5)
        assertNull(client.parseCheckResult("""{"conclusion":"合规"}"""))
    }

    @Test
    fun `parse malformed json returns null`() {
        val client = BaiduClient("ak", "sk", 5)
        assertNull(client.parseCheckResult("not a json"))
        assertNull(client.parseCheckResult(""))
        assertNull(client.parseCheckResult("[]"))
    }

    @Test
    fun `parseHits with null or empty data returns empty`() {
        val client = BaiduClient("ak", "sk", 5)
        assertTrue(client.parseHits(null).isEmpty())
        assertTrue(client.parseHits(com.google.gson.JsonParser.parseString("[]").asJsonArray).isEmpty())
    }

    @Test
    fun `parseHits collects msg and words`() {
        val client = BaiduClient("ak", "sk", 5)
        val data = com.google.gson.JsonParser.parseString(
            """
            [
              {"msg": "标签A", "hits": [{"words": ["词1", "词2"]}]},
              {"msg": "标签B", "hits": [{"words": ["词3"]}]},
              {"msg": "标签C"}
            ]
            """.trimIndent()
        ).asJsonArray

        val hits = client.parseHits(data)

        assertEquals(listOf("标签A", "词1", "词2", "标签B", "词3", "标签C"), hits)
    }

    // ---------------- HTTP 流程 (MockWebServer) ----------------

    private fun clientWith(server: MockWebServer): BaiduClient =
        BaiduClient("ak", "sk", 5, server.url("/token").toString(), server.url("/censor").toString())

    private fun tokenResponse(expiresIn: Long = 2592000): MockResponse =
        MockResponse().setResponseCode(200).setBody("""{"access_token":"tok-1","expires_in":$expiresIn}""")

    private fun censorResponse(body: String): MockResponse =
        MockResponse().setResponseCode(200).setBody(body)

    @Test
    fun `full flow returns parsed result`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(tokenResponse())
            server.enqueue(censorResponse("""{"conclusionType":1,"conclusion":"合规","data":[]}"""))

            val client = clientWith(server)
            val result = client.checkText("hello")

            assertEquals(BaiduCheckResult.TYPE_PASS, result?.conclusionType)
            assertEquals("合规", result?.conclusion)

            val tokenRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
            val censorRequest = server.takeRequest(5, TimeUnit.SECONDS)!!

            assertTrue(tokenRequest.path!!.startsWith("/token?"))
            assertTrue(tokenRequest.path!!.contains("grant_type=client_credentials"))
            assertTrue(tokenRequest.path!!.contains("client_id=ak"))
            assertTrue(tokenRequest.path!!.contains("client_secret=sk"))

            assertTrue(censorRequest.path!!.startsWith("/censor?"))
            assertTrue(censorRequest.path!!.contains("access_token=tok-1"))
            assertTrue(censorRequest.body.readUtf8().contains("text=hello"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `access token is cached across calls`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(tokenResponse())
            server.enqueue(censorResponse("""{"conclusionType":1,"conclusion":"合规","data":[]}"""))
            server.enqueue(censorResponse("""{"conclusionType":1,"conclusion":"合规","data":[]}"""))

            val client = clientWith(server)
            client.checkText("first")
            client.checkText("second")

            val req1 = server.takeRequest(5, TimeUnit.SECONDS)!!
            val req2 = server.takeRequest(5, TimeUnit.SECONDS)!!
            val req3 = server.takeRequest(5, TimeUnit.SECONDS)!!

            assertTrue(req1.path!!.startsWith("/token?"))
            assertTrue(req2.path!!.startsWith("/censor?"))
            assertTrue(req3.path!!.startsWith("/censor?"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `expired token is fetched again`() {
        val server = MockWebServer()
        server.start()
        try {
            // expires_in=0 -> 立刻过期, 每次调用都重新获取 token
            server.enqueue(tokenResponse(expiresIn = 0))
            server.enqueue(censorResponse("""{"conclusionType":1,"conclusion":"合规","data":[]}"""))
            server.enqueue(tokenResponse(expiresIn = 0))
            server.enqueue(censorResponse("""{"conclusionType":1,"conclusion":"合规","data":[]}"""))

            val client = clientWith(server)
            client.checkText("first")
            client.checkText("second")

            val paths = (0 until 4).map { server.takeRequest(5, TimeUnit.SECONDS)!!.path!!.substringBefore("?") }
            assertEquals(listOf("/token", "/censor", "/token", "/censor"), paths)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `token endpoint http error returns null`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

            val client = clientWith(server)
            assertNull(client.checkText("hello"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `token endpoint error json returns null`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"error":"invalid_client","error_description":"unknown"}"""))

            val client = clientWith(server)
            assertNull(client.checkText("hello"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `censor endpoint http error returns null`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(tokenResponse())
            server.enqueue(MockResponse().setResponseCode(502).setBody("bad gateway"))

            val client = clientWith(server)
            assertNull(client.checkText("hello"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `censor endpoint error_code returns null`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(tokenResponse())
            server.enqueue(censorResponse("""{"error_code":18,"error_msg":"Open api qps request limit reached"}"""))

            val client = clientWith(server)
            assertNull(client.checkText("hello"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `malformed censor body returns null`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(tokenResponse())
            server.enqueue(censorResponse("garbage"))

            val client = clientWith(server)
            assertNull(client.checkText("hello"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `missing token in response returns null`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"expires_in":3600}"""))

            val client = clientWith(server)
            assertNull(client.checkText("hello"))
        } finally {
            server.shutdown()
        }
    }
}
