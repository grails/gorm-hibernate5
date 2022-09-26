package example

import org.hibernate.Hibernate

import grails.test.hibernate.HibernateSpec

/**
 * Tests Proxy with hibernate-groovy-bytebuddy
 */
class ProxySpec extends HibernateSpec {

    void "Test Proxy"() {
        when:
        Customer.withTransaction {
            new Customer(1, "Bob").save(failOnError: true, flush: true)
        }

        def proxy
        Customer.withNewSession {
            proxy = Customer.load(1)
        }

        then:
        //without ByteBuddyGroovyInterceptor this would normally cause the proxy to init
        proxy
        proxy.metaClass
        proxy.getMetaClass()
        !Hibernate.isInitialized(proxy)
        //id calls
        proxy.id == 1
        proxy.getId() == 1
        proxy["id"] == 1
        !Hibernate.isInitialized(proxy)
        // gorms trait implements in the class so no way to tell
        // proxy.toString() == "Customer : 1 (proxy)"
        // !Hibernate.isInitialized(proxy)
    }

}
