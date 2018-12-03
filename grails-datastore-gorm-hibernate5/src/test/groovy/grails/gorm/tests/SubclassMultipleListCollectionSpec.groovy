package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import groovy.transform.NotYetImplemented
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 01/03/2017.
 */
@Ignore // not yet implemented
class SubclassMultipleListCollectionSpec extends Specification {

    @AutoCleanup @Shared HibernateDatastore hibernateDatastore = new HibernateDatastore(
        SuperProduct, Product, Iteration
    )

    @Shared PlatformTransactionManager transactionManager = hibernateDatastore.getTransactionManager()

    @Rollback
    @NotYetImplemented
    @Issue('https://github.com/grails/grails-data-mapping/issues/882')
    void "test inheritance with multiple list collections"() {
        when:
        Iteration iter = new Iteration()
        iter.addToProducts(new Product())
        iter.addToOtherProducts(new SuperProduct())
        iter.save(flush:true)

        then:
        Iteration.count == 1
    }
}

@Entity
class Iteration {
    List products

    static hasMany = [products: Product, otherProducts: SuperProduct]
    // uncommenting this line resolves the issue
//    static mappedBy = [products: 'iteration', otherProducts: 'none']
}

@Entity
class Product extends SuperProduct {

    static belongsTo = [iteration: Iteration]
}

@Entity
class SuperProduct {

    static constraints = {
    }
}