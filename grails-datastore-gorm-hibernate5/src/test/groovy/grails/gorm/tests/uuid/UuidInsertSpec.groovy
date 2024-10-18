package grails.gorm.tests.uuid

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 05/04/2017.
 */
class UuidInsertSpec extends Specification {
    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(getClass().getPackage())


    @Rollback
    @Issue('https://github.com/grails/grails-data-mapping/issues/902')
    @Ignore("java.lang.IllegalStateException: Either class [grails.gorm.tests.uuid.Person] is not a domain class or GORM has not been initialized correctly or has already been shutdown. Ensure GORM is loaded and configured correctly before calling any methods on a GORM entity.")
    void "Test UUID insert"() {
        when:"A UUID is used"
        Person p = new Person(name: "test").save(flush:true)

        then:"An update should not be triggered"
        p.id
        p.name == 'test'
    }
}

@Entity
class Person {
    UUID id
    String name

    def beforeUpdate() {
        name = "changed"
    }
    static mapping = {
        id generator : 'uuid2', type: 'uuid-binary'
    }
}
