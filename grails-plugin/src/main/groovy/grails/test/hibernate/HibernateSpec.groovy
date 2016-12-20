package grails.test.hibernate

import grails.config.Config
import grails.persistence.Entity
import groovy.transform.CompileStatic
import org.grails.config.PropertySourcesConfig
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.boot.env.PropertySourcesLoader
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.ResourceLoader
import org.springframework.core.type.filter.AnnotationTypeFilter
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
        PropertySourcesLoader loader = new PropertySourcesLoader()
        ResourceLoader resourceLoader = new DefaultResourceLoader()
        MutablePropertySources propertySources = loader.propertySources
        propertySources.addFirst(new MapPropertySource("defaults", getConfiguration()))
        loader.load resourceLoader.getResource("application.yml")
        loader.load resourceLoader.getResource("application.groovy")
        Config config = new PropertySourcesConfig(propertySources)
        List<Class> domainClasses = getDomainClasses()
        String packageName = config.getProperty('grails.codegen.defaultPackage', getClass().package.name)

        if (!domainClasses) {
            ClassPathScanningCandidateComponentProvider componentProvider = new ClassPathScanningCandidateComponentProvider(false)
            componentProvider.addIncludeFilter(new AnnotationTypeFilter(Entity))

            for (BeanDefinition candidate in componentProvider.findCandidateComponents(packageName)) {
                Class persistentEntity = Class.forName(candidate.beanClassName)
                domainClasses << persistentEntity
            }
        }
        hibernateDatastore = new HibernateDatastore(
                                        DatastoreUtils.createPropertyResolver(getConfiguration()),
                                        domainClasses as Class[])
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
     * @return the current session factory
     */
    SessionFactory getSessionFactory() {
        hibernateDatastore.getSessionFactory()
    }

    /**
     * @return the current Hibernate session
     */
    Session getHibernateSession() {
        getSessionFactory().getCurrentSession()
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
    List<Class> getDomainClasses() { [] }
}
