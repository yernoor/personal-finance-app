package dev.yernur.moneytracker.data.sheets

import java.net.HttpURLConnection
import java.net.URL

class HttpRequest(val method: String, val url: String, val headers: Map<String, String>, val body: String?)

class HttpResponse(val code: Int, val body: String)

/** The one place that touches the network, so everything above it can be tested with a fake. */
fun interface HttpTransport {
    fun execute(request: HttpRequest): HttpResponse
}

/** Plain `HttpURLConnection`: no HTTP library needed for a handful of JSON calls. Blocking; call it off the main thread. */
class UrlConnectionTransport : HttpTransport {
    override fun execute(request: HttpRequest): HttpResponse {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000 // the Sheets API answers within 180 s at worst
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            request.body?.let {
                connection.doOutput = true
                connection.outputStream.use { out -> out.write(it.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            return HttpResponse(code, stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }
}
