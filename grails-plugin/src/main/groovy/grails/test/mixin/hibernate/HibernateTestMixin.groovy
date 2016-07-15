package grails.test.mixin.hibernate

import grails.core.GrailsApplication
import grails.orm.bootstrap.HibernateDatastoreSpringInitializer
import grails.persistence.support.PersistenceContextInterceptor
import grails.test.mixin.gorm.Domain
import grails.test.mixin.support.GrailsUnitTestMixin
import grails.test.runtime.SharedRuntimeConfigurer
import grails.test.runtime.TestEvent
import grails.test.runtime.TestPlugin
import grails.test.runtime.TestPluginRegistrar
import grails.test.runtime.TestPluginUsage
import grails.test.runtime.TestRuntime
import grails.test.runtime.TestRuntimeUtil
import grails.validation.ConstrainedProperty
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.datastore.gorm.bootstrap.support.InstanceFactoryBean
import org.grails.orm.hibernate.cfg.Settings
import org.grails.orm.hibernate.validation.PersistentConstraintFactory
import org.grails.orm.hibernate.validation.UniqueConstraint
import org.grails.test.support.GrailsTestTransactionInterceptor
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.core.env.PropertyResolver
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager

import javax.sql.DataSource

/**
 * A testing plugin for Hibernate
 *
 * @deprecated Use {@link grails.test.hibernate.HibernateSpec} instead
 */
@CompileStatic
@Deprecated
class HibernateTestMixin extends GrailsUnitTestMixin implements TestPluginRegistrar, TestPlugin {

    String[] requiredFeatures = ['grailsApplication', 'coreBeans']
    String[] providedFeatures = ['domainClass', 'gorm', 'hibernateGorm']
    int ordinal = 1

    @Override
    Iterable<TestPluginUsage> getTestPluginUsages() {
        return TestPluginUsage.createForActivating(HibernateTestMixin)
    }

    @Override
    void onTestEvent(TestEvent event) {
        switch(event.name) {
            case 'before':
                before(event.runtime, event.arguments.testInstance)
                break
            case 'after':
                after(event.runtime)
                break
            case 'registerBeans':
                registerBeans(event.runtime, (GrailsApplication)event.arguments.grailsApplication)
                break
            case 'afterClass':
                event.runtime.removeValue("hibernatePersistentClassesToRegister")
                break
            case 'beforeClass':
                Collection<Class<?>> persistentClasses = [] as Set
                persistentClasses.addAll(collectDomainClassesFromAnnotations((Class<?>)event.arguments.testClass, event.runtime.getSharedRuntimeConfigurer()?.getClass()))
                event.runtime.putValue('hibernatePersistentClassesToRegister', persistentClasses)
                break
            case 'hibernateDomain':
                hibernateDomain(event.runtime, event.arguments)
                break
        }
    }
    /**
     * Sets up a GORM for Hibernate domain
     *
     * @param persistentClasses
     */
    void hibernateDomain(TestRuntime runtime, Map parameters) {
        Collection<Class> persistentClasses = [] as Set
        persistentClasses.addAll((Collection<Class>)parameters.domains)
        boolean immediateDelivery = true
        if(runtime.containsValueFor("hibernatePersistentClassesToRegister")) {
            Collection<Class<?>> allPersistentClasses = runtime.getValue("hibernatePersistentClassesToRegister", Collection)
            allPersistentClasses.addAll(persistentClasses)
            immediateDelivery = false
        }

        DataSource dataSource = (DataSource)parameters.dataSource
        if(dataSource != null) {
            defineDataSourceBean(runtime, immediateDelivery, dataSource)
        }

        Map initializerConfig

        def configArgument = parameters.config
        if(configArgument instanceof Map) {
            initializerConfig = (Map)configArgument
        }

        if(immediateDelivery) {
            Collection<Class<?>> previousPersistentClasses = runtime.getValue("initializedHibernatePersistentClasses", Collection)
            if(!previousPersistentClasses?.containsAll(persistentClasses) || initializerConfig || dataSource) {
                if(previousPersistentClasses) {
                    persistentClasses.addAll(previousPersistentClasses)
                }
                boolean reconnectPersistenceInterceptor = false
                if(runtime.containsValueFor("hibernateInterceptor")) {
                    destroyPersistenceInterceptor(runtime)
                    cleanupSessionFactory(runtime)
                    reconnectPersistenceInterceptor = true
                }
                registerHibernateDomains(runtime, runtime.getValueIfExists("grailsApplication", GrailsApplication), persistentClasses, initializerConfig, true)
                if(reconnectPersistenceInterceptor) {
                    connectPersistenceInterceptor(runtime)
                }
            }
        } else {
            if(initializerConfig) {
                runtime.putValue("hibernateInitializerConfig", initializerConfig)
            }
        }
    }
    @CompileDynamic
    public PlatformTransactionManager getTransactionManager() {
        getMainContext().getBean("transactionManager", PlatformTransactionManager)
    }

    @CompileDynamic
    public Session getHibernateSession() {
        Object value = TransactionSynchronizationManager.getResource(getSessionFactory());
        if (value instanceof Session) {
            return (Session) value;
        }

        // handle any SessionHolder (Hibdernate 4 or 5)
        if (value != null && value.respondsTo('getSession')) {
            return value.getSession();
        }
        return null
    }

    @CompileDynamic
    public SessionFactory getSessionFactory() {
        getMainContext().getBean("sessionFactory", SessionFactory)
    }

    GrailsApplication getGrailsApplication(TestRuntime runtime) {
        runtime.getValue("grailsApplication", GrailsApplication)
    }

    @Override
    void close(TestRuntime runtime) {
        ConstrainedProperty.removeConstraint(UniqueConstraint.UNIQUE_CONSTRAINT, UniqueConstraint)
        runtime.removeValue('initializedHibernatePersistentClasses')
    }

    void registerBeans(TestRuntime runtime, GrailsApplication grailsApplication) {
        def config = grailsApplication.config
        config.merge((Map)runtime.getValueIfExists("hibernateInitializerConfig", Map))
        config.put(Settings.SETTING_DB_CREATE, "create-drop")

        if(runtime.containsValueFor("hibernatePersistentClassesToRegister")) {
            Collection<Class<?>> persistentClasses = runtime.getValue("hibernatePersistentClassesToRegister", Collection)
            registerHibernateDomains(runtime, grailsApplication, persistentClasses, runtime.getValueIfExists("hibernateInitializerConfig", Map), false)
        }
    }

    void registerHibernateDomains(TestRuntime runtime, GrailsApplication grailsApplication, Collection<Class<?>> persistentClasses, Map initializerConfig, boolean immediateDelivery) {
        for(cls in persistentClasses) {
            grailsApplication.addArtefact(DomainClassArtefactHandler.TYPE, cls)
        }
        def config = grailsApplication.config
        def initializer = new HibernateDatastoreSpringInitializer((PropertyResolver)config, persistentClasses)
        def context = grailsApplication.getMainContext()

        ConstrainedProperty.registerNewConstraint(UniqueConstraint.UNIQUE_CONSTRAINT,
                new PersistentConstraintFactory(context,
                        UniqueConstraint))

        def beansClosure = initializer.getBeanDefinitions((BeanDefinitionRegistry)context)
        runtime.putValue('initializedHibernatePersistentClasses', Collections.unmodifiableList(new ArrayList(persistentClasses)))
        defineBeans(runtime, immediateDelivery, beansClosure)
    }

    void defineBeans(TestRuntime runtime, boolean immediateDelivery = true, Closure<?> closure) {
        runtime.publishEvent("defineBeans", [closure: closure], [immediateDelivery: immediateDelivery])
    }

    protected void before(TestRuntime runtime, Object target) {
        connectPersistenceInterceptor(runtime)
        initTransaction(runtime, target)
    }

    protected void after(TestRuntime runtime) {
        destroyTransaction(runtime)
        destroyPersistenceInterceptor(runtime)
    }

    protected void initTransaction(TestRuntime runtime, Object target) {
        def transactionInterceptor = new GrailsTestTransactionInterceptor(getGrailsApplication(runtime).mainContext)
        if (runtime.containsValueFor("hibernateInterceptor") && transactionInterceptor.isTransactional(target)) {
            transactionInterceptor.init()
            runtime.putValue("hibernateTransactionInterceptor", transactionInterceptor)
        }
    }

    protected void destroyPersistenceInterceptor(TestRuntime runtime) {
        if(runtime.containsValueFor("hibernateInterceptor")) {
            PersistenceContextInterceptor persistenceInterceptor=(PersistenceContextInterceptor)runtime.removeValue("hibernateInterceptor")
            persistenceInterceptor.destroy()
        }
    }


    protected void destroyTransaction(TestRuntime runtime) {
        if (runtime.containsValueFor("hibernateTransactionInterceptor")) {
            def transactionInterceptor = runtime.removeValue("hibernateTransactionInterceptor", GrailsTestTransactionInterceptor)
            transactionInterceptor.destroy()
        }
    }

    protected void connectPersistenceInterceptor(TestRuntime runtime) {
        GrailsApplication grailsApplication = getGrailsApplication(runtime)
        def mainContext = grailsApplication.getMainContext()
        if(mainContext.containsBean("persistenceInterceptor")) {
            def persistenceInterceptor = mainContext.getBean("persistenceInterceptor", PersistenceContextInterceptor)
            persistenceInterceptor.init()
            runtime.putValue("hibernateInterceptor", persistenceInterceptor)
        }
    }

    public static Set<Class<?>> collectDomainClassesFromAnnotations(Class annotatedClazz, Class<? extends SharedRuntimeConfigurer> sharedRuntimeConfigurerClazz = null) {
        List<Domain> allAnnotations = collectDomainAnnotations(annotatedClazz, sharedRuntimeConfigurerClazz)
        (Set<Class<?>>)allAnnotations.inject([] as Set) { Set<Class<?>> accumulator, Domain domainAnnotation ->
            accumulator.addAll(domainAnnotation.value() as List)
            accumulator
        }
    }

    public static List<Domain> collectDomainAnnotations(Class annotatedClazz, Class<? extends SharedRuntimeConfigurer> sharedRuntimeConfigurerClazz) {
        List<Domain> allAnnotations = []
        appendDomainAnnotations(allAnnotations, annotatedClazz)
        appendDomainAnnotations(allAnnotations, sharedRuntimeConfigurerClazz)
        allAnnotations
    }

    private static appendDomainAnnotations(List allAnnotations, Class annotatedClazz) {
        if(annotatedClazz) {
            allAnnotations.addAll(TestRuntimeUtil.collectAllAnnotations(annotatedClazz, Domain, true) as List)
        }
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    private defineDataSourceBean(TestRuntime runtime, boolean immediateDelivery, DataSource dataSource) {
        defineBeans(runtime, immediateDelivery) {
            delegate.dataSource(InstanceFactoryBean, dataSource)
        }
    }

    private void cleanupSessionFactory(TestRuntime runtime) {
        GrailsApplication grailsApplication = getGrailsApplication(runtime)
        if(grailsApplication.getMainContext().containsBean("sessionFactory")) {
            SessionFactory sessionFactory = grailsApplication.getMainContext().getBean("sessionFactory", SessionFactory)
            if(sessionFactory != null && !sessionFactory.isClosed()) {
                try {
                    sessionFactory.close()
                } catch (Exception e) {
                    // ignore exception
                }
            }
        }
    }

}
