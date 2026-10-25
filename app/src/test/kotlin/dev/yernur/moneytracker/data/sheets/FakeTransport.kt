package dev.yernur.moneytracker.data.sheets

/** Records every request and answers from [responder]; stands in for the network. */
class FakeTransport(private val responder: (HttpRequest) -> HttpResponse) : HttpTransport {
    val requests = mutableListOf<HttpRequest>()

    override fun execute(request: HttpRequest): HttpResponse {
        requests += request
        return responder(request)
    }
}

fun ok(json: String = "{}") = HttpResponse(200, json)
