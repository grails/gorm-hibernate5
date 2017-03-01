package example

import groovy.transform.CompileStatic
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@CompileStatic
class BookController {

    @RequestMapping("/books")
    @Transactional(readOnly = true)
    List<Book> books() {
        Book.list()
    }
}
