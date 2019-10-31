package org.grails.orm.hibernate.proxy;

import org.grails.datastore.mapping.proxy.EntityProxyMethodHandler;
import org.hibernate.proxy.LazyInitializer;

/**
 * An {@link EntityProxyMethodHandler} for Groovy objects
 *
 * @author Graeme Rocher
 * @since 6.1.2
 */
public class HibernateGroovyObjectMethodHandler extends EntityProxyMethodHandler {
    private Object target;
    private final Object originalSelf;
    private final LazyInitializer lazyInitializer;

    public HibernateGroovyObjectMethodHandler(Class<?> proxyClass, Object originalSelf, LazyInitializer lazyInitializer) {
        super(proxyClass);
        this.originalSelf = originalSelf;
        this.lazyInitializer = lazyInitializer;
    }

    @Override
    protected Object resolveDelegate(Object self) {
        if (self != originalSelf) {
            throw new IllegalStateException("self instance has changed.");
        }
        if (target == null) {
            target = lazyInitializer.getImplementation();
        }
        return target;
    }

    @Override
    protected Object isProxyInitiated(Object self) {
        return target != null || !lazyInitializer.isUninitialized();
    }

    @Override
    protected Object getProxyKey(Object self) {
        return lazyInitializer.getIdentifier();
    }
}
