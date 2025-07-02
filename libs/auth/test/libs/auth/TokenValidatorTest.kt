package libs.auth

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterAll
import kotlin.test.assertEquals
import io.ktor.serialization.jackson.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.get
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.server.testing.*
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import io.ktor.client.request.bearerAuth

class TokenValidatorTest {

    private val azure = AzureFake()
    private val ktor = TestApplication { application { module(azure.config) } }.apply { runBlocking { start() }}

    private val client = ktor.createClient {
        install(ContentNegotiation) {
            jackson {}
        }
    }

    @Test
    fun `can call open route`() {
        val res = runBlocking {
            client.get("/open") 
        }
        assertEquals(HttpStatusCode.OK, res.status)
    }

    @Test
    fun `can restrict secure route`() {
        val res = runBlocking {
            client.get("/secure") 
        }
        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }

    @Test
    fun `can call secure route`() {
        val res = runBlocking {
            client.get("/secure") {
                bearerAuth(azure.generateToken())
            } 
        }
        assertEquals(HttpStatusCode.OK, res.status)
    }
}

