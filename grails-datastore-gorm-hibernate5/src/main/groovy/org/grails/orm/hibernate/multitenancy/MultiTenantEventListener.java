package org.grails.orm.hibernate.multitenancy;

import grails.gorm.multitenancy.Tenants;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent;
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener;
import org.grails.datastore.mapping.engine.event.PersistenceEventListener;
import org.grails.datastore.mapping.engine.event.PreInsertEvent;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.TenantId;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.event.PostQueryEvent;
import org.grails.datastore.mapping.query.event.PreQueryEvent;
import org.grails.datastore.mapping.reflect.EntityReflector;
import org.grails.orm.hibernate.HibernateDatastore;
import org.grails.orm.hibernate.IHibernateTemplate;
import org.grails.orm.hibernate.cfg.GrailsDomainBinder;
import org.grails.orm.hibernate.query.HibernateHqlQuery;
import org.grails.orm.hibernate.query.HibernateQuery;
import org.hibernate.Session;
import org.springframework.context.ApplicationEvent;

import java.io.Serializable;

/**
 * @author Graeme Rocher
 * @since 6.0
 */
public class MultiTenantEventListener implements PersistenceEventListener {
    @Override
    public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return PreQueryEvent.class.isAssignableFrom(eventType) || PostQueryEvent.class.isAssignableFrom(eventType) || PreInsertEvent.class.isAssignableFrom(eventType);
    }

    @Override
    public boolean supportsSourceType(Class<?> sourceType) {
        return HibernateDatastore.class.isAssignableFrom(sourceType);
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if(supportsEventType(event.getClass()) && supportsSourceType(event.getSource().getClass())) {
            HibernateDatastore hibernateDatastore = (HibernateDatastore) event.getSource();
            if(event instanceof  PreQueryEvent) {
                PreQueryEvent preQueryEvent = (PreQueryEvent) event;
                Query query = preQueryEvent.getQuery();

                PersistentEntity entity = query.getEntity();
                if(entity.isMultiTenant()) {
                    HibernateMultiTenancySupport.enableFilter(hibernateDatastore, entity);
                }
            }
            else if(event instanceof PostQueryEvent) {
                PostQueryEvent postQueryEvent = (PostQueryEvent) event;
                Query query = postQueryEvent.getQuery();
                PersistentEntity entity = query.getEntity();
                if(entity.isMultiTenant()) {
                    HibernateMultiTenancySupport.disableFilter(hibernateDatastore, entity);
                }
            }
            else if(event instanceof PreInsertEvent) {
                PreInsertEvent preInsertEvent = (PreInsertEvent) event;
                PersistentEntity entity = preInsertEvent.getEntity();
                if(entity.isMultiTenant()) {
                    TenantId tenantId = entity.getTenantId();
                    EntityReflector reflector = entity.getReflector();
                    Serializable currentId = Tenants.currentId(hibernateDatastore.getClass());
                    reflector.setProperty(preInsertEvent.getEntityObject(), tenantId.getName(), currentId);
                }
            }
        }
    }

    @Override
    public int getOrder() {
        return DEFAULT_ORDER;
    }
}

