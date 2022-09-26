/*
 * Copyright 2004-2008 the original author or authors.
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
package org.grails.orm.hibernate.proxy;

import java.io.Serializable;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.engine.AssociationQueryExecutor;
import org.grails.datastore.mapping.proxy.ProxyFactory;
import org.grails.datastore.mapping.proxy.ProxyHandler;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;
import org.hibernate.Hibernate;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.HibernateProxyHelper;

/**
 * Implementation of the ProxyHandler interface for Hibernate using org.hibernate.Hibernate
 * and HibernateProxyHelper where possible.
 *
 * @author Graeme Rocher
 * @since 1.2.2
 */
public class HibernateProxyHandler implements ProxyHandler, ProxyFactory {

    @Override
    public boolean isInitialized(Object o) {
        return Hibernate.isInitialized(o);
    }

    @Override
    public boolean isInitialized(Object obj, String associationName) {
        try {
            Object proxy = ClassPropertyFetcher.getInstancePropertyValue(obj, associationName);
            return isInitialized(proxy);
        }
        catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public Object unwrap(Object object) {
        return unwrapIfProxy(object);
    }

    @Override
    public Serializable getIdentifier(Object o) {
        if (o instanceof HibernateProxy) {
            return ((HibernateProxy)o).getHibernateLazyInitializer().getIdentifier();
        }
        else {
            //TODO seems we can get the id here if its has normal getId
            return null;
        }
    }

    @Override
    public Class<?> getProxiedClass(Object o) {
        if(o instanceof HibernateProxy) {
            return HibernateProxyHelper.getClassWithoutInitializingProxy(o);
        }
        else {
            return o.getClass();
        }
    }

    public Object unwrapIfProxy(Object instance) {
        if (instance instanceof PersistentCollection) {
            initialize(instance);
            return instance;
        }
        return Hibernate.unproxy(instance);
    }

    @Override
    public boolean isProxy(Object o) {
        return (o instanceof HibernateProxy)  || (o instanceof PersistentCollection);
    }

    @Override
    public void initialize(Object o) {
        Hibernate.initialize(o);
    }

    @Override
    public <T> T createProxy(Session session, Class<T> type, Serializable key) {
        throw new UnsupportedOperationException("createProxy not supported in HibernateProxyHandler");
    }

    @Override
    public <T, K extends Serializable> T createProxy(Session session, AssociationQueryExecutor<K, T> executor, K associationKey) {
        throw new UnsupportedOperationException("createProxy not supported in HibernateProxyHandler");
    }

    public Object unwrapProxy(Object proxy) {
        return unwrapIfProxy(proxy);
    }

    public HibernateProxy getAssociationProxy(Object obj, String associationName) {
        try {
            Object proxy = ClassPropertyFetcher.getInstancePropertyValue(obj, associationName);
            if (proxy instanceof HibernateProxy) {
                return (HibernateProxy) proxy;
            }
            return null;
        }
        catch (RuntimeException e) {
            return null;
        }
    }
}
