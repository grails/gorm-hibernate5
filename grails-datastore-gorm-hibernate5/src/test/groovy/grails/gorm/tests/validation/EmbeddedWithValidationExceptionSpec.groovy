package grails.gorm.tests.validation

import grails.gorm.annotation.Entity
import grails.gorm.tests.GormDatastoreSpec
import grails.gorm.transactions.Rollback
import grails.validation.ValidationException
import spock.lang.Issue

class EmbeddedWithValidationExceptionSpec extends GormDatastoreSpec {
    @Override
    List getDomainClasses() {
        return [DomainWithEmbedded]
    }

    @Rollback
    @Issue("https://github.com/grails/gorm-hibernate5/issues/110")
    void "test validation exception with embedded in domain"() {
        when:
        new DomainWithEmbedded(
                foo: 'not valid',
                myEmbedded: new MyEmbedded(
                        a: 1,
                        b: 'foo'
                )
        ).save(failOnError: true)

        then:
        thrown(ValidationException)
    }
}

@Entity
class DomainWithEmbedded {
    MyEmbedded myEmbedded
    String foo

    static embedded = ['myEmbedded']

    static constraints = {
        foo(validator: { val, self ->
            return 'not.valid.foo'
        })
    }
}

class MyEmbedded {
    Integer a
    String b

    static constraints = {
        a(nullable: true)
        b(nullalbe: true)
    }
}