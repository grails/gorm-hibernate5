package org.grails.orm.hibernate.connections

import grails.persistence.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.Session
import org.hibernate.dialect.H2Dialect
import spock.lang.Specification

/**
 * Created by graemerocher on 06/07/2016.
 */
class MultipleDataSourceConnectionsSpec extends Specification {

    void "Test map to multiple data sources"() {
        given:"A configuration for multiple data sources"
        Map config = [
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
        HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config),Book, Author )

        then:
        Book.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:books"
            return true
        }
        Book.moreBooks.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:moreBooks"
            return true
        }
        Author.withNewSession { Author.count() == 0 }
        Author.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:grailsDB"
            return true
        }
        Author.books.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:books"
            return true
        }
        Author.moreBooks.withNewSession { Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:moreBooks"
            return true
        }
    }
}

@Entity
class Book {
    Long id
    Long version
    String name

    static mapping = {
        datasources( ['books', 'moreBooks'] )
    }
    static constraints = {
        name blank:false
    }
}

@Entity
class Author {
    Long id
    Long version
    String name

    static mapping = {
        datasource 'ALL'
    }
    static constraints = {
        name blank:false
    }
}


