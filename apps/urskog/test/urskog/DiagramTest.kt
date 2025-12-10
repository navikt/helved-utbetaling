package urskog

import org.junit.jupiter.api.Test
import java.io.File

class DiagramTest {

    @Test
    fun `generate diagram`() {
        val mermaid = TestRuntime.kafka.visulize().mermaid().generateDiagram(disableJobs = true)
        File("../../dokumentasjon/urskog.mmd").apply { writeText(mermaid) }

        val uml = TestRuntime.kafka.visulize().uml()
        File("../../dokumentasjon/urskog.puml").apply { writeText(uml) }

        val desc = TestRuntime.kafka.visulize().desc()
        File("../../dokumentasjon/urskog.desc").apply { writeText(desc) }
    }
}

