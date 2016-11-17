package grails.gorm.tests

import grails.transaction.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 17/11/16.
 */
class SqlQuerySpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(Club)
    @Shared PlatformTransactionManager transactionManager = datastore.getTransactionManager()

    @Rollback
    void "test simple sql query"() {
        when:"Some test data is saved"
        new Club(name: "Barcelona").save()
        new Club(name: "Arsenal").save()
        new Club(name: "Manchester United").save(flush:true)

        List<Club> results = Club.findAllWithSql("select * from club c order by c.name")

        then:"The results are correct"
        results.size() == 3
        results[0] instanceof Club
        results[0].name == 'Arsenal'
    }

    @Rollback
    void "test sql query with gstring parameters"() {
        when:"Some test data is saved"
        new Club(name: "Barcelona").save()
        new Club(name: "Arsenal").save()
        new Club(name: "Manchester United").save(flush:true)

        String p = "%l%"
        List<Club> results = Club.findAllWithSql("select * from club c where c.name like $p order by c.name")

        then:"The results are correct"
        results.size() == 2
        results[0] instanceof Club
        results[0].name == 'Arsenal'
    }
}
