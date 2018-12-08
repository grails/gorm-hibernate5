package functionaltests

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.*
import spock.lang.*
import example.Book
import ds2.Book as SecondBook

@Integration
@Rollback
class MultipleDataSourcesSpec extends Specification {

    void "Test multiple data source persistence"() {
        when:
            new Book(title:"One").save(flush:true)
            new Book(title:"Two").save(flush:true)
            SecondBook.withTransaction {
                new SecondBook(title:"Three").save(flush:true)
            }

        then:
            Book.count() == 2
            SecondBook.withTransaction(readOnly: true) { SecondBook.count() } == 1
            SecondBook.withTransaction(readOnly: true)  { SecondBook.secondary.count() } == 1
    }
}
