package grails.test.hibernate

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.interceptor.DefaultTransactionAttribute
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Specification for Hibernate tests
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
abstract class HibernateSpec extends Specification {
    @Shared @AutoCleanup HibernateDatastore hibernateDatastore
    @Shared PlatformTransactionManager transactionManager

    void setupSpec() {
        hibernateDatastore = new HibernateDatastore(
                                        DatastoreUtils.createPropertyResolver(getConfiguration()),
                                        getDomainClasses() as Class[])
        transactionManager = hibernateDatastore.getTransactionManager()
    }

    /**
     * The transaction status
     */
    TransactionStatus transactionStatus

    void setup() {
        transactionStatus = transactionManager.getTransaction(new DefaultTransactionAttribute())
    }

    void cleanup() {
        if(isRollback()) {
            transactionManager.rollback(transactionStatus)
        }
        else {
            transactionManager.commit(transactionStatus)
        }
    }

    /**
     * @return The configuration
     */
    Map getConfiguration() {
        Collections.singletonMap(Settings.SETTING_DB_CREATE, "create-drop")
    }

    /**
     * Whether to rollback on each test (defaults to true)
     */
    boolean isRollback() {
        return true
    }
    /**
     * @return The domain classes
     */
    abstract List<Class> getDomainClasses()
}
