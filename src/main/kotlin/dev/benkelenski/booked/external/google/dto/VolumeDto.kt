package dev.benkelenski.booked.external.google.dto

data class VolumeDto(val id: String, val kind: String, val volumeInfo: VolumeInfoDto)

data class VolumeInfoDto(
    val title: String,
    val authors: List<String>,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val description: String? = null,
    val printType: String? = null,
    val pageCount: Int? = null,
    val mainCategory: String? = null,
    val categories: List<String>? = null,
    val averageRating: Double? = null,
    val ratingsCount: Int? = null,
    val imageLinks: ImageLinksDto? = null,
    val industryIdentifiers: List<IndustryIdentifierDto>? = null,
    val language: String? = null,
    val dimensionsDto: DimensionsDto? = null,
)
