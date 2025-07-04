package dev.benkelenski.booked.domain

data class GoogleBooksResponse(val items: List<DataBook>)

data class DataBook(val id: String, val kind: String, val volumeInfo: VolumeInfo)

data class VolumeInfo(
    val title: String,
    val authors: List<String>,
    val publisher: String?,
    val publishedDate: String?,
    val description: String?,
)
