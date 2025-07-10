package dev.benkelenski.booked

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addResourceSource
import dev.benkelenski.booked.auth.GoogleAuthProvider
import dev.benkelenski.booked.clients.GoogleBooksClient
import dev.benkelenski.booked.repos.BookRepo
import dev.benkelenski.booked.repos.ShelfRepo
import dev.benkelenski.booked.repos.UserRepo
import dev.benkelenski.booked.routes.authRoutes
import dev.benkelenski.booked.routes.bookRoutes
import dev.benkelenski.booked.routes.shelfRoutes
import dev.benkelenski.booked.services.BookService
import dev.benkelenski.booked.services.ShelfService
import dev.benkelenski.booked.services.UserService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.client.OkHttp
import org.http4k.core.HttpHandler
import org.http4k.core.Uri
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.jetbrains.exposed.sql.Database

private val logger = KotlinLogging.logger {}

/** Constants used throughout the application */
private object AppConstants {
    const val API_PREFIX = "/api/v1"
    const val DEFAULT_CONFIG_PATH = "/application.conf"
    const val PROFILE_CONFIG_PATH_FORMAT = "/application-%s.conf"
}

/**
 * Combines routes with a common prefix
 *
 * @param prefix The common prefix for all routes
 * @param routes The routes to be combined
 * @return A RoutingHttpHandler with all routes under the specified prefix
 */
private fun withPrefix(prefix: String, vararg routes: RoutingHttpHandler): RoutingHttpHandler {
    return prefix bind routes(*routes)
}

/**
 * Loads application configuration from resources
 *
 * @param profile Optional profile name for environment-specific configuration
 * @return Loaded configuration
 * @throws Exception if configuration loading fails
 */
fun loadConfig(profile: String? = null): Config {
    return try {
        val loader = ConfigLoaderBuilder.default()
        if (profile != null) {
            loader
                .addResourceSource(AppConstants.PROFILE_CONFIG_PATH_FORMAT.format(profile))
                .build()
                .loadConfigOrThrow<Config>()
        } else {
            loader
                .addResourceSource(AppConstants.DEFAULT_CONFIG_PATH)
                .build()
                .loadConfigOrThrow<Config>()
        }
    } catch (e: Exception) {
        logger.error(e) { "Failed to load configuration" }
        throw e
    }
}

/**
 * Establishes database connection using the provided configuration
 *
 * @param config Application configuration containing database details
 * @throws Exception if a database connection fails
 */
fun createDbConn(config: Config) {
    try {
        Database.connect(
            url = config.database.url,
            driver = config.database.driver,
            user = config.database.user,
            password = config.database.password,
        )
    } catch (e: Exception) {
        logger.error(e) { "Failed to establish database connection" }
        throw e
    }
}

/**
 * Creates the main application with all routes and services
 *
 * @param config Application configuration
 * @param internet HTTP client for external communications
 * @return Configured RoutingHttpHandler
 */
fun createApp(config: Config, internet: HttpHandler): RoutingHttpHandler {

    val googleAuthProvider =
        GoogleAuthProvider(
            publicKey = config.server.auth.publicKey,
            jwksUri = config.server.auth.jwksUri,
            issuer = config.server.auth.issuer,
            audience = config.server.auth.audience,
            userRepo = UserRepo(),
        )

    val authProviders = mapOf("google" to googleAuthProvider)

    val bookService =
        BookService(
            bookRepo = BookRepo(),
            googleBooksClient =
                GoogleBooksClient(
                    host = config.client.googleApisHost.toUri(),
                    apiKey = config.client.googleApisKey,
                    internet = internet,
                ),
        )

    val shelfService = ShelfService(shelfRepo = ShelfRepo())

    val userService = UserService(userRepo = UserRepo())

    return withPrefix(
        AppConstants.API_PREFIX,
        bookRoutes(
            bookService::getBookById,
            bookService::getAllBooks,
            bookService::createBook,
            bookService::deleteBook,
            bookService::searchBooks,
            //            authProvider::verifyToken,
        ),
        shelfRoutes(
            shelfService::getShelfById,
            shelfService::getAllShelves,
            shelfService::createShelf,
            shelfService::deleteShelf,
            //            authProvider::verifyToken,
        ),
        authRoutes(
            userService::registerWithEmail,
            userService::loginWithEmail,
            userService::authenticateOrRegister,
            authProviders = authProviders,
        ),
    )
}

fun main() {
    try {
        val config = loadConfig()

        logger.info { "creating database connection" }
        createDbConn(config = config)
        logger.info { "creating app" }
        val app = createApp(config = config, internet = OkHttp())
        val webApp = webApp(config.server.auth.audience, config.server.auth.redirectUri.toUri())

        logger.info { "starting app on port: ${config.server.port}" }
        routes(app, webApp).asServer(Jetty(config.server.port)).start()
    } catch (e: Exception) {
        logger.error(e) { "Failed to start application" }
        throw e
    }
}

/** Extension function to convert String to URI */
fun String.toUri(): Uri = Uri.of(this)
