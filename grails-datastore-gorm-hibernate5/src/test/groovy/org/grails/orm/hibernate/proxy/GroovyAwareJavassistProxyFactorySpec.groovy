package org.grails.orm.hibernate.proxy

import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.proxy.HibernateProxy
import org.hibernate.proxy.ProxyFactory
import spock.lang.Ignore
import spock.lang.Specification

/**
 * Created by graemerocher on 16/11/16.
 */
class GroovyAwareJavassistProxyFactorySpec extends Specification {

    @Ignore
    void "test create proxy factory dynamically"() {

        when:"A proxy factory is created dynamically"
        def pool = ClassPool.default
        final CtClass superClass = pool.get( AbstractGroovyAwareJavassistProxyFactory.name );
        def clz = pool.makeClass("${AbstractGroovyAwareJavassistProxyFactory.package.name}.GroovyAwareJavassistProxyFactory", superClass)

        CtMethod getProxyMethod = superClass.getMethods().find { CtMethod m -> m.name == 'getProxy' }
        def parameterTypes = getProxyMethod.getParameterTypes()
        CtMethod newGetProxyMethod = new CtMethod(getProxyMethod.getReturnType(), getProxyMethod.name, parameterTypes, clz)
        newGetProxyMethod.setBody(
                '''return org.grails.orm.hibernate.proxy.ProxyFactorySupport.createProxyFactory(
                        entityName, persistentClass, interfaces, $1, getIdentifierMethod,
                        setIdentifierMethod, componentIdType, $2, org.hibernate.internal.util.ReflectHelper.overridesEquals(persistentClass)                        
                );'''
        )

        clz.addMethod(
            newGetProxyMethod
        )
        ProxyFactory factory = clz.toClass().newInstance()

        factory.postInstantiate("test", GroovyAwareJavassistProxyFactorySpec, [] as Set, null, null, null)
        def proxy = factory.getProxy(1D, Mock(SessionImplementor))

        then:"The proxy is not null"
        proxy.writeReplace() == 'test'
    }
}
