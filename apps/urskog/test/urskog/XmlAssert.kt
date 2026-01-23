import libs.xml.XMLMapper

object XmlAssert {
    inline fun <reified V: Any> assertEquals(expected: V, actual: V) {
        val mapper = XMLMapper<V>()
        val left = mapper.writeValueAsString(expected)
        val right = mapper.writeValueAsString(actual)
        kotlin.test.assertEquals(left, right)
    }
}

