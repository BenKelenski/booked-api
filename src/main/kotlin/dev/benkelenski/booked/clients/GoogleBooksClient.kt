package dev.benkelenski.booked.clients

import dev.benkelenski.booked.models.DataBook
import jdk.internal.org.jline.utils.Log.error
import org.http4k.core.*
import org.http4k.format.Moshi.auto
import org.http4k.lens.Query
import org.http4k.lens.string

val queryLens = Query.string().required("q")
val projectionLens = Query.string().optional("projection")
val apikeyLens = Query.string().required("key")
val dataBooksLens = Body.auto<Array<DataBook>>().toLens()

class GoogleBooksClient(
    private val host: Uri,
    val apiKey: String,
    private val internet: HttpHandler,
) {

    fun search(query: String = ""): List<DataBook> {
        val request =
            Request(Method.GET, host.path("/books/v1/volumes"))
                .with(queryLens of query)
                .with(projectionLens of "lite")
                .with(apikeyLens of apiKey)

        val response = internet(request)

        if (!response.status.successful) error(response)

        return dataBooksLens(response).toList()
    }
}
