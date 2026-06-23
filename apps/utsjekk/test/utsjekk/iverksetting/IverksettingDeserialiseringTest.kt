package utsjekk.iverksetting

import libs.kotlinx.KotlinxJson
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class IverksettingDeserialiseringTest {

    @Test
    fun `deserialiser JSON til Iverksett, forvent ingen unntak`() {
        val json = readResource("json/IverksettEksempel.json")
        val iverksetting = KotlinxJson.decodeFromString<Iverksetting>(json)
        assertNotNull(iverksetting)
    }

    private fun readResource(name: String): String {
        return this::class.java.classLoader.getResource(name)!!.readText(StandardCharsets.UTF_8)
    }
}
