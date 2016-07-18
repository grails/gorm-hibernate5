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

import org.grails.datastore.gorm.events.*;
import org.grails.datastore.gorm.validation.constraints.MappingContextAwareConstraintFactory;
import org.grails.datastore.gorm.validation.constraints.builtin.UniqueConstraint;
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry;
import org.grails.datastore.mapping.core.ConnectionNotFoundException;
import org.grails.datastore.mapping.core.DatastoreUtils;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.datastore.mapping.core.connections.ConnectionSources;
import org.grails.datastore.mapping.core.connections.ConnectionSourcesInitializer;
import org.grails.datastore.mapping.core.connections.SingletonConnectionSources;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.engine.event.DatastoreInitializedEvent;
import org.grails.datastore.mapping.model.AbstractMappingContext;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings;
import org.grails.orm.hibernate.cfg.GrailsDomainBinder;
import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.grails.orm.hibernate.cfg.Settings;
import org.grails.orm.hibernate.connections.HibernateConnectionSource;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceFactory;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings;
import org.grails.orm.hibernate.support.ClosureEventTriggeringInterceptor;
import org.hibernate.SessionFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertyResolver;
import org.grails.orm.hibernate.multitenancy.*;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
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

    public HibernateDatastore(ConnectionSources<SessionFactory, HibernateConnectionSourceSettings> connectionSources, HibernateMappingContext mappingContext, ConfigurableApplicationEventPublisher eventPublisher) {
        super(connectionSources, mappingContext);

        GrailsHibernateTransactionManager hibernateTransactionManager = new GrailsHibernateTransactionManager();
        HibernateConnectionSource defaultConnectionSource = (HibernateConnectionSource) connectionSources.getDefaultConnectionSource();
        hibernateTransactionManager.setDataSource(defaultConnectionSource.getDataSource());
        hibernateTransactionManager.setSessionFactory(defaultConnectionSource.getSource());
        this.transactionManager = hibernateTransactionManager;
        this.eventPublisher = eventPublisher;
        this.eventTriggeringInterceptor = new EventTriggeringInterceptor(this);

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
        }

        this.gormEnhancer = initialize();
        this.eventPublisher.publishEvent( new DatastoreInitializedEvent(this) );
    }

    public HibernateDatastore(PropertyResolver configuration, HibernateConnectionSourceFactory connectionSourceFactory, ConfigurableApplicationEventPublisher eventPublisher) {
        this(ConnectionSourcesInitializer.create(connectionSourceFactory, configuration), connectionSourceFactory.getMappingContext(), eventPublisher);
    }

    public HibernateDatastore(PropertyResolver configuration, HibernateConnectionSourceFactory connectionSourceFactory) {
        this(ConnectionSourcesInitializer.create(connectionSourceFactory, configuration), connectionSourceFactory.getMappingContext(), new DefaultApplicationEventPublisher());
    }

    public HibernateDatastore(PropertyResolver configuration, ConfigurableApplicationEventPublisher eventPublisher, Class...classes) {
        this(configuration, new HibernateConnectionSourceFactory(classes), eventPublisher);
    }

    public HibernateDatastore(PropertyResolver configuration, Class...classes) {
        this(configuration, new HibernateConnectionSourceFactory(classes));
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
        eventPublisher.addApplicationListener(new AutoTimestampEventListener(this));
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
        return new HibernateGormEnhancer(this, transactionManager, getConnectionSources().getDefaultConnectionSource().getSettings());
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
