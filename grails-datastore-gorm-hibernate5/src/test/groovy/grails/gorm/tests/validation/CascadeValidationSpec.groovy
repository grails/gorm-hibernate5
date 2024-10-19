package grails.gorm.tests.validation

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 04/05/2017.
 */
class CascadeValidationSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(Business, Person, Employee)

    @Rollback
    @Issue('https://github.com/grails/grails-data-mapping/issues/926')
    @Ignore("groovy.lang.MissingPropertyException: No such property: business for class: grails.gorm.tests.validation.Employee")
    void "validation cascades correctly"() {
        given: "an invalid business"
        Business b = new Business(name: null)

        and: "a valid employee that belongs to the business"
        Person p = new Employee(business: b)
        b.addToPeople(p)

        when:
        b.save()

        then:
        b.errors.hasFieldErrors('name')
        b.hasErrors()
    }
}
@Entity
class Business {

    String name

    static hasMany = [
            people: Person
    ]

}
@Entity
abstract class Person {

}

// @Entity
// https://issues.apache.org/jira/browse/GROOVY-5106 - The interface GormEntity cannot be implemented more than once with different arguments: org.grails.datastore.gorm.GormEntity<grails.gorm.tests.XXX> and org.grails.datastore.gorm.GormEntity<grails.gorm.tests.XXX>
class Employee extends Person {

    static belongsTo = [
            business: Business
    ]

}