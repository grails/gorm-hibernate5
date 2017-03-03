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
import org.grails.datastore.gorm.jdbc.connections.DataSourceSettings;
import org.grails.datastore.gorm.jdbc.schema.SchemaHandler;
import org.grails.datastore.gorm.utils.ClasspathEntityScanner;
import org.grails.datastore.gorm.validation.constraints.MappingContextAwareConstraintFactory;
import org.grails.datastore.gorm.validation.constraints.builtin.UniqueConstraint;
import org.grails.datastore.gorm.validation.constraints.registry.ConstraintRegistry;
import org.grails.datastore.mapping.core.ConnectionNotFoundException;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.DatastoreUtils;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.core.connections.*;
import org.grails.datastore.mapping.core.exceptions.ConfigurationException;
import org.grails.datastore.mapping.engine.event.DatastoreInitializedEvent;
import org.grails.datastore.mapping.model.DatastoreConfigurationException;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.multitenancy.AllTenantsResolver;
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings;
import org.grails.datastore.mapping.validation.ValidatorRegistry;
import org.grails.orm.hibernate.cfg.GrailsDomainBinder;
import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.grails.orm.hibernate.cfg.Settings;
import org.grails.orm.hibernate.connections.HibernateConnectionSource;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceFactory;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings;
import org.grails.orm.hibernate.multitenancy.MultiTenantEventListener;
import org.grails.orm.hibernate.support.ClosureEventTriggeringInterceptor;
import org.grails.orm.hibernate.support.HibernateVersionSupport;
import org.hibernate.SessionFactory;
import org.hibernate.boot.SchemaAutoTooling;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.*;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.env.PropertyResolver;

import javax.sql.DataSource;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Datastore implementation that uses a Hibernate SessionFactory underneath.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class HibernateDatastore extends AbstractHibernateDatastore implements MessageSourceAware {
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
    public HibernateDatastore(final ConnectionSources<SessionFactory, HibernateConnectionSourceSettings> connectionSources, final HibernateMappingContext mappingContext, final ConfigurableApplicationEventPublisher eventPublisher) {
        super(connectionSources, mappingContext);

        GrailsHibernateTransactionManager hibernateTransactionManager = new GrailsHibernateTransactionManager();
        HibernateConnectionSource defaultConnectionSource = (HibernateConnectionSource) connectionSources.getDefaultConnectionSource();
        hibernateTransactionManager.setDataSource(defaultConnectionSource.getDataSource());
        hibernateTransactionManager.setSessionFactory(defaultConnectionSource.getSource());
        this.transactionManager = hibernateTransactionManager;
        this.eventPublisher = eventPublisher;
        this.eventTriggeringInterceptor = new EventTriggeringInterceptor(this);

        HibernateConnectionSourceSettings settings = defaultConnectionSource.getSettings();
        HibernateConnectionSourceSettings.HibernateSettings hibernateSettings = settings.getHibernate();

        ClosureEventTriggeringInterceptor interceptor = (ClosureEventTriggeringInterceptor) hibernateSettings.getEventTriggeringInterceptor();
        interceptor.setDatastore(this);
        interceptor.setEventPublisher(eventPublisher);
        registerEventListeners(this.eventPublisher);
        configureValidatorRegistry(settings, mappingContext);
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
                SingletonConnectionSources<SessionFactory, HibernateConnectionSourceSettings> singletonConnectionSources = new SingletonConnectionSources<>(connectionSource, connectionSources.getBaseConfiguration());
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

                    for (Serializable tenantId : tenantIds) {
                        addTenantForSchemaInternal(tenantId.toString());
                    }
                }
                else {
                    Collection<String> allSchemas = schemaHandler.resolveSchemaNames(defaultConnectionSource.getDataSource());
                    for (String schema : allSchemas) {
                        addTenantForSchemaInternal(schema);
                    }
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
     * Construct a Hibernate datastore scanning the given packages
     *
     * @param configuration The configuration
     * @param eventPublisher The event publisher
     * @param packagesToScan The packages to scan
     */
    public HibernateDatastore(PropertyResolver configuration, ConfigurableApplicationEventPublisher eventPublisher,  Package...packagesToScan) {
        this(configuration, eventPublisher, new ClasspathEntityScanner().scan(packagesToScan));
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
     * Construct a Hibernate datastore scanning the given packages
     *
     * @param configuration The configuration
     * @param packagesToScan The packages to scan
     */
    public HibernateDatastore(PropertyResolver configuration, Package...packagesToScan) {
        this(configuration, new ClasspathEntityScanner().scan(packagesToScan));
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
     * Construct a Hibernate datastore scanning the given packages
     *
     * @param configuration The configuration
     * @param packagesToScan The packages to scan
     */
    public HibernateDatastore(Map<String,Object> configuration, Package...packagesToScan) {
        this(DatastoreUtils.createPropertyResolver(configuration), packagesToScan);
    }

    /**
     * Constructor used purely for testing purposes. Creates a datastore with an in-memory database and dbCreate set to 'create-drop'
     *
     * @param classes The classes
     */
    public HibernateDatastore(Class...classes) {
        this(DatastoreUtils.createPropertyResolver(Collections.singletonMap(Settings.SETTING_DB_CREATE, (Object) "create-drop")), new HibernateConnectionSourceFactory(classes));
    }

    /**
     * Construct a Hibernate datastore scanning the given packages
     *
     * @param packagesToScan The packages to scan
     */
    public HibernateDatastore(Package...packagesToScan) {
        this(new ClasspathEntityScanner().scan(packagesToScan));
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

    @Override
    public HibernateMappingContext getMappingContext() {
        return (HibernateMappingContext) super.getMappingContext();
    }

    @Override
    public void setMessageSource(MessageSource messageSource) {
        HibernateMappingContext mappingContext = getMappingContext();
        ValidatorRegistry validatorRegistry = createValidatorRegistry(messageSource);
        HibernateConnectionSourceSettings settings = getConnectionSources().getDefaultConnectionSource().getSettings();
        configureValidatorRegistry(settings, mappingContext, validatorRegistry, messageSource);
    }

    protected void registerEventListeners(ConfigurableApplicationEventPublisher eventPublisher) {
        eventPublisher.addApplicationListener(new AutoTimestampEventListener(this));
        if(multiTenantMode == MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR) {
            eventPublisher.addApplicationListener(new MultiTenantEventListener());
        }
        eventPublisher.addApplicationListener(eventTriggeringInterceptor);
    }

    protected void configureValidatorRegistry(HibernateConnectionSourceSettings settings, HibernateMappingContext mappingContext) {
        StaticMessageSource messageSource = new StaticMessageSource();
        ValidatorRegistry defaultValidatorRegistry = createValidatorRegistry(messageSource);
        configureValidatorRegistry(settings, mappingContext, defaultValidatorRegistry, messageSource);
    }

    protected void configureValidatorRegistry(HibernateConnectionSourceSettings settings, HibernateMappingContext mappingContext, ValidatorRegistry validatorRegistry, MessageSource messageSource) {
        if(validatorRegistry instanceof ConstraintRegistry) {
            ((ConstraintRegistry)validatorRegistry).addConstraintFactory(
                    new MappingContextAwareConstraintFactory(UniqueConstraint.class, messageSource, mappingContext)
            );
        }
        mappingContext.setValidatorRegistry(
                validatorRegistry
        );
    }



    protected HibernateGormEnhancer initialize() {
        final HibernateConnectionSource defaultConnectionSource = (HibernateConnectionSource) getConnectionSources().getDefaultConnectionSource();
        if(multiTenantMode == MultiTenancySettings.MultiTenancyMode.SCHEMA) {
            return new HibernateGormEnhancer(this, transactionManager, defaultConnectionSource.getSettings()) {
                @Override
                public List<String> allQualifiers(Datastore datastore, PersistentEntity entity) {
                    List<String> allQualifiers = super.allQualifiers(datastore, entity);
                    if( MultiTenant.class.isAssignableFrom(entity.getJavaClass()) ) {
                        if(tenantResolver instanceof AllTenantsResolver) {
                            Iterable<Serializable> tenantIds = ((AllTenantsResolver) tenantResolver).resolveTenantIds();
                            for(Serializable id : tenantIds) {
                                allQualifiers.add(id.toString());
                            }
                        }
                        else {
                            Collection<String> schemaNames = schemaHandler.resolveSchemaNames(defaultConnectionSource.getDataSource());
                            for (String schemaName : schemaNames) {
                                // skip common internal schemas
                                if(schemaName.equals("INFORMATION_SCHEMA") || schemaName.equals("PUBLIC")) continue;
                                for (String connectionName : datastoresByConnectionSource.keySet()) {
                                    if(schemaName.equalsIgnoreCase(connectionName)) {
                                        allQualifiers.add(connectionName);
                                    }
                                }
                            }
                        }
                    }

                    return allQualifiers;
                }
            };
        }
        else {
            return new HibernateGormEnhancer(this, transactionManager, defaultConnectionSource.getSettings());
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
            HibernateConnectionSourceSettings settings = getConnectionSources().getDefaultConnectionSource().getSettings();
            HibernateConnectionSourceSettings.HibernateSettings hibernateSettings = settings.getHibernate();
            ClosureEventTriggeringInterceptor interceptor = (ClosureEventTriggeringInterceptor) hibernateSettings.getEventTriggeringInterceptor();
            interceptor.setDatastore(this);
            interceptor.setEventPublisher(eventPublisher);
            MappingContext mappingContext = getMappingContext();
            // make messages from the application context available to validation
            ValidatorRegistry validatorRegistry = createValidatorRegistry(applicationContext);
            configureValidatorRegistry(settings, (HibernateMappingContext) mappingContext, validatorRegistry, applicationContext);
            mappingContext.setValidatorRegistry(
                    validatorRegistry
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
                previousMode = HibernateVersionSupport.getFlushMode(session);
                HibernateVersionSupport.setFlushMode(session, org.hibernate.FlushMode.valueOf(flushMode.name()));
            }
            try {
                reset = callable.call();
            } catch (Exception e) {
                reset = false;
            }
        }
        finally {
            if (session != null && previousMode != null && reset) {
                HibernateVersionSupport.setFlushMode(session, previousMode);
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

    @Override
    public void addTenantForSchema(String schemaName) {
        addTenantForSchemaInternal(schemaName);
        for (PersistentEntity persistentEntity : mappingContext.getPersistentEntities()) {
            gormEnhancer.registerEntity(persistentEntity);
        }

    }

    private void addTenantForSchemaInternal(String schemaName) {
        if( multiTenantMode != MultiTenancySettings.MultiTenancyMode.SCHEMA ) {
            throw new ConfigurationException("The method [addTenantForSchema] can only be called with multi-tenancy mode SCHEMA. Current mode is: " + multiTenantMode);
        }
        HibernateConnectionSourceFactory factory = (HibernateConnectionSourceFactory) connectionSources.getFactory();
        HibernateConnectionSource defaultConnectionSource = (HibernateConnectionSource) connectionSources.getDefaultConnectionSource();
        HibernateConnectionSourceSettings tenantSettings;
        try {
            tenantSettings = connectionSources.getDefaultConnectionSource().getSettings().clone();
        } catch (CloneNotSupportedException e) {
            throw new ConfigurationException("Couldn't clone default Hibernate settings! " + e.getMessage(), e);
        }
        tenantSettings.getHibernate().put("default_schema", schemaName);

        String dbCreate = tenantSettings.getDataSource().getDbCreate();

        SchemaAutoTooling schemaAutoTooling = dbCreate != null ?  SchemaAutoTooling.interpret(dbCreate) : null;
        if(schemaAutoTooling != null && schemaAutoTooling != SchemaAutoTooling.VALIDATE && schemaAutoTooling != SchemaAutoTooling.NONE) {

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

        DefaultConnectionSource<DataSource, DataSourceSettings> dataSourceConnectionSource = new DefaultConnectionSource<>(schemaName, defaultConnectionSource.getDataSource(), tenantSettings.getDataSource());
        ConnectionSource<SessionFactory, HibernateConnectionSourceSettings> connectionSource = factory.create(schemaName, dataSourceConnectionSource, tenantSettings);
        SingletonConnectionSources<SessionFactory, HibernateConnectionSourceSettings> singletonConnectionSources = new SingletonConnectionSources<>(connectionSource, connectionSources.getBaseConfiguration());
        HibernateDatastore childDatastore = new HibernateDatastore(singletonConnectionSources, (HibernateMappingContext) mappingContext, eventPublisher) {
            @Override
            protected HibernateGormEnhancer initialize() {
                return null;
            }
        };
        datastoresByConnectionSource.put(connectionSource.getName(), childDatastore);
    }
}
