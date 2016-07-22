package example

import com.fasterxml.jackson.annotation.JsonView
import org.grails.datastore.mapping.engine.event.DatastoreInitializedEvent
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.event.EventListener
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

import javax.annotation.PostConstruct

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
