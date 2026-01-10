package dev.benkelenski.booked

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addResourceSource
import dev.benkelenski.booked.auth.GoogleAuthProvider
import dev.benkelenski.booked.auth.JwtTokenProvider
import dev.benkelenski.booked.auth.TokenProvider
import dev.benkelenski.booked.external.google.GoogleBooksClient
import dev.benkelenski.booked.middleware.authMiddleware
import dev.benkelenski.booked.repos.BookRepo
import dev.benkelenski.booked.repos.RefreshTokenRepo
import dev.benkelenski.booked.repos.ShelfRepo
import dev.benkelenski.booked.repos.UserRepo
import dev.benkelenski.booked.routes.*
import dev.benkelenski.booked.services.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.client.OkHttp
import org.http4k.core.*
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.jetbrains.exposed.sql.Database
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

/** Constants used throughout the application */
private object App {
    // Constants
    const val API_PREFIX = "/api/v1"
    const val DEFAULT_CONFIG_PATH = "/application.conf"
    const val PROFILE_CONFIG_PATH_FORMAT = "/application-%s.conf"

    val tokenProvider: TokenProvider by lazy { JwtTokenProvider() }
    val httpClient: HttpHandler by lazy { OkHttp() }
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
        val configPath =
            profile?.let { App.PROFILE_CONFIG_PATH_FORMAT.format(it) } ?: App.DEFAULT_CONFIG_PATH

        ConfigLoaderBuilder.default()
            .addResourceSource(configPath)
            .build()
            .loadConfigOrThrow<Config>()
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
 * @param tokenProvider JWT token provider for access/refresh token generation
 * @return Configured RoutingHttpHandler
 */
fun createApp(
    config: Config,
    internet: HttpHandler = App.httpClient,
    tokenProvider: TokenProvider = App.tokenProvider,
): RoutingHttpHandler {

    // Repos
    val userRepo = UserRepo()
    val refreshTokenRepo = RefreshTokenRepo()
    val bookRepo = BookRepo()
    val shelfRepo = ShelfRepo()

    // Auth Providers
    val googleAuthProvider =
        GoogleAuthProvider(
            publicKey = config.server.auth.google.publicKey,
            jwksUri = config.server.auth.google.jwksUri,
            issuer = config.server.auth.google.issuer,
            audience = config.server.auth.google.audience,
        )

    // External
    val googleBooksClient =
        GoogleBooksClient(
            host = config.client.googleApisHost.toUri(),
            apiKey = config.client.googleApisKey,
            internet = internet,
        )

    // Services
    val authService =
        AuthService(
            userRepo = userRepo,
            refreshTokenRepo = refreshTokenRepo,
            shelfRepo = shelfRepo,
            googleAuthProvider = googleAuthProvider,
            tokenProvider = tokenProvider,
        )

    val bookService = BookService(bookRepo = bookRepo, shelfRepo = shelfRepo)

    val shelfService =
        ShelfService(
            shelfRepo = shelfRepo,
            bookRepo = bookRepo,
            googleBooksClient = googleBooksClient,
        )

    val googleBooksService = GoogleBooksService(googleBooksClient)

    val userService = UserService(userRepo = userRepo)

    val authMiddleware = authMiddleware(tokenProvider)

    return App.API_PREFIX bind
        routes(
            authRoutes(
                registerWithEmail = authService::registerWithEmail,
                loginWithEmail = authService::loginWithEmail,
                authenticateWith = authService::authenticateWith,
                refresh = authService::refresh,
                checkAuthStatus = authService::checkAuthStatus,
                logout = authService::logout,
                authMiddleware = authMiddleware,
            ),
            bookRoutes(
                findBookById = bookService::findBookById,
                findBooksByShelf = bookService::findBooksByShelf,
                moveBook = bookService::moveBook,
                updateBookProgress = bookService::updateBookProgress,
                deleteBook = bookService::deleteBook,
                completeBook = bookService::completeBook,
                authMiddleware = authMiddleware,
            ),
            shelfRoutes(
                findShelfById = shelfService::findShelfById,
                findShelvesByUserId = shelfService::findShelvesByUserId,
                createShelf = shelfService::createShelf,
                deleteShelf = shelfService::deleteShelf,
                //                findBooksByShelf = shelfService::findBooksByShelf,
                addBookToShelf = shelfService::addBookToShelf,
                authMiddleware = authMiddleware,
            ),
            googleBooksRoutes(
                searchWithQuery = googleBooksService::searchWithQuery,
                fetchByVolumeId = googleBooksService::fetchByVolumeId,
            ),
            userRoutes(
                getUserById = userService::getUserById,
                authMiddleware = authMiddleware,
            ),
        )
}

fun main() {
    try {
        // Load configuration
        logger.info { "Loading configuration" }
        val config = loadConfig()
        logger.info { "Configuration loaded successfully" }

        // Set up database
        logger.info { "Establishing database connection" }
        createDbConn(config = config)

        // Create and configure the application
        logger.info { "Configuring application" }
        val app =
            createApp(config = config, internet = App.httpClient, tokenProvider = App.tokenProvider)

        logger.info { "Starting server on port: ${config.server.port}" }

        routes(
                app,
                "/health" bind
                    Method.GET to
                    { _: Request ->
                        Response(Status.OK).body("{\"status\":\"UP\"}")
                    },
            )
            .asServer(Jetty(config.server.port))
            .start()
        logger.info { "Server started successfully" }
    } catch (e: Exception) {
        logger.error(e) { "Failed to start application" }
        exitProcess(1)
    }
}

/** Extension function to convert String to URI */
fun String.toUri(): Uri = Uri.of(this)
