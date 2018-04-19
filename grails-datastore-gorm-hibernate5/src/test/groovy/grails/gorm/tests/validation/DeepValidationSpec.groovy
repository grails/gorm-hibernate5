package grails.gorm.tests.validation

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by francoiskha on 19/04/18.
 */
class DeepValidationSpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore hibernateDatastore = new HibernateDatastore(Market, Address)

    @Rollback
    @Issue('https://github.com/grails/grails-data-mapping/issues/1033')
    void "performs deep validation correctly"() {
        given: "a market with and invalid address"
        Market m = new Market(address: new Address(notNullableMember: null))

        when: "save market without deepValidate"
        m.save(deepValidate:false)

        then: "market is saved, no validation error"
        Market.count() == 1
        m.errors?.allErrors != []
    }
}

@Entity
class Market {

    Address address

}

@Entity
class Address {

    String notNullableMember

    static constraints = {
        notNullableMember nullable: false
    }
}