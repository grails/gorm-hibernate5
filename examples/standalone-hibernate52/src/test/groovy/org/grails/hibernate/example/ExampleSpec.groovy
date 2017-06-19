package org.grails.hibernate.example

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.transaction.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 13/07/2016.
 */
class ExampleSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore
    @Shared PlatformTransactionManager transactionManager

    void setupSpec() {
       hibernateDatastore = new HibernateDatastore(['hibernate.hibernateDirtyChecking':true, (Settings.SETTING_DB_CREATE):'create-drop'],Example)
       transactionManager = hibernateDatastore.getTransactionManager()
    }

    @Rollback
    void "test execute Hibernate standalone in a unit test"() {
        when:
        def e = new Example(name: "Fred").save(flush:true)
        hibernateDatastore.sessionFactory.currentSession.clear()
        e = Example.load(e.id)
        then:
        e.name == "Fred"
        !e.isDirty()
        !e.isDirty('name')
        e.getDirtyPropertyNames() == []
        Example.count() == 1
        Example.executeQuery("from Example").size() == 1
        Example.executeUpdate("update Example as e set e.name = 'fred' where e.name = 'Fred'")
        Example.findWithSql("select * from example") != null
    }
}

@Entity
class Example implements HibernateEntity<Example> {
    String name
}
