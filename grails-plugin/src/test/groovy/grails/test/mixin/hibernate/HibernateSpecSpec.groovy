package grails.test.mixin.hibernate

import grails.test.hibernate.HibernateSpec

/**
 * Created by graemerocher on 15/07/2016.
 */
class HibernateSpecSpec extends HibernateSpec {

    void setupSpec() {
        Book.withTransaction {
            new Book(title: "The Stand").save(flush:true)
        }
    }
    void "test hibernate spec"() {
        expect:
        Book.count() == 1
    }
    @Override
    List<Class> getDomainClasses() {
        [Book]
    }
}
