package example

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier

class TestBean {

    @Autowired
    BookService bookRepo

    @Autowired
    @Qualifier("bookService")
    BookService bookService

    void doSomething() {
        assert bookRepo != null
        bookRepo.get(1l)
    }

}
