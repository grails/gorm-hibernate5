package org.grails.orm.hibernate.proxy;

import org.hibernate.HibernateException;
import org.hibernate.proxy.ProxyFactory;
import org.hibernate.type.CompositeType;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Abstract implementation of the ProxyFactory interface
 *
 * @author Graeme Rocher
 * @since 6.1
 */
public abstract class AbstractGroovyAwareJavassistProxyFactory implements ProxyFactory, Serializable {
    private static final long serialVersionUID = 8959336753472691947L;
    protected static final Class<?>[] NO_CLASSES = {};
    protected Class<?> persistentClass;
    protected String entityName;
    protected Class<?>[] interfaces;
    protected Method getIdentifierMethod;
    protected Method setIdentifierMethod;
    protected CompositeType componentIdType;

    @Override
    public void postInstantiate(String entityName, Class persistentClass, Set<Class> interfaces, Method getIdentifierMethod, Method setIdentifierMethod, CompositeType componentIdType) throws HibernateException {
        this.entityName = entityName;
        this.persistentClass = persistentClass;
        this.interfaces = interfaces.toArray(NO_CLASSES);
        this.getIdentifierMethod = getIdentifierMethod;
        this.setIdentifierMethod = setIdentifierMethod;
        this.componentIdType = componentIdType;
    }
}
