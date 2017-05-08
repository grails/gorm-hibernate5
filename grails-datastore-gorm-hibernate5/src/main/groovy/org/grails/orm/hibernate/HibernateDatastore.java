/* Copyright (C) 2011 SpringSource
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
package org.grails.orm.hibernate;

import grails.gorm.MultiTenant;
import org.grails.datastore.gorm.events.AutoTimestampEventListener;
import org.grails.datastore.gorm.events.ConfigurableApplicationContextEventPublisher;
import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher;
import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher;
import org.grails.datastore.gorm.validation.constraints.MappingContextAwareConstraintFactory;
import org.grails.datastore.gorm.validation.constraints.builtin.UniqueConstraint;
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry;
import org.grails.datastore.mapping.core.ConnectionNotFoundException;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.DatastoreUtils;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.core.connections.*;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.engine.event.DatastoreInitializedEvent;
import org.grails.datastore.mapping.model.AbstractMappingContext;
import org.grails.datastore.mapping.model.DatastoreConfigurationException;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver;
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings;
import org.grails.orm.hibernate.cfg.GrailsDomainBinder;
import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.grails.orm.hibernate.cfg.Settings;
import org.grails.orm.hibernate.connections.HibernateConnectionSource;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceFactory;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings;
import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.datastore.gorm.jdbc.connections.*;
import org.grails.orm.hibernate.multitenancy.MultiTenantEventListener;
import org.grails.orm.hibernate.support.ClosureEventTriggeringInterceptor;
import org.hibernate.SessionFactory;
import org.hibernate.boot.SchemaAutoTooling;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertyResolver;

import javax.sql.DataSource;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Datastore implementation that uses a Hibernate SessionFactory underneath.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class HibernateDatastore extends AbstractHibernateDatastore {
    protected final GrailsHibernateTransactionManager transactionManager;
    protected ConfigurableApplicationEventPublisher eventPublisher;
    protected final HibernateGormEnhancer gormEnhancer;
    protected final Map<String, HibernateDatastore> datastoresByConnectionSource = new LinkedHashMap<>();

    /**
     * Create a new HibernateDatastore for the given connection sources and mapping context
     *
     * @param connectionSources The {@link ConnectionSources} instance
     * @param mappingContext The {@link MappingContext} instance
     * @param eventPublisher The {@link ConfigurableApplicationEventPublisher} instance
     */
    public HibernateDatastore(ConnectionSources<SessionFactory, HibernateConnectionSourceSettings> connectionSources, HibernateMappingContext mappingContext, ConfigurableApplicationEventPublisher eventPublisher) {
        super(connectionSources, mappingContext);

        GrailsHibernateTransactionManager hibernateTransactionManager = new GrailsHibernateTransactionManager();
        HibernateConnectionSource defaultConnectionSource = (HibernateConnectionSource) connectionSources.getDefaultConnectionSource();
        hibernateTransactionManager.setDataSource(defaultConnectionSource.getDataSource());
        hibernateTransactionManager.setSessionFactory(defaultConnectionSource.getSource());
        this.transactionManager = hibernateTransactionManager;
        this.eventPublisher = eventPublisher;
        this.eventTriggeringInterceptor = new EventTriggeringInterceptor(this);
        this.autoTimestampEventListener = new AutoTimestampEventListener(this);

        HibernateConnectionSourceSettings.HibernateSettings hibernateSettings = defaultConnectionSource.getSettings().getHibernate();

        ClosureEventTriggeringInterceptor interceptor = (ClosureEventTriggeringInterceptor) hibernateSettings.getEventTriggeringInterceptor();
        interceptor.setDatastore(this);
        interceptor.setEventPublisher(eventPublisher);
        registerEventListeners(this.eventPublisher);
        configureValidationRegistry(connectionSources.getBaseConfiguration(), mappingContext);
        this.mappingContext.addMappingContextListener(new MappingContext.Listener() {
            @Override
            public void persistentEntityAdded(PersistentEntity entity) {
                gormEnhancer.registerEntity(entity);
            }
        });
        initializeConverters(this.mappingContext);

        if(!(connectionSources instanceof SingletonConnectionSources)) {

            Iterable<ConnectionSource<SessionFactory, HibernateConnectionSourceSettings>> allConnectionSources = connectionSources.getAllConnectionSources();
            for (ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> connectionSource : allConnectionSources) {
                SingletonConnectionSources singletonConnectionSources = new SingletonConnectionSources(connectionSource, connectionSources.getBaseConfiguration());
                HibernateDatastore childDatastore;

                if(ConnectionSource.DEFAULT.equals(connectionSource.getName())) {
                    childDatastore = this;
                }
                else {
                    childDatastore = new HibernateDatastore(singletonConnectionSources, mappingContext, eventPublisher) {
                        @Override
                        protected HibernateGormEnhancer initialize() {
                            return null;
                        }
                    };
                }
                datastoresByConnectionSource.put(connectionSource.getName(), childDatastore);
            }

            if(multiTenantMode == MultiTenancySettings.MultiTenancyMode.SCHEMA) {
                if(this.tenantResolver instanceof AllTenantsResolver) {
                    AllTenantsResolver allTenantsResolver = (AllTenantsResolver) tenantResolver;
                    Iterable<Serializable> tenantIds = allTenantsResolver.resolveTenantIds();
                    HibernateConnectionSourceFactory factory = (HibernateConnectionSourceFactory) connectionSources.getFactory();
                    
                    for (Serializable tenantId : tenantIds) {
                        HibernateConnectionSourceSettings settings;
                        try {
                            settings = connectionSources.getDefaultConnectionSource().getSettings().clone();
                        } catch (CloneNotSupportedException e) {
                            throw new ConfigurationException("Couldn't clone default Hibernate settings! " + e.getMessage(), e);
                        }
                        String schemaName = tenantId.toString();
                        DefaultConnectionSource<DataSource, DataSourceSettings> dataSourceConnectionSource = new DefaultConnectionSource<>(schemaName, defaultConnectionSource.getDataSource(), settings.getDataSource());
                        settings.getHibernate().put("default_schema", schemaName);

                        String dbCreate = settings.getDataSource().getDbCreate();

                        if(dbCreate != null && SchemaAutoTooling.interpret(dbCreate) != SchemaAutoTooling.VALIDATE) {

                            Connection connection = null;
                            try {
                                connection = defaultConnectionSource.getDataSource().getConnection();
                                try {
                                    schemaHandler.useSchema(connection, schemaName);
                                } catch (Exception e) {
                                    // schema doesn't exist
                                    schemaHandler.createSchema(connection, schemaName);
                                }
                            } catch (SQLException e) {
                                throw new DatastoreConfigurationException(String.format("Failed to create schema for name [%s]", schemaName));
                            }
                            finally {
                                if(connection != null) {
                                    try {
                                        connection.close();
                                    } catch (SQLException e) {
                                        //ignore
                                    }
                                }
                            }
                        }

                        ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> connectionSource = factory.create(schemaName, dataSourceConnectionSource, settings);
                        SingletonConnectionSources<SessionFactory, HibernateConnectionSourceSettings>  singletonConnectionSources = new SingletonConnectionSources(connectionSource, connectionSources.getBaseConfiguration());
                        HibernateDatastore childDatastore = new HibernateDatastore(singletonConnectionSources, mappingContext, eventPublisher) {
                            @Override
                            protected HibernateGormEnhancer initialize() {
                                return null;
                            }
                        };
                        datastoresByConnectionSource.put(connectionSource.getName(), childDatastore);
                    }
                }
                else {
                    throw new ConfigurationException("Configured TenantResolver must implement interface AllTenantsResolver in mode SCHEMA");
                }
            }
        }


        this.gormEnhancer = initialize();
        this.eventPublisher.publishEvent( new DatastoreInitializedEvent(this) );
    }

    /**
     * Create a new HibernateDatastore for the given connection sources and mapping context
     *
     * @param configuration The configuration
     * @param connectionSourceFactory The {@link HibernateConnectionSourceFactory} instance
     * @param eventPublisher The {@link ConfigurableApplicationEventPublisher} instance
     */
    public HibernateDatastore(PropertyResolver configuration, HibernateConnectionSourceFactory connectionSourceFactory, ConfigurableApplicationEventPublisher eventPublisher) {
        this(ConnectionSourcesInitializer.create(connectionSourceFactory, DatastoreUtils.preparePropertyResolver(configuration, "dataSource", "hibernate", "grails")), connectionSourceFactory.getMappingContext(), eventPublisher);
    }

    /**
     * Create a new HibernateDatastore for the given connection sources and mapping context
     *
     * @param configuration The configuration
     * @param connectionSourceFactory The {@link HibernateConnectionSourceFactory} instance
     */
    public HibernateDatastore(PropertyResolver configuration, HibernateConnectionSourceFactory connectionSourceFactory) {
        this(ConnectionSourcesInitializer.create(connectionSourceFactory, DatastoreUtils.preparePropertyResolver(configuration, "dataSource", "hibernate", "grails")), connectionSourceFactory.getMappingContext(), new DefaultApplicationEventPublisher());
    }

    /**
     * Create a new HibernateDatastore for the given connection sources and mapping context
     *
     * @param configuration The configuration
     * @param eventPublisher The {@link ConfigurableApplicationEventPublisher} instance
     * @param classes The persistent classes
     */
    public HibernateDatastore(PropertyResolver configuration, ConfigurableApplicationEventPublisher eventPublisher, Class...classes) {
        this(configuration, new HibernateConnectionSourceFactory(classes), eventPublisher);
    }

    /**
     * Create a new HibernateDatastore for the given connection sources and mapping context
     *
     * @param configuration The configuration
     * @param classes The persistent classes
     */
    public HibernateDatastore(PropertyResolver configuration, Class...classes) {
        this(configuration, new HibernateConnectionSourceFactory(classes));
    }
    /**
     * Constructor used purely for testing purposes. Creates a datastore with an in-memory database and dbCreate set to 'create-drop'
     *
     * @param classes The classes
     */
    public HibernateDatastore(Map<String,Object> configuration, Class...classes) {
        this(DatastoreUtils.createPropertyResolver(configuration), new HibernateConnectionSourceFactory(classes));
    }


    /**
     * Constructor used purely for testing purposes. Creates a datastore with an in-memory database and dbCreate set to 'create-drop'
     *
     * @param classes The classes
     */
    public HibernateDatastore(Class...classes) {
        this(DatastoreUtils.createPropertyResolver(Collections.singletonMap(Settings.SETTING_DB_CREATE, (Object) "create-drop")), new HibernateConnectionSourceFactory(classes));
    }

    @Override
    public ApplicationEventPublisher getApplicationEventPublisher() {
        return this.eventPublisher;
    }




    /**
     * @return The {@link org.springframework.transaction.PlatformTransactionManager} instance
     */
    public GrailsHibernateTransactionManager getTransactionManager() {
        return transactionManager;
    }

    /**
     * Obtain a child {@link HibernateDatastore} by connection name
     *
     * @param connectionName The connection name
     *
     * @return The {@link HibernateDatastore}
     */
    public HibernateDatastore getDatastoreForConnection(String connectionName) {
        if(connectionName.equals(Settings.SETTING_DATASOURCE) || connectionName.equals(ConnectionSource.DEFAULT)) {
            return this;
        } else {
            HibernateDatastore hibernateDatastore = this.datastoresByConnectionSource.get(connectionName);
            if(hibernateDatastore == null) {
                throw new ConfigurationException("DataSource not found for name ["+connectionName+"] in configuration. Please check your multiple data sources configuration and try again.");
            }
            return hibernateDatastore;
        }
    }

    protected void registerEventListeners(ConfigurableApplicationEventPublisher eventPublisher) {
        eventPublisher.addApplicationListener(autoTimestampEventListener);
        if(multiTenantMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            eventPublisher.addApplicationListener(new MultiTenantEventListener());
        }
        eventPublisher.addApplicationListener(eventTriggeringInterceptor);
    }

    protected void configureValidationRegistry(PropertyResolver configuration, HibernateMappingContext mappingContext) {
        DefaultValidatorRegistry defaultValidatorRegistry = new DefaultValidatorRegistry(mappingContext, configuration);
        defaultValidatorRegistry.addConstraintFactory(
                new MappingContextAwareConstraintFactory(UniqueConstraint.class, defaultValidatorRegistry.getMessageSource(), mappingContext)
        );
        mappingContext.setValidatorRegistry(
                defaultValidatorRegistry
        );
    }

    protected HibernateGormEnhancer initialize() {
        if(multiTenantMode == MultiTenancySettings.MultiTenancyMode.SCHEMA) {
            return new HibernateGormEnhancer(this, transactionManager, getConnectionSources().getDefaultConnectionSource().getSettings()) {
                @Override
                public List<String> allQualifiers(Datastore datastore, PersistentEntity entity) {
                    List<String> allQualifiers = super.allQualifiers(datastore, entity);
                    if( MultiTenant.class.isAssignableFrom(entity.getJavaClass()) ) {
                        Iterable<Serializable> tenantIds = ((AllTenantsResolver) tenantResolver).resolveTenantIds();
                        for (Serializable tenantId : tenantIds) {
                            allQualifiers.add(tenantId.toString());
                        }
                    }

                    return allQualifiers;
                }
            };
        }
        else {
            return new HibernateGormEnhancer(this, transactionManager, getConnectionSources().getDefaultConnectionSource().getSettings());
        }
    }

    @Override
    protected Session createSession(PropertyResolver connectionDetails) {
        return new HibernateSession(this, sessionFactory);
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if(applicationContext instanceof ConfigurableApplicationContext) {
            super.setApplicationContext(applicationContext);

            for (HibernateDatastore hibernateDatastore : datastoresByConnectionSource.values()) {
                if(hibernateDatastore != this) {
                    hibernateDatastore.setApplicationContext(applicationContext);
                }
            }
            this.eventPublisher = new ConfigurableApplicationContextEventPublisher((ConfigurableApplicationContext) applicationContext);
            HibernateConnectionSourceSettings.HibernateSettings hibernateSettings = getConnectionSources().getDefaultConnectionSource().getSettings().getHibernate();
            ClosureEventTriggeringInterceptor interceptor = (ClosureEventTriggeringInterceptor) hibernateSettings.getEventTriggeringInterceptor();
            interceptor.setDatastore(this);
            interceptor.setEventPublisher(eventPublisher);
            MappingContext mappingContext = getMappingContext();
            // make messages from the application context available to validation
            ((AbstractMappingContext) mappingContext).setValidatorRegistry(
                    new DefaultValidatorRegistry(mappingContext, connectionSources.getBaseConfiguration(), applicationContext)
            );

            registerEventListeners(eventPublisher);
            this.eventPublisher.publishEvent( new DatastoreInitializedEvent(this) );
        }
    }

    @Override
    public IHibernateTemplate getHibernateTemplate(int flushMode) {
        return new GrailsHibernateTemplate(getSessionFactory(), this, flushMode);
    }

    @Override
    public void withFlushMode(FlushMode flushMode, Callable<Boolean> callable) {
        final org.hibernate.Session session = sessionFactory.getCurrentSession();
        org.hibernate.FlushMode previousMode = null;
        Boolean reset = true;
        try {
            if (session != null) {
                previousMode = session.getFlushMode();
                session.setFlushMode(org.hibernate.FlushMode.valueOf(flushMode.name()));
            }
            try {
                reset = callable.call();
            } catch (Exception e) {
                reset = false;
            }
        }
        finally {
            if (session != null && previousMode != null && reset) {
                session.setFlushMode(previousMode);
            }
        }
    }

    @Override
    public org.hibernate.Session openSession() {
        return this.sessionFactory.openSession();
    }

    @Override
    public Session getCurrentSession() throws ConnectionNotFoundException {
        // HibernateSession, just a thin wrapper around default session handling so simply return a new instance here
        return new HibernateSession(this, sessionFactory, getDefaultFlushMode());
    }

    @Override
    public void destroy() throws Exception {
        try {
            super.destroy();
        } finally {
            GrailsDomainBinder.clearMappingCache();
            this.gormEnhancer.close();
        }
    }
}
