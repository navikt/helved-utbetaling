package abetal

import abetal.models.*
import jakarta.xml.bind.JAXBContext
import jakarta.xml.bind.JAXBElement
import jakarta.xml.bind.Marshaller
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import models.*
import libs.kafka.*
import libs.kafka.stream.MappedStream
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningRequest
import no.trygdeetaten.skjema.oppdrag.Oppdrag
import java.util.UUID
import javax.xml.namespace.QName
import javax.xml.stream.XMLInputFactory
import javax.xml.transform.stream.StreamSource
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.ObjectFactory
import kotlin.reflect.KClass
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer

object Topics {
    val aap = Topic("helved.aap-utbetalinger.v1", json<AapUtbetaling>())
    val utbetalinger = Topic("helved.utbetalinger.v1", json<Utbetaling>())
    val oppdrag = Topic("helved.oppdrag.v1", xml<Oppdrag>())
    val simulering = Topic("helved.simulering.v1", jaxb<SimulerBeregningRequest, ObjectFactory>())
    val status = Topic("helved.status.v1", json<StatusReply>())
    val saker = Topic("helved.saker.v1", jsonjson<SakKey, SakValue>())
}

object Tables {
    val utbetalinger = Table(Topics.utbetalinger)
    val saker = Table(Topics.saker)
}

fun createTopology(): Topology = topology {
    val utbetalinger = consume(Tables.utbetalinger)
    val saker = consume(Tables.saker)
    aapStream(utbetalinger, saker)
    utbetalingToSak(utbetalinger, saker)
}

data class UtbetalingTuple(
    val uid: UUID,
    val utbetaling: Utbetaling,
)

fun utbetalingToSak(utbetalinger: KTable<String, Utbetaling>, saker: KTable<SakKey, SakValue>) {
    utbetalinger
        .toStream()
        .map { key, utbetaling -> UtbetalingTuple(UUID.fromString(key), utbetaling) }
        .rekey { (_, utbetaling) -> SakKey(utbetaling.sakId, Fagsystem.from(utbetaling.stønad)) }
        .leftJoin(jsonjson(), saker)
        .map { (uid, _), prevSak -> when (prevSak) {
                null -> SakValue(setOf(UtbetalingId(uid)))
                else -> SakValue(prevSak.uids + UtbetalingId(uid))
            }
        }
        .produce(Topics.saker)
}

data class AapTuple(
    val uid: String,
    val aap: AapUtbetaling,
)

fun Topology.aapStream(utbetalinger: KTable<String, Utbetaling>, saker: KTable<SakKey, SakValue>) {
    consume(Topics.aap)
        .map { key, aap -> AapTuple(key, aap) }
        .rekey { (_, aap) -> SakKey(aap.sakId, Fagsystem.from(aap.stønad)) }
        .leftJoin(jsonjson(), saker)
        .map(::toDomain)
        .rekey { utbetaling -> utbetaling.uid.id.toString() }
        .leftJoin(json(), utbetalinger)
        .branch({ (new, _) -> new.simulate }, ::simuleringStream)
        .default(::oppdragStream)
}

fun oppdragStream(branched: MappedStream<String, StreamsPair<Utbetaling, Utbetaling?>>) {
    branched.map { (new, prev) ->
        Result.catch {
            new.validate(prev)
            val new = new.copy(perioder = new.perioder.aggreger(new.periodetype))
            val oppdrag = when (new.action) {
                Action.CREATE -> OppdragService.opprett(new)
                Action.UPDATE -> OppdragService.update(new, prev ?: notFound("previous utbetaling"))
                Action.DELETE -> OppdragService.delete(new, prev ?: notFound("previous utbetaling"))
            }
            val lastPeriodeId = PeriodeId.decode(oppdrag.oppdrag110.oppdragsLinje150s.last().delytelseId)
            val utbetaling = new.copy(lastPeriodeId = lastPeriodeId)
            utbetaling to oppdrag
        }
    }.branch({ it.isOk() }) {
        val result = this.map { it -> it.unwrap() }
        result.map { (utbetaling, _) -> utbetaling }.produce(Topics.utbetalinger)
        result.map { (_, oppdrag) -> oppdrag }.produce(Topics.oppdrag)
        result.map { (_, _) -> StatusReply() }.produce(Topics.status)
    }.default {
        map { it -> it.unwrapErr() }.produce(Topics.status)
    }
}

fun simuleringStream(branched: MappedStream<String, StreamsPair<Utbetaling, Utbetaling?>>) {
    branched.map { (new, prev) ->
        Result.catch {
            new.validate(prev)
            val new = new.copy(perioder = new.perioder.aggreger(new.periodetype))
            when (new.action) {
                Action.CREATE -> SimuleringService.opprett(new)
                Action.UPDATE -> SimuleringService.update(new, prev ?: notFound("previous utbetaling"))
                Action.DELETE -> SimuleringService.delete(new, prev ?: notFound("previous utbetaling"))
            }
        }
    }.branch( { it.isOk() }) {
        val result = this.map { it -> it.unwrap() }
        result.map { _ -> StatusReply() }.produce(Topics.status)
        result.produce(Topics.simulering)
    }.default {
        map { it -> it.unwrapErr() }.produce(Topics.status)
    }
}

inline fun <reified K: Any, reified V: Any> jsonjson() = Serdes(JsonSerde.jackson<K>(), JsonSerde.jackson<V>())


inline fun <reified V: Any, reified O: Any> jaxb(): Serdes<String, V> {
    return Serdes(StringSerde, JaxbSerde.serde<V, O>())
}

object JaxbSerde {
    inline fun <reified V : Any, reified O: Any> serde(): StreamSerde<V> = object : StreamSerde<V> {
        override fun serializer(): Serializer<V> = JaxbSerializer(V::class, O::class)
        override fun deserializer(): Deserializer<V> = JaxbDeserializer(V::class, O::class)
    }
}

class JaxbSerializer<T : Any, O: Any>(
    private val kclass: KClass<T>,
    objectFactory: KClass<O>,
) : Serializer<T> {
    private val ctx = JAXBContext.newInstance(kclass.java, objectFactory.java)
    private val marshaller = ctx.createMarshaller().apply { 
        setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
        setProperty(Marshaller.JAXB_FRAGMENT, true)
    }
    override fun serialize(topic: String, data: T?): ByteArray? {
        return data?.let {
            val q = QName(
                "http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt",
                "simulerBeregningRequest",
                "ns3"
            )
            val root = JAXBElement(q, kclass.java, data)
            val outStream = ByteArrayOutputStream()
            marshaller.marshal(root, outStream)
            outStream.toByteArray()
        }
    }
}

class JaxbDeserializer<T : Any, O: Any>(
    private val kclass: KClass<T>,
    objectFactory: KClass<O>,
) : Deserializer<T> {
    private val ctx = JAXBContext.newInstance(kclass.java, objectFactory.java)
    private val unmarshaller = ctx.createUnmarshaller()
    private val inputFactory = XMLInputFactory.newInstance()
    override fun deserialize(topic: String, data: ByteArray?): T? {
        return data?.let {
            val reader = inputFactory.createXMLStreamReader(StreamSource(ByteArrayInputStream(data)))
            val jaxb = unmarshaller.unmarshal(reader, kclass.java)
            reader.close()
            return jaxb.value
        }
    }
}
