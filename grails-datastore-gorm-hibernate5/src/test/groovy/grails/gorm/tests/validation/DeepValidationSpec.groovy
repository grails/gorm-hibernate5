package grails.gorm.tests.validation

import grails.gorm.annotation.Entity
import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.transactions.Rollback
import spock.lang.Issue

/**
 * Created by francoiskha on 19/04/18.
 */
class DeepValidationSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        return [Market, Address]
    }

    @Rollback
    @Issue('https://github.com/grails/grails-data-mapping/issues/1033')
    void "performs deep validation correctly"() {

        when: "save market with invalid address"
        new Market(name: "Main", address: new Address(streetName: "Main St.", landmark: "The Golder Gate Bridge", postalCode: "11")).save(deepValidate: false)

        then: "market is saved, no validation error"
        Market.count() == 1
    }
}

@Entity
class Market {

    String name
    Address address

}

@Entity
class Address {

    String streetName
    String landmark
    String postalCode

    private static final POSTAL_CODE_PATTERN = /^(\d{5}-\d{4})|(\d{5})|(\d{9})$/

    static constraints = {
        streetName nullable: false
        landmark nullable: true
        postalCode validator: { value -> value ==~ POSTAL_CODE_PATTERN }
    }
}