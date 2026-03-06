package statistikkern

import com.google.cloud.NoCredentials
import com.google.cloud.bigquery.BigQueryOptions
import com.google.cloud.bigquery.DatasetInfo
import org.testcontainers.containers.BigQueryEmulatorContainer

object BigQueryEmulator {
    private val container = BigQueryEmulatorContainer("ghcr.io/goccy/bigquery-emulator:0.6.6")
        .apply { start() }

    val projectId: String = container.projectId
    val datasetName: String = "helved_stats"

    val bigQuery = BigQueryOptions.newBuilder()
        .setProjectId(projectId)
        .setHost(container.emulatorHttpEndpoint)
        .setLocation(container.emulatorHttpEndpoint)
        .setCredentials(NoCredentials.getInstance())
        .build()
        .service
        .also { bq ->
            bq.create(DatasetInfo.newBuilder(datasetName).build())
        }
}