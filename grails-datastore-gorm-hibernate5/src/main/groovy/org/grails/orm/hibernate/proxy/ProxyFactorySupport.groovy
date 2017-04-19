package org.grails.orm.hibernate.proxy

import groovy.transform.CompileStatic
import javassist.*
import javassist.util.proxy.MethodHandler
import javassist.util.proxy.Proxy
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.hibernate.internal.util.ReflectHelper
import org.hibernate.proxy.HibernateProxy
import org.hibernate.proxy.ProxyFactory
import org.hibernate.proxy.pojo.BasicLazyInitializer
import org.hibernate.proxy.pojo.javassist.SerializableProxy

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * In order to support all versions of Hibernate 5.x we have to create proxy factories dynamically
 *
 * This class is full of horrible hacks that we can remove when support for Hibernate versions below 5.2 is dropped.
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class ProxyFactorySupport {

    private static final Class<ProxyFactory> FACTORY
    static {
        try {
            ClassPool pool = ClassPool.default
            CtClass classArrayType = pool.getCtClass("java.lang.Class[]")

            // first make the lazy initializer
            String initializerName = "${ProxyFactorySupport.package.name}.GroovyAwareJavassistLazyInitializer"
            CtClass initializerSuperClass = getClassFromPool(pool, BasicLazyInitializer.name)
            CtClass initializerClass = pool.makeClass(initializerName, initializerSuperClass)
            initializerClass.addInterface(getClassFromPool(pool, MethodHandler.name))

            // Add Field: boolean constructed;
            def constructedField = new CtField(getClassFromPool(pool, "boolean"), "constructed", initializerClass)
            constructedField.setModifiers(Modifier.PUBLIC)
            initializerClass.addField(constructedField)

            // Add Field: boolean constructed;
            def groovyObjectMethodHandler = new CtField(getClassFromPool(pool, HibernateGroovyObjectMethodHandler.name), "groovyObjectMethodHandler", initializerClass)
            groovyObjectMethodHandler.setModifiers(Modifier.PRIVATE | Modifier.FINAL)
            initializerClass.addField(groovyObjectMethodHandler)

            // Add field: HibernateProxy proxy
            CtClass proxyType = getClassFromPool(pool, HibernateProxy.name)
            def proxyField = new CtField(proxyType, "proxy", initializerClass)
            proxyField.setModifiers(Modifier.PRIVATE | Modifier.FINAL)
            initializerClass.addField(proxyField)

            // Add field: Class[] interfaces
            def interfacesField = new CtField(classArrayType, "interfaces", initializerClass)
            interfacesField.setModifiers(Modifier.PRIVATE | Modifier.FINAL)
            initializerClass.addField(interfacesField)

            // Add constructor
            CtClass[] existingParameterTypes = initializerSuperClass.constructors[0].parameterTypes
            CtClass[] newParameterTypes = (CtClass[])Arrays.copyOf(existingParameterTypes, existingParameterTypes.length + 2)

            newParameterTypes[existingParameterTypes.length] = classArrayType
            newParameterTypes[existingParameterTypes.length + 1] = proxyType

            def constructor = new CtConstructor(newParameterTypes, initializerClass)
            constructor.setBody("""{
        super(\$1, \$2, \$3, \$4, \$5, \$6, \$7, \$8);
        this.interfaces = \$9;
        this.proxy = \$10;
        ( (${Proxy.name}) proxy ).setHandler( this );
        this.groovyObjectMethodHandler = new ${HibernateGroovyObjectMethodHandler.name}(this.persistentClass, this.proxy, this);
        this.constructed = true;
        }""")
            initializerClass.addConstructor(constructor)

            CtClass objectType = getClassFromPool(pool, Object.name)
            // Add method: Object serializableProxy()
            CtMethod serializableProxyMethod = new CtMethod(objectType, "serializableProxy", [] as CtClass[], initializerClass)
            serializableProxyMethod.setModifiers(Modifier.PROTECTED)
            serializableProxyMethod.setBody("""{
        return new ${SerializableProxy.name}(
                getEntityName(),
                persistentClass,
                interfaces,
                getIdentifier(),
                Boolean.FALSE,
                getIdentifierMethod,
                setIdentifierMethod,
                componentIdType);       
}""")

            // Add method: Object invoke(Object, Method, Method, Object[])
            CtClass methodType = getClassFromPool(pool, Method.name)
            CtClass objectArrayType = getClassFromPool(pool, "java.lang.Object[]")
            CtMethod invokeMethod = new CtMethod(objectType, "invoke", [objectType, methodType, methodType, objectArrayType] as CtClass[] , initializerClass)
            invokeMethod.setBody("""{
        // while constructor is running
        if (\$2.getName().equals("getHibernateLazyInitializer")) {
            return this;
        }

        Object result = groovyObjectMethodHandler.handleInvocation(\$1, \$2, \$4);
        if (groovyObjectMethodHandler.wasHandled(result)) {
           return result;
        }

        if (constructed) {
            try {
                result = invoke(\$2, \$4, \$1);
            }
            catch (Throwable t) {
                throw new Exception(t.getCause());
            }
            if (result == INVOKE_IMPLEMENTATION) {
                Object target = getImplementation();
                final Object returnValue;
                try {
                    if (${ReflectHelper.name}.isPublic(persistentClass, \$2)) {
                        if (!\$2.getDeclaringClass().isInstance(target)) {
                            throw new ClassCastException(target.getClass().getName());
                        }
                        returnValue = \$2.invoke(target, \$4);
                    }
                    else {
                        if (!\$2.isAccessible()) {
                            \$2.setAccessible(true);
                        }
                        returnValue = \$2.invoke(target, \$4);
                    }
                    return returnValue == target ? \$1 : returnValue;
                }
                catch (${InvocationTargetException.name} ite) {
                    throw ite.getTargetException();
                }
            }
            return result;
        }

        return \$3.invoke(proxy, \$4);
    }
""")
            initializerClass.addMethod(invokeMethod)

            initializerClass.toClass()

            // now make the proxy factory
            String factoryName = "${ProxyFactorySupport.package.name}.GroovyAwareJavassistProxyFactory"
            CtClass factorySuperClass = getClassFromPool(pool, AbstractGroovyAwareJavassistProxyFactory.name)
            CtClass factorCls = pool.makeClass(factoryName, factorySuperClass)

            CtMethod getProxyMethod = factorySuperClass.getMethods().find { CtMethod m -> m.name == 'getProxy' }
            CtClass[] parameterTypes = getProxyMethod.getParameterTypes()
            CtMethod newGetProxyMethod = new CtMethod(getProxyMethod.getReturnType(), getProxyMethod.name, parameterTypes, factorCls)
            newGetProxyMethod.setBody(
                """{

                        try {
                            final org.hibernate.proxy.HibernateProxy proxy = (org.hibernate.proxy.HibernateProxy) proxyClass.newInstance();
                            ${initializerName} initializer =  new ${initializerName}(entityName, 
                                                                                     persistentClass, 
                                                                                     \$1, 
                                                                                     getIdentifierMethod,
                                                                                     setIdentifierMethod, 
                                                                                     componentIdType, 
                                                                                     \$2, 
                                                                                     org.hibernate.internal.util.ReflectHelper.overridesEquals(persistentClass), 
                                                                                     interfaces,
                                                                                     proxy);                            
                            return proxy;
                        }
                        catch (Throwable t) {
                            throw new org.hibernate.HibernateException( "Javassist Enhancement failed: " + persistentClass, t );
                        }                        
                        
                    }"""
            )

            factorCls.addMethod(
                    newGetProxyMethod
            )
            FACTORY = factorCls.toClass()
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

    private static CtClass getClassFromPool(ClassPool pool, String superClassName) {
        CtClass superClass = pool.getOrNull(superClassName)
        if (superClass == null) {
            pool.appendClassPath(
                    new LoaderClassPath(ProxyFactorySupport.classLoader)
            )
        }
        superClass = pool.get(superClassName)
        return superClass
    }
}
