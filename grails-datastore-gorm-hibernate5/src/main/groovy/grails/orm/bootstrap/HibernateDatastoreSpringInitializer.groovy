/* Copyright (C) 2014 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.orm.bootstrap

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.datastore.gorm.bootstrap.AbstractDatastoreInitializer
import org.grails.datastore.gorm.proxy.ProxyHandlerAdapter
import org.grails.datastore.gorm.support.AbstractDatastorePersistenceContextInterceptor
import org.grails.datastore.gorm.support.DatastorePersistenceContextInterceptor
import org.grails.datastore.mapping.core.connections.AbstractConnectionSources
import org.grails.datastore.mapping.validation.BeanFactoryValidatorRegistry
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.connections.HibernateConnectionSourceFactory
import org.grails.orm.hibernate.proxy.HibernateProxyHandler
import org.grails.orm.hibernate.support.FlushOnRedirectEventListener
import org.grails.orm.hibernate.validation.HibernateDomainClassValidator
import org.grails.orm.hibernate5.support.AggregatePersistenceContextInterceptor
import org.grails.orm.hibernate5.support.GrailsOpenSessionInViewInterceptor
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.config.MethodInvokingFactoryBean
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.ApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.PropertyResolver

import javax.sql.DataSource
/**
 * Class that handles the details of initializing GORM for Hibernate
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@Slf4j
class HibernateDatastoreSpringInitializer extends AbstractDatastoreInitializer {
    public static final String SESSION_FACTORY_BEAN_NAME = "sessionFactory"
    public static final String DEFAULT_DATA_SOURCE_NAME = 'dataSource'
    public static final String DATA_SOURCES = "dataSources";
    public static final String TEST_DB_URL = "jdbc:h2:mem:grailsDb;MVCC=TRUE;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1"

    String defaultDataSourceBeanName = Mapping.DEFAULT_DATA_SOURCE
    String defaultSessionFactoryBeanName = SESSION_FACTORY_BEAN_NAME
    String ddlAuto = "update"
    Set<String> dataSources = [defaultDataSourceBeanName]
    boolean enableReload = false

    HibernateDatastoreSpringInitializer(PropertyResolver configuration, Collection<Class> persistentClasses) {
        super(configuration, persistentClasses)
        configureDataSources(configuration)
    }

    HibernateDatastoreSpringInitializer(PropertyResolver configuration, Class... persistentClasses) {
        super(configuration, persistentClasses)
        configureDataSources(configuration)
    }

    HibernateDatastoreSpringInitializer(PropertyResolver configuration, String... packages) {
        super(configuration, packages)
        configureDataSources(configuration)
    }

    HibernateDatastoreSpringInitializer(Map configuration, Class... persistentClasses) {
        super(configuration, persistentClasses)
        configureDataSources(this.configuration)
    }

    HibernateDatastoreSpringInitializer(Map configuration, Collection<Class> persistentClasses) {
        super(configuration, persistentClasses)
        configureDataSources(this.configuration)
    }


    @CompileStatic
    void configureDataSources(PropertyResolver config) {

        Set<String> dataSourceNames = new HashSet<String>()

        if(config == null) {
            dataSourceNames = [defaultDataSourceBeanName] as Set
        }
        else {
            Map dataSources = config.getProperty(DATA_SOURCES, Map.class, Collections.emptyMap())

            if (dataSources != null && !dataSources.isEmpty()) {
                dataSourceNames.addAll( AbstractConnectionSources.toValidConnectionSourceNames(dataSources) )
            } else {
                Map dataSource = (Map)config.getProperty(DEFAULT_DATA_SOURCE_NAME, Map.class, Collections.emptyMap())
                if (dataSource != null && !dataSource.isEmpty()) {
                    dataSourceNames.add( Mapping.DEFAULT_DATA_SOURCE)
                }
            }
        }
        this.dataSources = dataSourceNames
    }

    @Override
    protected Class<AbstractDatastorePersistenceContextInterceptor> getPersistenceInterceptorClass() {
        DatastorePersistenceContextInterceptor
    }

    /**
     * Configures an in-memory test data source, don't use in production
     */
    @Override
    ApplicationContext configure() {
        GenericApplicationContext applicationContext = createApplicationContext()
        configureForBeanDefinitionRegistry(applicationContext)
        applicationContext.refresh()
        return applicationContext
    }

    protected String getTestDbUrl() {
        TEST_DB_URL
    }

    @CompileStatic
    ApplicationContext configureForDataSource(DataSource dataSource) {
        GenericApplicationContext applicationContext = createApplicationContext()
        applicationContext.beanFactory.registerSingleton(DEFAULT_DATA_SOURCE_NAME, dataSource)
        configureForBeanDefinitionRegistry(applicationContext)
        applicationContext.refresh()
        return applicationContext
    }

    public Closure getBeanDefinitions(BeanDefinitionRegistry beanDefinitionRegistry) {
        Closure beanDefinitions = {
            def common = getCommonConfiguration(beanDefinitionRegistry, "hibernate")
            common.delegate = delegate
            common.call()

            // for unwrapping / inspecting proxies
            hibernateProxyHandler(HibernateProxyHandler)
            proxyHandler(ProxyHandlerAdapter, ref('hibernateProxyHandler'))

            // Useful interceptor for wrapping Hibernate behavior


            def config = this.configuration
            final boolean isGrailsPresent = isGrailsPresent()
            hibernateConnectionSourceFactory(HibernateConnectionSourceFactory, persistentClasses as Class[])
            hibernateDatastore(HibernateDatastore, config, hibernateConnectionSourceFactory)
            sessionFactory(hibernateDatastore:'getSessionFactory')
            transactionManager(hibernateDatastore:"getTransactionManager")
            persistenceInterceptor(AggregatePersistenceContextInterceptor, ref("hibernateDatastore"))

            // domain model mapping context, used for configuration
            grailsDomainClassMappingContext(hibernateDatastore:"getMappingContext") {
                if(isGrailsPresent && (beanDefinitionRegistry instanceof BeanFactory)) {
                    validatorRegistry = new BeanFactoryValidatorRegistry((BeanFactory)beanDefinitionRegistry)
                }
            }

            if(isGrailsPresent) {
                // override Validator beans with Hibernate aware instances
                for(cls in persistentClasses) {
                    "${cls.name}Validator"(HibernateDomainClassValidator) {
                        messageSource = ref("messageSource")
                        domainClass = ref("${cls.name}DomainClass")
                        grailsApplication = ref('grailsApplication')
                        mappingContext = ref("grailsDomainClassMappingContext")
                    }
                }
            }

            for(dataSourceName in dataSources) {

                boolean isDefault = dataSourceName == defaultDataSourceBeanName
                if(isDefault) continue

                String suffix = '_' + dataSourceName
                def sessionFactoryName = isDefault ? defaultSessionFactoryBeanName : "sessionFactory$suffix"
                String datastoreBeanName = "hibernateDatastore$suffix"
                "$datastoreBeanName"(MethodInvokingFactoryBean) {
                    targetObject = ref("hibernateDatastore")
                    targetMethod = "getDatastoreForConnection"
                    arguments = [dataSourceName]
                }
                // the main SessionFactory bean
                if(!beanDefinitionRegistry.containsBeanDefinition(sessionFactoryName)) {
                    "$sessionFactoryName"((datastoreBeanName):"getSessionFactory")
                }

                String transactionManagerBeanName = "transactionManager$suffix"
                if (!beanDefinitionRegistry.containsBeanDefinition(transactionManagerBeanName)) {
                    "$transactionManagerBeanName"((datastoreBeanName):"getTransactionManager")
                }
                boolean osivEnabled = config.getProperty("hibernate${suffix}.osiv.enabled", Boolean, true)
                boolean isWebApplication = beanDefinitionRegistry?.containsBeanDefinition("dispatcherServlet") ||
                        beanDefinitionRegistry?.containsBeanDefinition("grailsControllerHelper")

                if (isWebApplication && osivEnabled) {
                    "flushingRedirectEventListener$suffix"(FlushOnRedirectEventListener, datastoreBeanName)
                    "openSessionInViewInterceptor$suffix"(GrailsOpenSessionInViewInterceptor) {
                        hibernateDatastore = ref(datastoreBeanName)
                    }
                }
            }


        }
        return beanDefinitions
    }


    protected GenericApplicationContext createApplicationContext() {
        GenericApplicationContext applicationContext = new GenericApplicationContext()
        if (configuration instanceof ConfigurableEnvironment) {
            applicationContext.environment = (ConfigurableEnvironment) configuration
        }
        applicationContext
    }

}
