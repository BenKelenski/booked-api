package dev.benkelenski.booked.services

import dev.benkelenski.booked.models.Shelf
import dev.benkelenski.booked.models.ShelfRequest
import dev.benkelenski.booked.repos.ShelfRepo

class ShelfService(val shelfRepo: ShelfRepo) {

    fun getShelf(id: Int): Shelf? = shelfRepo.getShelfById(id)

    fun getAllShelves(): List<Shelf> = shelfRepo.getAllShelves()

    fun createShelf(shelfRequest: ShelfRequest): Shelf? =
        shelfRepo.addShelf(shelfRequest.name, shelfRequest.description)

    fun deleteShelf(id: Int): Boolean = shelfRepo.deleteShelf(id) == 1
}