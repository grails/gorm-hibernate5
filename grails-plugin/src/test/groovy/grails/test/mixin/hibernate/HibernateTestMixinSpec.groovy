package grails.test.mixin.hibernate

import grails.gorm.annotation.Entity
import grails.test.mixin.TestMixin
import grails.test.mixin.gorm.Domain
import spock.lang.Specification

/**
 * Created by graemerocher on 15/07/2016.
 */
@Domain(Book)
@TestMixin(HibernateTestMixin)
class HibernateTestMixinSpec extends Specification {

    void "Test hibernate test mixin"() {
        expect:
        Book.count() == 0
    }
}
@Entity
class Book {
    String title

    static constraints = {
        title validator: { val ->
            val.asBoolean()
        }
    }
}
