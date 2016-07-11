package org.grails.orm.hibernate.connections

import grails.gorm.MultiTenant
import grails.gorm.multitenancy.Tenants
import grails.persistence.Entity
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.Session
import org.hibernate.dialect.H2Dialect
import spock.lang.Specification

/**
 * Created by graemerocher on 07/07/2016.
 */
class SingleTenantSpec extends Specification {
    void "Test a database per tenant multi tenancy"() {
        given:"A configuration for multiple data sources"
        Map config = [
                "grails.gorm.multiTenancy.mode":"SINGLE",
                "grails.gorm.multiTenancy.tenantResolverClass":SystemPropertyTenantResolver,
                'dataSource.url':"jdbc:h2:mem:grailsDB;MVCC=TRUE;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate': 'update',
                'dataSource.dialect': H2Dialect.name,
                'dataSource.formatSql': 'true',
                'hibernate.flush.mode': 'COMMIT',
                'hibernate.cache.queries': 'true',
                'hibernate.hbm2ddl.auto': 'create',
                'dataSources.books':[url:"jdbc:h2:mem:books;MVCC=TRUE;LOCK_TIMEOUT=10000"],
                'dataSources.moreBooks':[url:"jdbc:h2:mem:moreBooks;MVCC=TRUE;LOCK_TIMEOUT=10000"]
        ]

        HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config),Book, SingleTenantAuthor )

        when:"no tenant id is present"
        SingleTenantAuthor.list()

        then:"An exception is thrown"
        thrown(TenantNotFoundException)

        when:"no tenant id is present"
        new SingleTenantAuthor().save()

        then:"An exception is thrown"
        thrown(TenantNotFoundException)

        when:"A tenant id is present"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "moreBooks")

        then:"the correct tenant is used"
        SingleTenantAuthor.withNewSession { SingleTenantAuthor.count() == 0 }
        SingleTenantAuthor.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:moreBooks"
            return true
        }

        when:"An object is saved"
        SingleTenantAuthor.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:moreBooks"
            new SingleTenantAuthor(name: "Stephen King").save(flush:true)
        }

        then:"The results are correct"
        SingleTenantAuthor.withNewSession { SingleTenantAuthor.count() == 1 }

        when:"An a transaction is used"
        SingleTenantAuthor.withTransaction{
            new SingleTenantAuthor(name: "James Patterson").save(flush:true)
        }

        then:"The results are correct"
        SingleTenantAuthor.withNewSession { SingleTenantAuthor.count() == 2 }

        when:"The tenant id is switched"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "books")

        then:"the correct tenant is used"
        SingleTenantAuthor.withNewSession { SingleTenantAuthor.count() == 0 }
        SingleTenantAuthor.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:books"
            SingleTenantAuthor.count() == 0
        }
        SingleTenantAuthor.withTenant("moreBooks") { String tenantId, Session s ->
            assert s != null
            SingleTenantAuthor.count() == 2
        }
        Tenants.withId("books") {
            SingleTenantAuthor.count() == 0
        }
        Tenants.withId("moreBooks") {
            SingleTenantAuthor.count() == 2
        }
        Tenants.withCurrent {
            SingleTenantAuthor.count() == 0
        }

        when:"each tenant is iterated over"
        Map tenantIds = [:]
        SingleTenantAuthor.eachTenant { String tenantId ->
            tenantIds.put(tenantId, SingleTenantAuthor.count())
        }

        then:"The result is correct"
        tenantIds == [moreBooks:2, books:0]
    }


}


@Entity
class SingleTenantAuthor implements GormEntity<SingleTenantAuthor>,MultiTenant<SingleTenantAuthor> {
    Long id
    Long version
    String name

    static constraints = {
        name blank:false
    }
}

