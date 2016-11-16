package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.transaction.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.Session
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.ResultSet

/**
 * Created by graemerocher on 16/11/16.
 */
class IdentityEnumTypeSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(EnumEntityDomain)
    @Shared PlatformTransactionManager transactionManager = hibernateDatastore.getTransactionManager()

    @Rollback
    void "test identity enum type"() {
        when:
        new EnumEntityDomain(status: EnumEntityDomain.Status.FOO).save(flush:true)
        DataSource ds = hibernateDatastore.connectionSources.defaultConnectionSource.dataSource
        ResultSet resultSet = ds.getConnection().prepareStatement('select status from enum_entity_domain').executeQuery()

        then:
        resultSet.next()
        resultSet.getString(1) == 'F'
    }
}

@Entity
class EnumEntityDomain {
    Status status

    static mapping = {
        status(enumType: "identity")
    }

    enum Status {
        FOO("F"), BAR("B")
        String id
        Status(String id) { this.id = id }
    }
}