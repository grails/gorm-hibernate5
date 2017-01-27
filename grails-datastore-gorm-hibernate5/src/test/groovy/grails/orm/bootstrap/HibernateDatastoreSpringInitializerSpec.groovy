package grails.orm.bootstrap

import grails.persistence.Entity
import org.h2.Driver
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.dialect.H2Dialect
import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.util.Log4jConfigurer
import spock.lang.IgnoreRest
import spock.lang.Specification

import javax.sql.DataSource

/**
 * Created by graemerocher on 29/01/14.
 */
class HibernateDatastoreSpringInitializerSpec extends Specification{

    void "Test that GORM is initialized correctly for an existing BeanDefinitionRegistry"() {
        given:"An initializer instance"

        def datastoreInitializer = new HibernateDatastoreSpringInitializer(['hibernate.hbm2ddl.auto': 'create'], Person)
        def applicationContext = new GenericApplicationContext()
        def dataSource = new DriverManagerDataSource("jdbc:h2:mem:grailsDb1;MVCC=TRUE;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1", 'sa', '')
        dataSource.driverClassName = Driver.name
        applicationContext.beanFactory.registerSingleton("dataSource", dataSource)


        when:"The application context is configured"
        datastoreInitializer.configureForBeanDefinitionRegistry(applicationContext)
        boolean refreshCalled = false
        applicationContext.refresh()
        refreshCalled = true
        def conn = dataSource.getConnection()

        then:"The database tables are created correctly"
        conn.prepareStatement("SELECT * FROM PERSON").execute()

        when:"A GORM method is invoked"
        def total = Person.withNewSession { Person.count() }

        then:"The correct results are returned"
        total == 0

        when:"A new domain instance is created"
        def p = new Person()

        then:"it is initially invalid"
        ! Person.withNewSession { p.validate() }

        when:"it is made valid"
        p.name = "Bob"

        then:"It can be saved"
        Person.withNewSession { p.save(flush:true) }
        Person.withNewSession { Person.count() } == 1


        cleanup:
        if(refreshCalled && applicationContext.isRunning()) {
            applicationContext.stop()
        }

    }

    void "Test that GORM is initialized correctly for a DataSource"() {
        given:"An initializer instance"

        def datastoreInitializer = new HibernateDatastoreSpringInitializer(['hibernate.hbm2ddl.auto': 'create'], Person)
        def dataSource = new DriverManagerDataSource("jdbc:h2:mem:grailsDb2;MVCC=TRUE;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1", 'sa', '')
        dataSource.driverClassName = Driver.name


        when:"The application context is configured"
        datastoreInitializer.configureForDataSource(dataSource)
        def conn = dataSource.getConnection()

        then:"The database tables are created correctly"
        Person.withNewSession {  Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:grailsDb2"
            return true
        }
        conn.prepareStatement("SELECT * FROM PERSON").execute()

        when:"A GORM method is invoked"
        def total = Person.withNewSession { Person.count() }

        then:"The correct results are returned"
        total == 0

        when:"A new domain instance is created"
        def p = new Person()

        then:"it is initially invalid"
        !Person.withNewSession { p.validate() }

        when:"it is made valid"
        p.name = "Bob"

        then:"It can be saved"
        Person.withNewSession { p.save(flush:true) }
        Person.withNewSession { Person.count()  } == 1



    }

//    @IgnoreRest
    void "Test configure multiple data sources"() {
        given:"An initializer instance"
        Map config = [
                'dataSource.url':"jdbc:h2:mem:people;MVCC=TRUE;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate': 'update',
                'dataSource.dialect': H2Dialect.name,
                'dataSource.formatSql': 'true',
                'hibernate.flush.mode': 'COMMIT',
                'hibernate.cache.queries': 'true',
                'hibernate.hbm2ddl.auto': 'create',
                'dataSources.books.url':"jdbc:h2:mem:books;MVCC=TRUE;LOCK_TIMEOUT=10000",
                'dataSources.moreBooks.url':"jdbc:h2:mem:moreBooks;MVCC=TRUE;LOCK_TIMEOUT=10000"
        ]
        def datastoreInitializer = new HibernateDatastoreSpringInitializer(config, Person, Book, Author)

        when:"the application is configured"
        def applicationContext = datastoreInitializer.configure()
        println applicationContext.getBeanDefinitionNames()

        then:"Each session factory has the correct number of persistent entities"
        applicationContext.getBean("sessionFactory", SessionFactory).allClassMetadata.values().size() == 2
        applicationContext.getBean("sessionFactory", SessionFactory).allClassMetadata.containsKey(Person.name)
        applicationContext.getBean("sessionFactory", SessionFactory).allClassMetadata.containsKey(Author.name)
        applicationContext.getBean("sessionFactory_books", SessionFactory).allClassMetadata.values().size() == 2
        applicationContext.getBean("sessionFactory_books", SessionFactory).allClassMetadata.containsKey(Book.name)
        applicationContext.getBean("sessionFactory_books", SessionFactory).allClassMetadata.containsKey(Author.name)
        applicationContext.getBean("sessionFactory_moreBooks", SessionFactory).allClassMetadata.values().size() == 2
        applicationContext.getBean("sessionFactory_moreBooks", SessionFactory).allClassMetadata.containsKey(Book.name)
        applicationContext.getBean("sessionFactory_moreBooks", SessionFactory).allClassMetadata.containsKey(Author.name)

        and:"Each domain has the correct data source(s)"
        Person.withNewSession { Person.count() == 0 }
        Person.withNewSession {  Session s ->
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:people"
            return true
        }
        Book.withNewSession { Book.count() == 0 }
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
            assert s.connection().metaData.getURL() == "jdbc:h2:mem:people"
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
class Person {
    Long id
    Long version
    String name

    static constraints = {
        name blank:false
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
