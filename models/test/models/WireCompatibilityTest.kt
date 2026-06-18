package models

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test
import models.kontrakter.BrukersNavKontor
import models.kontrakter.Ident
import libs.kotlinx.KotlinxJson
import java.time.LocalDateTime

class WireCompatibilityTest {

    @Test 
    fun `deserialize jackson-produced Ident as object`() {
        // Jackson serialized Ident as {"verdi":"12345678901"}
        val json = """{"verdi":"22479409483"}"""
        val ident: Ident = KotlinxJson.decodeFromString(json)
        assertEquals("22479409483", ident.verdi)
    }

    @Test 
    fun `deserialize Ident as plain string`() {
        val json = """"22479409483""""
        val ident: Ident = KotlinxJson.decodeFromString(json)
        assertEquals("22479409483", ident.verdi)
    }

    @Test 
    fun `LocalDateTime parses ISO with Z suffix`() {
        val json = """"2024-01-15T10:30:00Z""""
        assertEquals(LocalDateTime.of(2024,1,15,10,30), KotlinxJson.decodeFromString(json))
    }

    @Test 
    fun `LocalDateTime parses without Z`() { 
        val json = """"2026-02-10T20:26:00.0""""
        assertEquals(LocalDateTime.of(2026,2,10,20,26), KotlinxJson.decodeFromString(json))
    }

    @Test 
    fun `missing optional fields deserialize as null`() {
        // Minimal JSON without optional fields
        val json = """{"enhet":"0301"}"""
        val dto = KotlinxJson.decodeFromString<BrukersNavKontor>(json)
        assertNull(dto.gjelderFom)
    }
}
