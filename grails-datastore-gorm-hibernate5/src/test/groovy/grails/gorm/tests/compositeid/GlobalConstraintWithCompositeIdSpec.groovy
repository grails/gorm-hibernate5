package grails.gorm.tests.compositeid

import grails.gorm.annotation.Entity
import grails.transaction.Rollback
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 17/02/2017.
 */
class GlobalConstraintWithCompositeIdSpec extends Specification {

    @Shared Map config = [
            'dataSource.url':"jdbc:h2:mem:grailsDB;MVCC=TRUE;LOCK_TIMEOUT=10000",
            'dataSource.dbCreate': 'update',
            'dataSource.dialect': H2Dialect.name,
            'dataSource.formatSql': 'true',
            'hibernate.flush.mode': 'COMMIT',
            'grails.gorm.default.constraints':{
                '*'(nullable: true)
            }
    ]

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config),ParentB, ChildB)
    @Shared PlatformTransactionManager transactionManager = hibernateDatastore.transactionManager

    @Rollback
    @Issue('https://github.com/grails/grails-core/issues/10457')
    void "test global constraints with composite id"() {
        when:
        ParentB parent = new ParentB(code:"AAA", desc: "BBB")
                                    .addToChilds(name:"Child A")
                                    .save(flush:true)

        then:
        ParentB.count == 1
        ChildB.count == 1
    }
}


@Entity
class ParentB implements Serializable {

    String code
    String desc

    static hasMany = [childs: ChildB]

    static constraints = {
    }

    static mapping = {
        id composite: ['code', 'desc']

        code column: 'COD'
        desc column: 'DSC'
    }
}

@Entity
class ChildB implements Serializable {
    String name

    static belongsTo = [parent: ParentB]

    static constraints = {
    }

    static mapping = {
        id composite: ['name', 'parent']

        columns {
            parent {
                column name: 'COD'
                column name: 'DSC'
            }
        }
    }
}