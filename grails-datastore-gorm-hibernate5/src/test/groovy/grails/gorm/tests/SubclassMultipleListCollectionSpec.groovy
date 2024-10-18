package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.*

/**
 * Created by graemerocher on 01/03/2017.
 */
@Ignore
class SubclassMultipleListCollectionSpec extends Specification {

    @AutoCleanup @Shared HibernateDatastore hibernateDatastore
    @Shared PlatformTransactionManager transactionManager


    void setupSpec() {
        hibernateDatastore = new HibernateDatastore(
                SuperProduct, Product, Iteration
        )
        transactionManager = hibernateDatastore.getTransactionManager()
    }

    @Ignore // not yet implemented
    @Rollback
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

// @Entity
// https://issues.apache.org/jira/browse/GROOVY-5106 - The interface GormEntity cannot be implemented more than once with different arguments: org.grails.datastore.gorm.GormEntity<grails.gorm.tests.XXX> and org.grails.datastore.gorm.GormEntity<grails.gorm.tests.XXX>
class Product extends SuperProduct {

    static belongsTo = [iteration: Iteration]
}

@Entity
class SuperProduct {

    static constraints = {
    }
}
