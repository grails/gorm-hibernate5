package grails.gorm.tests.dirtychecking

import grails.gorm.annotation.Entity
import grails.gorm.dirty.checking.DirtyCheck
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 03/05/2017.
 */
class HibernateDirtyCheckingSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(Person, DirtyCheckingDummy)

    @Rollback
    @Issue('https://github.com/grails/grails-core/issues/10613')
    void    "Test that presence of beforeInsert doesn't impact dirty properties"() {
        given: 'a new person'
        def person = new Person(name: 'John', occupation: 'Grails developer').save(flush:true)

        when: 'the name is changed'
        person.name = 'Dave'

        then: 'the name field is dirty'
        person.getPersistentValue('name') == "John"
        person.dirtyPropertyNames.contains 'name'
        person.dirtyPropertyNames == ['name']
        person.isDirty('name')
        !person.isDirty('occupation')

        when:
        person.save(flush:true)

        then:
        person.getPersistentValue('name') == "Dave"
        person.dirtyPropertyNames == []
        !person.isDirty('name')
        !person.isDirty()

        when:
        person.occupation = "Civil Engineer"

        then:
        person.getPersistentValue('occupation') == "Grails developer"
        person.dirtyPropertyNames.contains 'occupation'
        person.dirtyPropertyNames == ['occupation']
        person.isDirty('occupation')
        !person.isDirty('name')
    }

    @Rollback
    void "test dirty checking on embedded"() {
        given: 'a new person'
        Person person = new Person(name: 'John', occupation: 'Grails developer', address: new Address(street: "Old Town", zip: "1234")).save(flush:true)

        when: 'the name is changed'
        person.address.street = "New Town"

        then:
        person.address.hasChanged()
        person.address.hasChanged("street")

        when:
        person.save(flush:true)
        hibernateDatastore.sessionFactory.currentSession.clear()
        person = Person.first()

        then:
        person.address.street == "New Town"


    }

    @Rollback
    void "test dirtyness of new instances"() {
        when:
        DirtyCheckingDummy dummy = new DirtyCheckingDummy(name: "dummy").save failOnError: true, flush: true

        then:
        !dummy.hasChanged()
        !dummy.dirty
    }
}


@Entity
class Person {

    String name
    String occupation

    Address address
    static embedded = ['address']

    static constraints = {
        address nullable:true
    }

    def beforeInsert() {
        // Do nothing
    }
}

@DirtyCheck
class Address {
    String street
    String zip
}

@Entity
class DirtyCheckingDummy {

    String name

    def beforeInsert() {
        assert hasChanged()
        assert !dirty
    }

}