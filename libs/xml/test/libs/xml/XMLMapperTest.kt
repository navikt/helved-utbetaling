package libs.xml

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import javax.xml.bind.annotation.XmlRootElement
import kotlin.test.assertEquals

class XMLMapperTest {
    private val mapper = XMLMapper<TestXml>()

    @Test
    fun `read value`() {
        val expected = TestXml(1, "test")
        val actual = mapper.readValue(xml)
        assertEquals(expected, actual)
    }

    @Test
    fun `write value as string`() {
        val actual = mapper.writeValueAsString(TestXml(1, "test"))
        val expected = xml
        assertEquals(expected, actual)
    }

    @Test
    fun `read and write concurrently`() = runTest {
        repeat(5) {
            launch {
                val testxml = TestXml(it, "hello")
                val xml = mapper.writeValueAsString(testxml)
                val actual = mapper.readValue(xml)
                assertEquals(testxml, actual)
            }
        }
    }
}

@XmlRootElement(name = "test")
private data class TestXml(
    var id: Int? = null,
    var name: String? = null,
)

@Language("XML")
private val xml = """
    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
    <test>
        <id>1</id>
        <name>test</name>
    </test>
    
""".trimIndent()