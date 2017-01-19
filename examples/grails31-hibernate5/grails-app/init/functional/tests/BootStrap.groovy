package functional.tests

import functional.tests.Product
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.beans.factory.annotation.Autowired

class BootStrap {

    HibernateDatastore hibernateDatastore

    def init = { servletContext ->
        assert hibernateDatastore.connectionSources.defaultConnectionSource.settings.hibernate.getConfigClass() == CustomHibernateMappingContextConfiguration
        new Product(name: "MacBook", price: "1200.01").save(flush:true)
    }
    def destroy = {
    }
}
