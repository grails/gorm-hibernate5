package grails.gorm.tests

import org.grails.orm.hibernate.GormSpec
import org.hibernate.Session
import org.springframework.orm.hibernate5.SessionHolder
import org.springframework.orm.hibernate5.SpringSessionSynchronization
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import spock.lang.Issue

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
        SessionHolder previousSessionHolder = TransactionSynchronizationManager.getResource(sessionFactory)
        Book.withNewSession { Session session ->
            // access the current session
            assert !previousSessionHolder.is(TransactionSynchronizationManager.getResource(sessionFactory))
            session.sessionFactory.currentSession
        }
        // reproduce session closed problem
        int result =  Book.count()
        SessionHolder sessionHolder = TransactionSynchronizationManager.getResource(sessionFactory)

        then:"The result is correct"
        sessionHolder.is(previousSessionHolder)
        TransactionSynchronizationManager.isSynchronizationActive()
        sessionHolder.session.isOpen()
        sessionHolder.isSynchronizedWithTransaction()
        sessionFactory.currentSession.isOpen()
        result == 0
        Book.count() == 0
    }

    @Issue('https://github.com/grails/grails-core/issues/10426')
    void "Test with withNewSession with nested transaction"() {
        when:"An existing transaction not to pick up the current session"
        sessionFactory.currentSession
        SessionHolder previousSessionHolder = TransactionSynchronizationManager.getResource(sessionFactory)
        Book.withNewSession { Session session ->
            assert !previousSessionHolder.is(TransactionSynchronizationManager.getResource(sessionFactory))
            // access the current session
            session.sessionFactory.currentSession
            // reproduce "Pre-bound JDBC Connection found!" problem
            Book.withNewTransaction {
                assert !previousSessionHolder.is(TransactionSynchronizationManager.getResource(sessionFactory))
                new Book(title: "The Stand", author: 'Stephen King').save()
            }
        }

        Book.count()
        SessionHolder sessionHolder = TransactionSynchronizationManager.getResource(sessionFactory)

        then:"The result is correct"
        sessionHolder.is(previousSessionHolder)
        TransactionSynchronizationManager.isSynchronizationActive()
        sessionHolder.session.isOpen()
        sessionHolder.isSynchronizedWithTransaction()
        sessionFactory.currentSession.isOpen()
    }

    @Issue('https://github.com/grails/grails-core/issues/10448')
    void "Test with withNewSession with existing transaction"() {
        when:"An existing transaction not to pick up the current session"
        sessionFactory.currentSession
        SessionHolder previousSessionHolder = TransactionSynchronizationManager.getResource(sessionFactory)
        Book.withNewTransaction { TransactionStatus status ->
            // reproduce "java.lang.IllegalStateException: No value for key" problem
            Book.withNewSession { Session session ->
                // access the current session
                assert !previousSessionHolder.is(TransactionSynchronizationManager.getResource(sessionFactory))
                session.sessionFactory.currentSession

                new Book(title: "The Stand", author: 'Stephen King').save()
            }
        }

        Book.count()
        SessionHolder sessionHolder = TransactionSynchronizationManager.getResource(sessionFactory)

        then:"The result is correct"
        sessionHolder.is(previousSessionHolder)
        TransactionSynchronizationManager.isSynchronizationActive()
        sessionHolder.session.isOpen()
        sessionHolder.isSynchronizedWithTransaction()
        sessionFactory.currentSession.isOpen()
    }
}
