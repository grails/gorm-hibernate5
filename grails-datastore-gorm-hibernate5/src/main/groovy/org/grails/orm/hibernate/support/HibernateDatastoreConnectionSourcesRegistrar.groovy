package org.grails.orm.hibernate.support

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.spring.beans.factory.InstanceFactoryBean
import org.hibernate.SessionFactory
import org.springframework.beans.BeansException
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.config.ConstructorArgumentValues
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.transaction.PlatformTransactionManager

/**
 * A factory bean that looks up a datastore by connection name
 *
 * @author Graeme Rocher
 * @since 6.0.6
 */
@CompileStatic
class HibernateDatastoreConnectionSourcesRegistrar implements BeanDefinitionRegistryPostProcessor {
    static final String DEFAULT_DATASOURCE_NAME = 'dataSource'
    final Iterable<String> dataSourceNames

    HibernateDatastoreConnectionSourcesRegistrar(Iterable<String> dataSourceNames) {
        this.dataSourceNames = dataSourceNames
    }

    @Override
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        for(String dataSourceName in dataSourceNames) {
            if(dataSourceName != ConnectionSource.DEFAULT && DEFAULT_DATASOURCE_NAME != dataSourceName) {
                String suffix = '_' + dataSourceName
                String sessionFactoryName = "sessionFactory$suffix"
                String transactionManagerBeanName = "transactionManager$suffix"

                def sessionFactoryBean = new RootBeanDefinition()
                sessionFactoryBean.setTargetType(SessionFactory)
                sessionFactoryBean.setBeanClass(InstanceFactoryBean)
                def args = new ConstructorArgumentValues()
                args.addGenericArgumentValue("#{hibernateDatastore.getDatastoreForConnection('$dataSourceName').sessionFactory}".toString())
                sessionFactoryBean.setConstructorArgumentValues(
                        args
                )
                registry.registerBeanDefinition(
                        sessionFactoryName,
                        sessionFactoryBean
                )

                def transactionManagerBean = new RootBeanDefinition()
                transactionManagerBean.setTargetType(PlatformTransactionManager)
                transactionManagerBean.setBeanClass(InstanceFactoryBean)
                def txMgrArgs = new ConstructorArgumentValues()
                txMgrArgs.addGenericArgumentValue("#{hibernateDatastore.getDatastoreForConnection('$dataSourceName').transactionManager}".toString())
                transactionManagerBean.setConstructorArgumentValues(
                        txMgrArgs
                )
                registry.registerBeanDefinition(
                        transactionManagerBeanName,
                        transactionManagerBean
                )
            }
        }
    }

    @Override
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // no-op
    }
}
