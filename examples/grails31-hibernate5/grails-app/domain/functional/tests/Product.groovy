package functional.tests

import grails.gorm.annotation.JpaEntity

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.validation.constraints.Digits

/**
 * Created by graemerocher on 02/01/2017.
 */
@Entity
class Product {
    @Id
    @GeneratedValue
    Long myId
    String name

    @Digits(integer = 6, fraction = 2)
    String price

}
