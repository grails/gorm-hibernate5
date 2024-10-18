package grails.gorm.tests.traits

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 02/05/2017.
 */
class TraitPropertySpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(getClass().getPackage())

    @Rollback
    @Ignore("java.lang.IllegalStateException: Either class [grails.gorm.tests.traits.EntityWithTrait] is not a domain class or GORM has not been initialized correctly or has already been shutdown. Ensure GORM is loaded and configured correctly before calling any methods on a GORM entity.")
    void "test entity with trait property"() {
        when:
        new EntityWithTrait(name: "test", bar: "test2").save(flush:true)
        EntityWithTrait obj = EntityWithTrait.first()

        then:
        obj.name == "test"
        obj.bar == "test2"
    }
}

trait Foo {
    String bar
}

@Entity
class EntityWithTrait implements Foo {
    String name
}
