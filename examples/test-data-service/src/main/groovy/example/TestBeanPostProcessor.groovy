package example

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.stereotype.Component

@Component
class TestBeanPostProcessor implements BeanPostProcessor {

    @Autowired
    BookService bookService

}
