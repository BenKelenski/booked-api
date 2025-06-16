package dev.benkelenski.booked

data class Config(val server: Server, val database: Database, val client: Client)

data class Server(val port: Int, val auth: Auth)

data class Auth(
    var publicKey: String?,
    val jwksUri: String,
    val issuer: String,
    val audience: String,
    val redirectUri: String,
)

data class Database(val url: String, val driver: String, val user: String, val password: String)

data class Client(val googleApisHost: String, val googleApisKey: String)
