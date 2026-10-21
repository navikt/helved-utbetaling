package smokesignal

import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import java.time.Duration

internal fun retryingHttpClient(
    client: HttpHandler = JavaHttpClient(
        httpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build(),
        requestModifier = { it.timeout(Duration.ofSeconds(60)) }
    ),
    pause: (Long) -> Unit = Thread::sleep
): HttpHandler {
    return { request ->
        var response = client(request)
        var retries = 0
        while (response.status.serverError && retries < 3) {
            pause(1000L shl retries)
            retries++
            response = client(request)
        }
        response
    }
}
