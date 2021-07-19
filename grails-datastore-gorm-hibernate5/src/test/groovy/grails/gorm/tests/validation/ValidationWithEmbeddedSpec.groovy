package grails.gorm.tests.validation

import grails.gorm.annotation.Entity
import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.transactions.Rollback
import grails.validation.ValidationException
import spock.lang.Issue

class ValidationWithEmbeddedSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        return [TrueEntity]
    }

    @Rollback
    @Issue('https://github.com/grails/gorm-hibernate5/issues/110')
    void "fail with ValidationException from embedded object"() {
        when: "not valid Entity together with embedded object"
        new TrueEntity(embeddedObject: new EmbeddedObject(name: "Cee-cee")).save(failOnError: true)

        then:
        thrown(ValidationException)


        when: "valid Entity together with not valid embedded object"
        new TrueEntity(name: "ba-ba", embeddedObject: new EmbeddedObject()).save(failOnError: true)

        then:
        thrown(ValidationException)
    }
}

@Entity
class TrueEntity {

    String name
    EmbeddedObject embeddedObject

    static embedded = ['embeddedObject']
    static constraints = {
        embeddedObject nullable: true
        name nullable: false
    }
}


class EmbeddedObject {
    String name

    static constraints = {
        name nullable: false
    }
}