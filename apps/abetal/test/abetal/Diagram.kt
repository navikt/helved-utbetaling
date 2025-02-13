package abetal

import org.junit.jupiter.api.Test
import java.io.File

class Diagram {

    @Test
    fun `generate diagram`() {
        val mermaid = TestRuntime.kafka.visulize().mermaid().generateDiagram(disableJobs = true)
        File("../../dokumentasjon/topology.mmd").apply { writeText(mermaid) }

        val uml = TestRuntime.kafka.visulize().uml()
        File("../../dokumentasjon/topology.puml").apply { writeText(uml) }
    }
}

