package org.grails.orm.hibernate.proxy

import groovy.transform.CompileStatic

import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.Hibernate

import grails.gorm.annotation.Entity
import spock.lang.Specification

/**
 * shows proxy problems
 */
class HibernateProxySpec extends Specification {

    void "test load proxy"() {
        when:
        def config = ["dataSource.dbCreate": "create-drop"]
        HibernateDatastore datastore = new HibernateDatastore(config, Book)
        def proxyHandler = new HibernateProxyHandler()
        def hibSession = datastore.openSession()
        Book.withTransaction {
            new Book(title: "Atlas Shrugged").save(failOnError: true, flush: true)
            // datastore.currentSession.clear()
        }
        hibSession.clear()
        Book book
        Book.withNewSession {
            book = Book.load(1)
        }

        then:"Should be a proxy"
        //Any attempt to initialize the proxy will throw an error since it was created in different session
        !Hibernate.isInitialized(book)
        !proxyHandler.isInitialized(book)
        proxyHandler.isProxy(book)

        def proxyTesting = new CompileStaticProxyTesting(proxy: book)
        //this will work because the static compilation converts .id to getId()
        proxyTesting.testAsserts()

        //FIXME all of these will fail and try to initialize the proxy when dynamically compiled
        // the metaClass gets accessed and hibernates byte buddy doesnt know about the metaClass hit.
        // uncommenting the build.gradle "testImplementation "org.yakworks:hibernate-groovy-bytebuddy:$hibernateGroovyBytebuddy""
        // the below as well as all tests will pass. See Example project too.
        // assert book
        // book.getId()
        // book.id
        // !proxyHandler.isInitialized(book)
    }
}

@Entity
class Book {
    String title

    static constraints = {
        title validator: { val ->
            val.asBoolean()
        }
    }
}

@CompileStatic
class CompileStaticProxyTesting {
    Book proxy

    //should return true and not initialize the proxy
    // getId works inside a compile static
    boolean testAsserts(){
        assert proxy.getId()
        assert !Hibernate.isInitialized(proxy)
        assert proxy.id
        assert !Hibernate.isInitialized(proxy)
        //a truthy check on the object will try to init it because it hits the getMetaClass
        // assert proxy
        // assert !Hibernate.isInitialized(proxy)

        return true
    }
}
