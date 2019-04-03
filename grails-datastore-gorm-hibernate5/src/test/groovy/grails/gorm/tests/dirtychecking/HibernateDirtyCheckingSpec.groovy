package grails.gorm.tests.dirtychecking

import grails.gorm.annotation.Entity
import grails.gorm.dirty.checking.DirtyCheck
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 03/05/2017.
 */
class HibernateDirtyCheckingSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(Person)

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
    void    "test dirty check in gorm works properly with booleans"() {
        given: 'a new person'
        def person = new Person(name: 'John', occupation: 'Grails developer').save(flush:true)

        when: 'the name is changed'
        person.setAccountLocked(true) // should at least work with calling a setter

        then: 'the name field is dirty'
        person.getPersistentValue('accountLocked') == true
        person.dirtyPropertyNames.contains 'accountLocked'
        person.dirtyPropertyNames == ['accountLocked']
        person.isDirty('accountLocked')
        !person.isDirty('occupation')

        when:
        person.save(flush:true)

        then:
        person.getPersistentValue('accountLocked') == accountLocked
        person.dirtyPropertyNames == []
        !person.isDirty('accountLocked')
        !person.isDirty()

        when:
        person.setEnabled(true)

        then:
        person.getPersistentValue('enabled') == true
        person.dirtyPropertyNames.contains 'enabled'
        person.dirtyPropertyNames == ['enabled']
        person.isDirty('enabled')
        !person.isDirty('occupation')


    }

}


@Entity
class Person {

    String name
    String occupation
    boolean accountLocked
    Boolean enabled = false

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