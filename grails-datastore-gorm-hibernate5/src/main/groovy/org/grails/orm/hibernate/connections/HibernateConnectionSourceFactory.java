package org.grails.orm.hibernate.connections;

import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.orm.hibernate.HibernateEventListeners;
import org.grails.orm.hibernate.cfg.GrailsDomainBinder;
import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration;
import org.grails.orm.hibernate.jdbc.connections.DataSourceSettings;
import org.grails.orm.hibernate.jdbc.connections.SpringDataSourceConnectionSourceFactory;
import org.grails.orm.hibernate.support.AbstractClosureEventTriggeringInterceptor;
import org.grails.orm.hibernate.support.ClosureEventTriggeringInterceptor;
import org.hibernate.Interceptor;
import org.hibernate.SessionFactory;
import org.hibernate.boot.spi.MetadataContributor;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.NamingStrategy;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;

/**
 * Constructs {@link SessionFactory} instances from a {@link HibernateMappingContext}
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class HibernateConnectionSourceFactory extends AbstractHibernateConnectionSourceFactory implements ApplicationContextAware {

    static {
        // use Slf4j logging by default
        System.setProperty("org.jboss.logging.provider", "slf4j");
    }

    protected HibernateMappingContext mappingContext;
    protected Class[] persistentClasses = new Class[0];
    private ApplicationContext applicationContext;
    protected HibernateEventListeners hibernateEventListeners;
    protected AbstractClosureEventTriggeringInterceptor closureEventTriggeringInterceptor;
    protected Interceptor interceptor;
    protected MetadataContributor metadataContributor;

    public HibernateConnectionSourceFactory(Class...classes) {
        this.persistentClasses = classes;
    }

    public Class[] getPersistentClasses() {
        return persistentClasses;
    }

    @Autowired(required = false)
    public void setHibernateEventListeners(HibernateEventListeners hibernateEventListeners) {
        this.hibernateEventListeners = hibernateEventListeners;
    }

    @Autowired(required = false)
    public void setClosureEventTriggeringInterceptor(AbstractClosureEventTriggeringInterceptor closureEventTriggeringInterceptor) {
        this.closureEventTriggeringInterceptor = closureEventTriggeringInterceptor;
    }

    @Autowired(required = false)
    public void setInterceptor(Interceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Autowired(required = false)
    public void setMetadataContributor(MetadataContributor metadataContributor) {
        this.metadataContributor = metadataContributor;
    }

    public HibernateMappingContext getMappingContext() {
        return mappingContext;
    }

    @Override
    public ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> create(String name, ConnectionSource<DataSource, DataSourceSettings> dataSourceConnectionSource, HibernateConnectionSourceSettings settings) {
        boolean isDefault = ConnectionSource.DEFAULT.equals(name);

        if(mappingContext == null) {
            mappingContext = new HibernateMappingContext(settings, applicationContext, persistentClasses);
        }

        HibernateConnectionSourceSettings.HibernateSettings hibernateSettings = settings.getHibernate();
        Class<? extends Configuration> configClass = hibernateSettings.getConfigClass();

        HibernateMappingContextConfiguration configuration;
        if(configClass != null) {
            if( !HibernateMappingContextConfiguration.class.isAssignableFrom(configClass) ) {
                throw new ConfigurationException("The configClass setting must be a subclass for [HibernateMappingContextConfiguration]");
            }
            else {
                configuration = (HibernateMappingContextConfiguration) BeanUtils.instantiate(configClass);
            }
        }
        else {
            configuration = new HibernateMappingContextConfiguration();
        }

        if(applicationContext != null && applicationContext.containsBean(dataSourceConnectionSource.getName())) {
            configuration.setApplicationContext(this.applicationContext);
        }
        else {
            configuration.setDataSourceConnectionSource(dataSourceConnectionSource);
        }

        Resource[] configLocations = hibernateSettings.getConfigLocations();
        if (configLocations != null) {
            for (Resource resource : configLocations) {
                // Load Hibernate configuration from given location.
                try {
                    configuration.configure(resource.getURL());
                } catch (IOException e) {
                    throw new ConfigurationException("Cannot configure Hibernate config for location: " + resource.getFilename(), e);
                }
            }
        }

        Resource[] mappingLocations = hibernateSettings.getMappingLocations();
        if (mappingLocations != null) {
            // Register given Hibernate mapping definitions, contained in resource files.
            for (Resource resource : mappingLocations) {
                try {
                    configuration.addInputStream(resource.getInputStream());
                } catch (IOException e) {
                    throw new ConfigurationException("Cannot configure Hibernate config for location: " + resource.getFilename(), e);
                }
            }
        }

        Resource[] cacheableMappingLocations = hibernateSettings.getCacheableMappingLocations();
        if (cacheableMappingLocations != null) {
            // Register given cacheable Hibernate mapping definitions, read from the file system.
            for (Resource resource : cacheableMappingLocations) {
                try {
                    configuration.addCacheableFile(resource.getFile());
                } catch (IOException e) {
                    throw new ConfigurationException("Cannot configure Hibernate config for location: " + resource.getFilename(), e);
                }
            }
        }

        Resource[] mappingJarLocations = hibernateSettings.getMappingJarLocations();
        if (mappingJarLocations != null) {
            // Register given Hibernate mapping definitions, contained in jar files.
            for (Resource resource : mappingJarLocations) {
                try {
                    configuration.addJar(resource.getFile());
                } catch (IOException e) {
                    throw new ConfigurationException("Cannot configure Hibernate config for location: " + resource.getFilename(), e);
                }
            }
        }

        Resource[] mappingDirectoryLocations = hibernateSettings.getMappingDirectoryLocations();
        if (mappingDirectoryLocations != null) {
            // Register all Hibernate mapping definitions in the given directories.
            for (Resource resource : mappingDirectoryLocations) {
                File file = null;
                try {
                    file = resource.getFile();
                } catch (IOException e) {
                    throw new ConfigurationException("Cannot configure Hibernate config for location: " + resource.getFilename(), e);
                }
                if (!file.isDirectory()) {
                    throw new IllegalArgumentException("Mapping directory location [" + resource + "] does not denote a directory");
                }
                configuration.addDirectory(file);
            }
        }

        if (this.interceptor != null) {
            configuration.setInterceptor(this.interceptor);
        }

        if (this.metadataContributor != null) {
            configuration.setMetadataContributor(metadataContributor);
        }

        Class[] annotatedClasses = hibernateSettings.getAnnotatedClasses();
        if (annotatedClasses != null) {
            configuration.addAnnotatedClasses(annotatedClasses);
        }

        String[] annotatedPackages = hibernateSettings.getAnnotatedPackages();
        if (annotatedPackages != null) {
            configuration.addPackages(annotatedPackages);
        }

        String[] packagesToScan = hibernateSettings.getPackagesToScan();
        if (packagesToScan != null) {
            configuration.scanPackages(packagesToScan);
        }

        Class<? extends AbstractClosureEventTriggeringInterceptor> closureEventTriggeringInterceptorClass = hibernateSettings.getClosureEventTriggeringInterceptorClass();

        AbstractClosureEventTriggeringInterceptor eventTriggeringInterceptor;

        if(closureEventTriggeringInterceptorClass == null) {
            if(this.closureEventTriggeringInterceptor != null) {
                eventTriggeringInterceptor = this.closureEventTriggeringInterceptor;
                hibernateSettings.eventTriggeringInterceptor(this.closureEventTriggeringInterceptor);
            }
            else {
                AbstractClosureEventTriggeringInterceptor fromConfiguration = hibernateSettings.getEventTriggeringInterceptor();
                eventTriggeringInterceptor = fromConfiguration != null ? fromConfiguration : new ClosureEventTriggeringInterceptor();
                hibernateSettings.eventTriggeringInterceptor(eventTriggeringInterceptor);
            }
        }
        else {
            eventTriggeringInterceptor = BeanUtils.instantiate(closureEventTriggeringInterceptorClass);
        }

        try {
            Class<? extends NamingStrategy> namingStrategy = hibernateSettings.getNaming_strategy();
            if(namingStrategy != null) {
                GrailsDomainBinder.configureNamingStrategy(name, namingStrategy);
            }
        } catch (Throwable e) {
            throw new ConfigurationException("Error configuring naming strategy: " + e.getMessage(), e);
        }

        configuration.setEventListeners(hibernateSettings.toHibernateEventListeners(eventTriggeringInterceptor));
        HibernateEventListeners hibernateEventListeners = hibernateSettings.getHibernateEventListeners();
        configuration.setHibernateEventListeners(this.hibernateEventListeners != null ? this.hibernateEventListeners  : hibernateEventListeners);
        configuration.setHibernateMappingContext(mappingContext);
        configuration.setDataSourceName(name);
        configuration.setSessionFactoryBeanName(isDefault ? "sessionFactory" : "sessionFactory_" + name);
        configuration.addProperties(settings.toProperties());
        SessionFactory sessionFactory = configuration.buildSessionFactory();
        return new HibernateConnectionSource(name, sessionFactory, dataSourceConnectionSource, settings);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if(applicationContext != null) {
            this.applicationContext = applicationContext;
            SpringDataSourceConnectionSourceFactory springDataSourceConnectionSourceFactory = new SpringDataSourceConnectionSourceFactory();
            springDataSourceConnectionSourceFactory.setApplicationContext(applicationContext);
            this.dataSourceConnectionSourceFactory = springDataSourceConnectionSourceFactory;
        }
    }
}
