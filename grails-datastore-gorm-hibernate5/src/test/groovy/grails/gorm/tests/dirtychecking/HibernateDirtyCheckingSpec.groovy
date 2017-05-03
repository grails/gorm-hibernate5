package grails.gorm.tests.dirtychecking

import grails.gorm.annotation.Entity
import grails.transaction.Rollback
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
    @Shared PlatformTransactionManager transactionManager = hibernateDatastore.getTransactionManager()

    @Rollback
    @Issue('https://github.com/grails/grails-core/issues/10613')
    void "Test that presence of beforeInsert doesn't impact dirty properties"() {
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
}


@Entity
class Person {

    String name
    String occupation

    static constraints = {
    }

    def beforeInsert() {
        // Do nothing
    }
}