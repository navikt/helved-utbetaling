package snickerboa

import org.http4k.format.ConfigurableKotlinxSerialization

object KotlinxJson : ConfigurableKotlinxSerialization({
    ignoreUnknownKeys = true
    encodeDefaults = true
    serializersModule = libs.kotlinx.KotlinxJson.serializersModule
})
