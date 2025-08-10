package testUtils

import dev.benkelenski.booked.domain.User
import dev.benkelenski.booked.repos.GetOrCreateUserResult

fun GetOrCreateUserResult.requireCreated(): User =
    when (this) {
        is GetOrCreateUserResult.Created -> user
        is GetOrCreateUserResult.Existing ->
            error("Expected Created, got Existing for userId=${user.id}")
    }
