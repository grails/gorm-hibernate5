package org.grails.orm.hibernate.connections

import grails.gorm.MultiTenant
import grails.persistence.Entity
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.resolvers.FixedTenantResolver
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.Session
import org.hibernate.dialect.H2Dialect
import spock.lang.Specification

/**
 * Created by graemerocher on 07/07/2016.
 */
class SingleTenantSpec extends Specification {
    void "Test map to multiple data sources"() {
        given:"A configuration for multiple data sources"
        Map config = [
                "grails.gorm.multiTenancy.mode":"SINGLE",
                "grails.gorm.multiTenancy.tenantResolverClass":DummyResolver,
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

        when:
        HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config),Book, MultiTenantAuthor )

        then:
        Book.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:books"
            return true
        }
        Book.moreBooks.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:moreBooks"
            return true
        }
        MultiTenantAuthor.withNewSession { MultiTenantAuthor.count() == 0 }
        MultiTenantAuthor.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:moreBooks"
            return true
        }
    }


    static class DummyResolver extends FixedTenantResolver {
        DummyResolver() {
            super("moreBooks")
        }
    }
}


@Entity
class MultiTenantAuthor implements GormEntity<MultiTenantAuthor>,MultiTenant {
    Long id
    Long version
    String name

    static constraints = {
        name blank:false
    }
}

