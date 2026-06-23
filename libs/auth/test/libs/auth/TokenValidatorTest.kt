package libs.auth

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.get
import io.ktor.server.testing.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import io.ktor.client.request.bearerAuth
import kotlinx.serialization.json.Json

internal class TokenValidatorTest {

    private val azure = AzureFake()
    private val ktor = TestApplication { application { module(azure.config) } }.apply { runBlocking { start() }}

    private val client = ktor.createClient {
        install(ContentNegotiation) {
            json(kotlinxJsonConfig)
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
