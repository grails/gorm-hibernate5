package grails.gorm.tests.jpa

import grails.transaction.Rollback
import groovy.transform.NotYetImplemented
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.OneToMany

/**
 * Created by graemerocher on 22/12/16.
 */
class SimpleJpaEntitySpec extends Specification {


    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(Customer)
    @Shared PlatformTransactionManager transactionManager = hibernateDatastore.getTransactionManager()

    @Rollback
    @NotYetImplemented
    void "test that JPA entities can be treated as GORM entities"() {
        when:"A basic entity is persisted"
        Customer c = new Customer(firstName: "Fred", lastName: "Flinstone")
        c.save(flush:true)

        then:"The object was saved"
        !c.errors.hasErrors()
        Customer.count() == 1
    }
}
@Entity
class Customer {

    @Id
    @GeneratedValue
    Long myId
    String firstName
    String lastName

    @OneToMany
    Set<Customer> related

}