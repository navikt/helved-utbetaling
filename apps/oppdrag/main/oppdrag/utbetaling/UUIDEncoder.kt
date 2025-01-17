import libs.utils.logger
import java.nio.ByteBuffer
import java.util.Base64
import java.util.UUID


internal object UUIDEncoder {
    private val logger = logger("UUIDEncoder")

    fun UUID.encode(): String {
        val byteBuffer =
            ByteBuffer.allocate(java.lang.Long.BYTES * 2).apply {
                putLong(mostSignificantBits)
                putLong(leastSignificantBits)
            }

        return Base64.getEncoder().encodeToString(byteBuffer.array())
    }

    fun String.decode(): UUID? {
        return try {
            val byteBuffer: ByteBuffer = ByteBuffer.wrap(Base64.getDecoder().decode(this))

            UUID(byteBuffer.long, byteBuffer.long)
        } catch (_: Throwable) {
            logger.warn("Klarte ikke dekomprimere UUID: $this")
            return null
        }
    }
}