package dev.benkelenski.booked.models

data class DataBook(val id: String, val kind: String, val volumeInfo: VolumeInfo)

data class VolumeInfo(
    val title: String,
    val authors: List<String>,
    val publisher: String,
    val publishedDate: String,
    val description: String,
)
