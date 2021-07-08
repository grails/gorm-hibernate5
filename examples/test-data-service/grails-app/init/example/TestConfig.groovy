package example

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration

@Configuration
class TestConfig {

    @Autowired
    BookService bookService

}
