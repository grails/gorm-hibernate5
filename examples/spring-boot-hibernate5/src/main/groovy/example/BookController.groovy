package example

import com.fasterxml.jackson.annotation.JsonView
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Created by graemerocher on 22/07/2016.
 */
@RestController
class BookController {

    @RequestMapping("/books")
    @Transactional(readOnly = true)
    @JsonView(BookView)
    public List<Book> books() {
        Book.list()
    }


}
