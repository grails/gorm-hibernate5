package grails.gorm.tests.validation

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 03/05/2017.
 */
class SaveWithInvalidEntitySpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(getClass().getPackage())

    @Rollback
    void "test save with an invalid entity"() {
        when:
        hibernateDatastore.currentSession.persist(new A(b:new B(field2: "test")))
        hibernateDatastore.currentSession.flush()

        then:
        A.count() == 1

    }
}

@Entity
class A {
    B b
}
@Entity
class B {
    String field1
    String field2
}