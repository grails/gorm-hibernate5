package grails.gorm.tests.validation

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.GormSpec
import groovy.transform.EqualsAndHashCode
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.SessionFactory
import org.springframework.dao.DuplicateKeyException
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 29/05/2017.
 */
@Issue('https://github.com/grails/gorm-hibernate5/issues/36')
@Ignore("java.lang.IllegalStateException: Either class [grails.gorm.tests.validation.Thing] is not a domain class or GORM has not been initialized correctly or has already been shutdown. Ensure GORM is loaded and configured correctly before calling any methods on a GORM entity.")
class UniqueWithinGroupSpec extends Specification {

    @AutoCleanup @Shared HibernateDatastore hibernateDatastore = new HibernateDatastore(getClass().getPackage())
    @Shared SessionFactory sessionFactory = hibernateDatastore.sessionFactory

    @Rollback
    @Ignore("java.lang.IllegalStateException: Either class [grails.gorm.tests.validation.Thing] is not a domain class or GORM has not been initialized correctly or has already been shutdown. Ensure GORM is loaded and configured correctly before calling any methods on a GORM entity.")
    void "test insert"() {
        when:
        Thing thing1 = new Thing(hello: 1, world: 2)
        thing1.insert(flush: true)
        sessionFactory.currentSession.flush()
        Thing thing2 = new Thing(hello: 1, world: 2)
        thing2.insert(flush: true)

        then:
        notThrown(DuplicateKeyException)
        !thing1.hasErrors()
        thing2.hasErrors()

    }

    @Rollback
    @Ignore("java.lang.IllegalStateException: Either class [grails.gorm.tests.validation.Thing] is not a domain class or GORM has not been initialized correctly or has already been shutdown. Ensure GORM is loaded and configured correctly before calling any methods on a GORM entity.")
    void "test save"() {
        when:
        Thing thing1 = new Thing(hello: 1, world: 2)
        thing1.save(insert: true, flush: true)
        sessionFactory.currentSession.flush()
        Thing thing2 = new Thing(hello: 1, world: 2)
        thing2.save(insert: true, flush: true)

        then:
        notThrown(DuplicateKeyException)
        !thing1.hasErrors()
        thing2.hasErrors()

    }

    @Rollback
    @Ignore("java.lang.IllegalStateException: Either class [grails.gorm.tests.validation.Thing] is not a domain class or GORM has not been initialized correctly or has already been shutdown. Ensure GORM is loaded and configured correctly before calling any methods on a GORM entity.")
    void "test validate"() {
        when:
        Thing thing1 = new Thing(hello: 1, world: 2).save(insert: true, flush: true)
        sessionFactory.currentSession.flush()
        Thing thing2 = new Thing(hello: 1, world: 2)

        then:
        !thing1.hasErrors()
        !thing2.validate()
        thing2.errors.getFieldError('hello').code == 'unique'
    }
}



@Entity
@EqualsAndHashCode(includes = ['hello', 'world'])
class Thing implements Serializable {
    Long hello
    Long world

    static constraints = {
        hello unique: 'world'
    }
    static mapping = {
        version false
        id composite: ['hello', 'world']
    }
}
