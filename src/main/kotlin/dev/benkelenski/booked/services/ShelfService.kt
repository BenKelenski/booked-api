package dev.benkelenski.booked.services

import dev.benkelenski.booked.models.Shelf
import dev.benkelenski.booked.models.ShelfRequest
import dev.benkelenski.booked.repos.ShelfRepo

/** alias for [ShelfService.getShelf] */
typealias GetShelf = (id: Int) -> Shelf?

/** alias for [ShelfService.getAllShelves] */
typealias GetAllShelves = () -> List<Shelf>

/** alias for [ShelfService.createShelf] */
typealias CreateShelf = (shelfRequest: ShelfRequest) -> Shelf?

/** alias for [ShelfService.deleteShelf] */
typealias DeleteShelf = (id: Int) -> Boolean

class ShelfService(private val shelfRepo: ShelfRepo) {

    fun getShelf(id: Int): Shelf? = shelfRepo.getShelfById(id)

    fun getAllShelves(): List<Shelf> = shelfRepo.getAllShelves()

    fun createShelf(shelfRequest: ShelfRequest): Shelf? =
        shelfRepo.addShelf(shelfRequest.name, shelfRequest.description)

    fun deleteShelf(id: Int): Boolean = shelfRepo.deleteShelf(id) == 1
}
