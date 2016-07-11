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
import org.hibernate.SessionFactory
import org.hibernate.dialect.H2Dialect
import org.springframework.orm.hibernate5.SessionHolder
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Specification

/**
 * Created by graemerocher on 11/07/2016.
 */
class MultiTenantSpec extends Specification {
    void "Test a database per tenant multi tenancy"() {
        setup:"A configuration for multiple data sources"
        Map config = [
                "grails.gorm.multiTenancy.mode":"MULTI",
                "grails.gorm.multiTenancy.tenantResolverClass":SystemPropertyTenantResolver,
                'dataSource.url':"jdbc:h2:mem:grailsDB;MVCC=TRUE;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate': 'update',
                'dataSource.dialect': H2Dialect.name,
                'dataSource.formatSql': 'true',
                'dataSource.logSql': 'true',
                'hibernate.flush.mode': 'COMMIT',
                'hibernate.cache.queries': 'true',
                'hibernate.hbm2ddl.auto': 'create',
        ]

        HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), MultiTenantAuthor )


        def sessionFactory = datastore.sessionFactory
        def session = sessionFactory.openSession()
        TransactionSynchronizationManager.bindResource(sessionFactory, new SessionHolder(session))

        when:"no tenant id is present"
        MultiTenantAuthor.list()


        then:"An exception is thrown"
        thrown(TenantNotFoundException)

        when:"no tenant id is present"
        def author = new MultiTenantAuthor(name: "Stephen King")
        author.save(flush:true)

        then:"An exception is thrown"
        !author.errors.hasErrors()
        thrown(TenantNotFoundException)

        when:"A tenant id is present"
        session.clear()
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "moreBooks")

        then:"the correct tenant is used"
        MultiTenantAuthor.count() == 0

        when:"An object is saved"
        new MultiTenantAuthor(name: "Stephen King").save(flush: true)

        then:"The results are correct"
        MultiTenantAuthor.findAll("from MultiTenantAuthor a").size() == 1
        MultiTenantAuthor.count() == 1

        when:"An a transaction is used"
        MultiTenantAuthor.withTransaction{
            new MultiTenantAuthor(name: "James Patterson").save(flush:true)
        }

        then:"The results are correct"
        MultiTenantAuthor.count() == 2

        when:"The tenant id is switched"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "books")

        then:"the correct tenant is used"
        MultiTenantAuthor.count() == 0
        MultiTenantAuthor.findAll("from MultiTenantAuthor a").size() == 0
        MultiTenantAuthor.withTenant("moreBooks").count() == 2
        MultiTenantAuthor.withTenant("moreBooks") { String tenantId, Session s ->
            assert s != null
            MultiTenantAuthor.count() == 2
        }
        Tenants.withId("books") {
            MultiTenantAuthor.count() == 0
        }
        Tenants.withId("moreBooks") {
            MultiTenantAuthor.count() == 2
        }
        Tenants.withCurrent {
            MultiTenantAuthor.count() == 0
        }

        when:"each tenant is iterated over"
        Map tenantIds = [:]
        MultiTenantAuthor.eachTenant { String tenantId ->
            tenantIds.put(tenantId, MultiTenantAuthor.count())
        }

        then:"The result is correct"
        tenantIds == [moreBooks:2, books:0]

        cleanup:
        TransactionSynchronizationManager.unbindResource(sessionFactory)
    }


}


@Entity
class MultiTenantAuthor implements GormEntity<MultiTenantAuthor>,MultiTenant<MultiTenantAuthor> {
    Long id
    Long version
    String tenantId
    String name

    static constraints = {
        name blank:false
    }
}

