package example

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormEntity

/**
 * Created by graemerocher on 22/07/2016.
 */
@Entity
class Book implements GormEntity<Book> {
    @JsonView(BookView)
    Long id
    @JsonView(BookView)
    String title
}
