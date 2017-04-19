package org.grails.orm.hibernate.proxy;

import groovy.lang.GroovyObject;
import javassist.util.proxy.MethodFilter;
import javassist.util.proxy.ProxyFactory;
import org.apache.commons.logging.LogFactory;
import org.grails.datastore.mapping.proxy.EntityProxy;
import org.grails.orm.hibernate.cfg.HibernateUtils;
import org.hibernate.HibernateException;
import org.hibernate.proxy.pojo.BasicLazyInitializer;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility methods for creating Javassist ProxyFactory instances
 *
 * @author Graeme Rocher
 * @since 6.1.2
 */
class JavassistEntityProxyUtils {

    private static final String WRITE_CLASSES_DIRECTORY = System.getProperty("javassist.writeDirectory");
    private static final Set<String> GROOVY_METHODS = new HashSet<>(Arrays.asList("$getStaticMetaClass"));
    private static final MethodFilter METHOD_FILTERS = new MethodFilter() {
        public boolean isHandled(Method m) {
            // skip finalize methods
            return m.getName().indexOf("super$") == -1 &&
                !GROOVY_METHODS.contains(m.getName()) &&
                !(m.getParameterTypes().length == 0 && (m.getName().equals("finalize")));
        }
    };

    static Class<?> createProxyClass(Class<?> persistentClass, Class<?>[] interfaces) throws HibernateException {
        // note: interfaces is assumed to already contain HibernateProxy.class

        try {
            Set<Class<?>> allInterfaces = new HashSet<>();
            if(interfaces != null) {
                allInterfaces.addAll(Arrays.asList(interfaces));
            }
            allInterfaces.add(GroovyObject.class);
            allInterfaces.add(EntityProxy.class);
            ProxyFactory factory = createJavassistProxyFactory(persistentClass, allInterfaces.toArray(new Class<?>[allInterfaces.size()]));
            Class<?> proxyClass = factory.createClass();
            HibernateUtils.enhanceProxyClass(proxyClass);
            return proxyClass;
        }
        catch (Throwable t) {
            LogFactory.getLog(BasicLazyInitializer.class).error(
                    "Javassist Enhancement failed: " + persistentClass.getName(), t);
            throw new HibernateException("Javassist Enhancement failed: " + persistentClass.getName(), t);
        }
    }

    private static ProxyFactory createJavassistProxyFactory(Class<?> persistentClass, Class<?>[] interfaces) {
        ProxyFactory factory = new ProxyFactory();
        factory.setSuperclass(persistentClass);
        factory.setInterfaces(interfaces);
        factory.setFilter(METHOD_FILTERS);
        factory.setUseCache(true);
        if (WRITE_CLASSES_DIRECTORY != null) {
            factory.writeDirectory = WRITE_CLASSES_DIRECTORY;
        }
        return factory;
    }
}
