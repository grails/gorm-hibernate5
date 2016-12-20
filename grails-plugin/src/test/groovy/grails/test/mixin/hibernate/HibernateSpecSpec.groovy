package grails.test.mixin.hibernate

import grails.gorm.annotation.Entity
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
        !new Book().validate()
        !new Book(title: "").validate()
        session != null
        sessionFactory != null
    }

    void "test hibernate spec with domain constraint inheritance"() {
        given:

        def player = new Player(sport: "Football", name: "Cantona", age: 50)
        player.validate()

        expect:
        !new Player().validate()
        !new Player(sport:"Football").validate()
        !new Player(sport:"Football", name: "Cantona").validate()
        !new Player(sport:"Football", name: "Cantona", age:70).validate()
        new Player(sport:"Football", name: "Cantona", age:50).validate()
    }
}
@Entity
class Person {
    String name
    Integer age
    String phone
    static constraints = {
        age min: 18, max: 65
        name blank: false
        phone nullable: true
    }
}
@Entity
class Player extends Person {
    String sport
    String height
    static constraints = {
        sport blank: false
        height nullable: true
    }
}