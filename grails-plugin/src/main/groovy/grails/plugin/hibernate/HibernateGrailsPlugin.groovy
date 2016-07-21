package grails.plugin.hibernate

import grails.config.Config
import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.core.GrailsDomainClass
import grails.orm.bootstrap.HibernateDatastoreSpringInitializer
import grails.plugins.Plugin
import grails.util.Environment
import grails.validation.ConstrainedProperty
import groovy.transform.CompileStatic
import org.grails.config.PropertySourcesConfig
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.orm.hibernate.validation.PersistentConstraintFactory
import org.grails.orm.hibernate.validation.UniqueConstraint
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.convert.converter.Converter
import org.springframework.core.convert.support.ConfigurableConversionService
import org.springframework.core.env.PropertyResolver
/**
 * Plugin that integrates Hibernate into a Grails application
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class HibernateGrailsPlugin extends Plugin {

    public static final String DEFAULT_DATA_SOURCE_NAME = HibernateDatastoreSpringInitializer.DEFAULT_DATA_SOURCE_NAME

    def grailsVersion = '3.1.0 > *'

    def author = 'Grails Core Team'
    def title = 'Hibernate 5 for Grails'
    def description = 'Provides integration between Grails and Hibernate 5 through GORM'
    def documentation = 'http://grails.github.io/grails-data-mapping/latest/'

    def observe = ['domainClass']
    def loadAfter = ['controllers', 'domainClass']
    def watchedResources = ['file:./grails-app/conf/hibernate/**.xml']
    def pluginExcludes = ['src/templates/**']

    def license = 'APACHE'
    def organization = [name: 'Grails', url: 'http://grails.org']
    def issueManagement = [system: 'Github', url: 'https://github.com/grails/grails-data-mapping/issues']
    def scm = [url: 'https://github.com/grails/grails-data-mapping']

    Set<String> dataSourceNames

    Closure doWithSpring() {{->
        ConfigurableApplicationContext applicationContext = (ConfigurableApplicationContext) applicationContext

        GrailsApplication grailsApplication = grailsApplication
        Config config = grailsApplication.config
        if(config instanceof PropertySourcesConfig) {
            ConfigurableConversionService conversionService = applicationContext.getEnvironment().getConversionService()
            conversionService.addConverter(new Converter<String, Class>() {
                @Override
                Class convert(String source) {
                    Class.forName(source)
                }
            })
            ((PropertySourcesConfig)config).setConversionService(conversionService)
        }

        ConstrainedProperty.registerNewConstraint(UniqueConstraint.UNIQUE_CONSTRAINT,
                new PersistentConstraintFactory(applicationContext,
                        UniqueConstraint))


        def domainClasses = grailsApplication.getArtefacts(DomainClassArtefactHandler.TYPE)
                .findAll() { GrailsClass cls ->
            GrailsDomainClass dc = (GrailsDomainClass)cls
            dc.mappingStrategy != "none" && dc.mappingStrategy == GrailsDomainClass.GORM
        }
        .collect() { GrailsClass cls -> cls.clazz }

        def springInitializer = new HibernateDatastoreSpringInitializer((PropertyResolver)config, domainClasses)
        if(!Environment.isDevelopmentMode()) {
            // set no-op for default DDL auto setting if not in development mode
            springInitializer.ddlAuto = ''
        }

        springInitializer.enableReload = Environment.isDevelopmentMode()
        springInitializer.registerApplicationIfNotPresent = false
        springInitializer.grailsPlugin = true
        dataSourceNames = springInitializer.dataSources
        def beans = springInitializer.getBeanDefinitions((BeanDefinitionRegistry)applicationContext)

        beans.delegate = delegate
        beans.call()
    }}

    @Override
    void onShutdown(Map<String, Object> event) {
        ConstrainedProperty.removeConstraint(UniqueConstraint.UNIQUE_CONSTRAINT, UniqueConstraint)
    }

    @Override
    void onChange(Map<String, Object> event) {
        // TODO: rewrite onChange handling
    }

}
