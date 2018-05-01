package grails.gorm.tests.validation

import grails.gorm.annotation.Entity
import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.transactions.Rollback
import org.springframework.dao.DataIntegrityViolationException
import spock.lang.Issue

/**
 * Created by francoiskha on 19/04/18.
 */
class DeepValidationSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        return [City, Market, Address]
    }

    @Rollback
    @Issue('https://github.com/grails/grails-data-mapping/issues/1033')
    void "performs deep validation correctly"() {

        when: "save market with failing custom validator on child"
        new Market(name: "Main", address: new Address(streetName: "Main St.", landmark: "The Golder Gate Bridge", postalCode: "11")).save(deepValidate: false)

        then: "market is saved, no validation error"
        Market.count() == 1

        when: "save market with nullable on child"
        new Market(name: "NIT", address: new Address(landmark: "1B, Main St.", postalCode: "121001")).save(deepValidate: false)

        then:
        thrown(DataIntegrityViolationException)

        when: "nested validation fails"
        new City(name: "Faridabad").addToMarkets(name: "NIT 1", address: new Address(streetName: "1B, Main St.", landmark: "V2", postalCode: "11")).save(deepValidate: false)

        then: "market is saved, no validation error"
        City.count() == 1
        Market.count() == 2
        Address.count() == 2

        when: "invalid embedded object"
        new City(name: "St. Louis", country: new Country()).save(deepValidate: false)

        then: "should save the city"
        City.count() == 2
        Country.count() == 0
    }
}

@Entity
class City {

    String name
    Country country

    static hasMany = [markets: Market]
    static embedded = ['country']
    static constraints = {
        country nullable: true
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

@Entity
class Country {
    String name
}