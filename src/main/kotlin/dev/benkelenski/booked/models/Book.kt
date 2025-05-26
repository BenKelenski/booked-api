package dev.benkelenski.booked.models

import java.time.Instant

data class Book(
  val id: Int,
  val title: String,
  val author: String,
  val createdAt: Instant,
  //    val publisher: String,
  //    val isbn: String,
)

data class BookRequest(
  val title: String,
  val author: String,
  //    val publisher: String?,
  //    val isbn: String?,
)
