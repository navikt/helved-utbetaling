package abetal

import org.junit.jupiter.api.Test
import java.io.File

class DiagramTest {

    @Test
    fun `generate diagram`() {
        val mermaid = TestRuntime.kafka.visulize().mermaid().generateDiagram(disableJobs = true)
        File("../../dokumentasjon/abetal.mmd").apply { writeText(mermaid) }

        val uml = TestRuntime.kafka.visulize().uml()
        File("../../dokumentasjon/abetal.puml").apply { writeText(uml) }
    }
}

