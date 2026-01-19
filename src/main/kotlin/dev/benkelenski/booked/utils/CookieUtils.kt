package dev.benkelenski.booked.utils

import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.SameSite

object CookieUtils {
    fun accessTokenCookie(token: String): Cookie =
        Cookie(
            name = "access_token",
            value = token,
            path = "/",
            secure = true,
            httpOnly = true,
            sameSite = SameSite.Strict,
            maxAge = 15 * 60,
        )

    fun refreshTokenCookie(token: String): Cookie =
        Cookie(
            name = "refresh_token",
            value = token,
            path = "/",
            secure = true,
            httpOnly = true,
            sameSite = SameSite.Strict,
            maxAge = 7 * 24 * 60 * 60,
        )

    fun expireCookie(name: String): Cookie =
        Cookie(
            name = name,
            value = "", // empty value
            path = "/",
            maxAge = 0, // expire immediately
            secure = true,
            httpOnly = true,
            sameSite = SameSite.Strict,
        )
}
