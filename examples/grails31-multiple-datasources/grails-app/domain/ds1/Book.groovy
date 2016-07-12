package ds1

import org.grails.datastore.gorm.GormEntity

class Book implements GormEntity<Book>{

    String title
    
    static constraints = {
    }
}
