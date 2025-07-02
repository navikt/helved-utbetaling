package libs.utils

import org.junit.jupiter.api.*
import kotlin.test.assertEquals

class ResultTest {

    @Test
    fun `can catch in result is err`() {
        fun maybe(): Result<String, IllegalStateException> {
            return Result.catch { 
                error("wops")
            }
        }
        val msg = assertThrows<ResultException> {
            maybe().unwrap()
        }
        assertEquals("called Result.unwrap() on an Err: java.lang.IllegalStateException: wops", msg.message)
    }

    @Test
    fun `can catch in result is ok`() {
        fun maybe(): Result<String, IllegalStateException> {
            return Result.catch { 
                "ok"
            }
        }
        assertEquals("ok", maybe().unwrap())
    }

    @Test
    fun `can create ok`() {
        val result = Ok("ok")
        assertEquals("ok", result.value)
    }
    
    @Test
    fun `can create err`() {
        val result = Err("oh")
        assertEquals("oh", result.err)
    }

    @Test
    fun `can unwrap ok`() {
        val result = Ok("oh")
        assertEquals("oh", result.unwrap())
    }

    @Test
    fun `can map value in result`() {
        val result = Ok("oh")
        assertEquals("hey", result.map { "hey" }.unwrap())
    }

    @Test
    fun `or another result`() {
        val failingRes = Err("wops")
        val anotherOk = Ok("nice")
        val result = failingRes.or(anotherOk).unwrap()
        assertEquals("nice", result)
    }

    @Test
    fun `on success`() {
        val ok = Ok("nice")
        var success = ""
        ok.onSuccess { 
            success = "yes"
        }
        assertEquals("yes", success)
    }

    @Test
    fun `on failure`() {
        val err = Err("nice")
        var failure = ""
        err.onFailure { 
            failure = "yes"
        }
        assertEquals("yes", failure)
    }

    @Test
    fun `on success and failure`() {
        val res = Ok("nice")
        var success = ""
        var failure = ""
        res.onFailure { 
            failure = "yes"
        }.onSuccess { 
            success = "yes"
        }
        assertEquals("", failure)
        assertEquals("yes", success)
    }

    @Test
    fun `can fold`() {
        fun doSuccess(it: String) {
            assertEquals("yeyeye", it)
        }
        Ok("yeyeye").fold(::doSuccess) { 
            fail("oh no")
        }
    }

    @Test
    fun `can flatten`() {
        fun wrap(): Result<String, String> = Result.catch { 
            "hello"
        }
        val wrapped: Result<Result<String, String>, String> = Result.catch {
            wrap()
        }
        assertEquals("hello", wrapped.flatten().unwrap())
    }
}

