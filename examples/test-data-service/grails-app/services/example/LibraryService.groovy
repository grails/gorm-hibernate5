package example

import grails.gorm.transactions.Transactional

@Transactional
class LibraryService {

    BookService bookService

    Boolean bookExists(Serializable id) {
        assert bookService != null
        bookService.get(id)
    }
}
