package grails.gorm.tests

import org.grails.orm.hibernate.GormSpec
import org.hibernate.Session
import org.springframework.orm.hibernate5.SessionHolder
import org.springframework.orm.hibernate5.SpringSessionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Created by graemerocher on 26/08/2016.
 */
class WithNewSessionAndExistingTransactionSpec extends GormSpec {

    @Override
    List getDomainClasses() {
        [Book]
    }

    void "Test withNewSession when an existing transaction is present"() {
        when:"An existing transaction not to pick up the current session"
        sessionFactory.currentSession
        Book.withNewSession { Session session ->
            // access the current session
            session.sessionFactory.currentSession
        }
        // reproduce session closed problem
        int result =  Book.count()
        SessionHolder sessionHolder = TransactionSynchronizationManager.getResource(sessionFactory)

        then:"The result is correct"
        TransactionSynchronizationManager.isSynchronizationActive()
        TransactionSynchronizationManager.getSynchronizations().isEmpty()
        sessionHolder.session.isOpen()
        sessionHolder.isSynchronizedWithTransaction()
        sessionFactory.currentSession.isOpen()
        result == 0
        Book.count() == 0
    }
}
