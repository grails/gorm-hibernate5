package grails.gorm.tests.inheritance

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.GormSpec
import spock.lang.Issue

/**
 * Created by graemerocher on 29/05/2017.
 */
@Issue('https://github.com/grails/grails-data-mapping/issues/937')
class TablePerConcreteClassAndDateCreatedSpec extends GormSpec {
    void "should set the dateCreated automatically"() {
        given:
        Spaceship ship = new Spaceship(name: "Heart of Gold")
        ship.save(flush: true)

        expect:
        ship.dateCreated != null
    }

    void "should set the dateCreated automatically on update"() {
        given:
        Spaceship ship = new Spaceship(name: "Heart of Gold")
        ship.save()

        when:
        ship.name = "Heart of Gold II"
        ship.save(flush: true)

        then:
        // DataIntegrityViolationException is thrown:
        // NULL not allowed for column "DATE_CREATED"
        ship.dateCreated != null
    }

    @Override
    List getDomainClasses() {
        [Vehicle, Spaceship]
    }
}

@Entity
abstract class Vehicle {
    String name
    Date dateCreated

    static mapping = {
        tablePerConcreteClass true
        dynamicUpdate true
        id generator: 'increment'
    }
}

// @Entity
// https://issues.apache.org/jira/browse/GROOVY-5106 - The interface GormEntity cannot be implemented more than once with different arguments: org.grails.datastore.gorm.GormEntity<grails.gorm.tests.XXX> and org.grails.datastore.gorm.GormEntity<grails.gorm.tests.XXX>
class Spaceship extends Vehicle {
    static mapping = {
        dynamicUpdate true
    }
}