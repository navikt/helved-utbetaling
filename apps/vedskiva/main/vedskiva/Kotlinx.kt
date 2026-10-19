package vedskiva

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import no.nav.virksomhet.tjenester.avstemming.meldinger.v1.Avstemmingsdata
import org.http4k.format.ConfigurableKotlinxSerialization

object AvstemmingsdataSerializer : KSerializer<Avstemmingsdata> {
    private val xmlMapper = libs.xml.XMLMapper<Avstemmingsdata>()
    override val descriptor = PrimitiveSerialDescriptor("Avstemmingsdata", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Avstemmingsdata) = encoder.encodeString(xmlMapper.writeValueAsString(value))
    override fun deserialize(decoder: Decoder): Avstemmingsdata = xmlMapper.readValue(decoder.decodeString().toByteArray())!!
}

object VedskivaJson : ConfigurableKotlinxSerialization({
    ignoreUnknownKeys = true
    encodeDefaults = true
    allowStructuredMapKeys = true
    serializersModule = SerializersModule {
        include(libs.kotlinx.KotlinxJson.serializersModule)
        contextual(Avstemmingsdata::class, AvstemmingsdataSerializer)
    }
})
