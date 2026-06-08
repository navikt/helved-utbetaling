import org.junit.jupiter.api.Test
import java.io.File

class DiagramTest {

    @Test
    fun `generate diagrams`() {
        val mermaid = TestRuntime.kafka.visulize().mermaid().generateDiagram(disableJobs = true)
        File("../../dokumentasjon/utsjekk.mmd").apply { writeText(mermaid) }

        val uml = TestRuntime.kafka.visulize().uml()
        File("../../dokumentasjon/utsjekk.puml").apply { writeText(uml) }

        val desc = TestRuntime.kafka.visulize().desc()
        File("../../dokumentasjon/utsjekk.desc").apply { writeText(desc) }
    }
}

