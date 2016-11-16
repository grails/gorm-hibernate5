package org.grails.orm.hibernate.proxy

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.hibernate.proxy.HibernateProxy
import org.hibernate.proxy.ProxyFactory
import org.hibernate.type.CompositeType

import java.lang.reflect.Method

/**
 * In order to support all versions of Hibernate 5.x we have to create proxy factories dynamically
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class ProxyFactorySupport {

    private static final Class<ProxyFactory> FACTORY
    static {
        try {
            def pool = ClassPool.default
            final CtClass superClass = pool.get( AbstractGroovyAwareJavassistProxyFactory.name );
            def clz = pool.makeClass("${AbstractGroovyAwareJavassistProxyFactory.package.name}.GroovyAwareJavassistProxyFactory", superClass)

            CtMethod getProxyMethod = superClass.getMethods().find { CtMethod m -> m.name == 'getProxy' }
            def parameterTypes = getProxyMethod.getParameterTypes()
            CtMethod newGetProxyMethod = new CtMethod(getProxyMethod.getReturnType(), getProxyMethod.name, parameterTypes, clz)
            newGetProxyMethod.setBody(
                '''return org.grails.orm.hibernate.proxy.ProxyFactorySupport.createProxy(
                        entityName, persistentClass, interfaces, $1, getIdentifierMethod,
                        setIdentifierMethod, componentIdType, $2, org.hibernate.internal.util.ReflectHelper.overridesEquals(persistentClass)                        
                );'''
            )

            clz.addMethod(
                    newGetProxyMethod
            )
            FACTORY = clz.toClass()
        } catch (Throwable e) {
            throw new ConfigurationException("Unable to create proxy factory, probably due to a classpath conflict. Check the version of Hibernate on the path is correct (should be Hibernate 5+): ${e.message}", e)
        }
    }
    /**
     * @return Dynamically builds the proxy factory
     */
    static ProxyFactory createProxyFactory(){
        return FACTORY.newInstance()
    }

    /**
     * Creates a proxy factory dynamically at runtime
     *
     * @return The proxy factory instance
     */
    @CompileDynamic
    static HibernateProxy createProxy(
            final String entityName,
            final Class<?> persistentClass,
            final Class<?>[] interfaces,
            final Serializable id,
            final Method getIdentifierMethod,
            final Method setIdentifierMethod,
            final CompositeType componentIdType,
            final Object sessionObject,
            final boolean overridesEquals) {
        def lazyInitializer = new GroovyAwareJavassistLazyInitializer(
                entityName, persistentClass, interfaces, id, getIdentifierMethod,
                setIdentifierMethod, componentIdType, sessionObject, overridesEquals)
        return lazyInitializer.createProxy()
    }
}
