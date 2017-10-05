/*
 * Copyright 2013 the original author or authors.
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
package org.grails.orm.hibernate.cfg

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import javassist.LoaderClassPath
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.proxy.EntityProxy
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.datastore.mapping.reflect.NameUtils
import org.hibernate.proxy.HibernateProxy
import org.springframework.beans.PropertyAccessorFactory

@CompileStatic
class HibernateUtils {



    /**
     * Overrides a getter on a property that is a Hibernate proxy in order to make sure the initialized object is returned hence avoiding Hibernate proxy hell.
     */
    static void handleLazyProxy(PersistentEntity entity, PersistentProperty property) {
        String propertyName = property.name
        if(propertyName != GormProperties.PROPERTIES && propertyName != "property") {

            String getterName = NameUtils.getGetterName(propertyName)
            String setterName = NameUtils.getSetterName(propertyName)

            GroovyObject mc = (GroovyObject)entity.javaClass.metaClass
            EntityReflector reflector = entity.getReflector()

            mc.setProperty(getterName, {->
                def thisObject = getDelegate()
                if(thisObject instanceof EntityProxy) {
                    EntityProxy entityProxy = (EntityProxy) thisObject
                    entityProxy.initialize()
                    thisObject = entityProxy.getTarget()
                }
                def propertyValue = reflector.getProperty(thisObject, propertyName)
                if (propertyValue instanceof HibernateProxy) {
                    propertyValue = GrailsHibernateUtil.unwrapProxy(propertyValue)
                }
                return propertyValue
            })
            mc.setProperty(setterName, {
                PropertyAccessorFactory.forBeanPropertyAccess(getDelegate()).setPropertyValue(propertyName, it)
            })

            def children = entity.getMappingContext().getDirectChildEntities(entity)
            for (PersistentEntity sub in children) {
                handleLazyProxy(sub, sub.getPropertyByName(property.name))
            }
        }
    }

    // http://jira.codehaus.org/browse/GROOVY-6138 prevents using CompileStatic for this method
    @CompileStatic(TypeCheckingMode.SKIP)
    static void enhanceProxyClass(Class proxyClass) {
        MetaClass mc = proxyClass.metaClass
        MetaMethod grailsEnhancedMetaMethod = mc.getStaticMetaMethod("grailsEnhanced", (Class[])null)
        if (grailsEnhancedMetaMethod != null && grailsEnhancedMetaMethod.invoke(proxyClass, null) == proxyClass) {
            return
        }

        MetaClass superMc = proxyClass.getSuperclass().metaClass


        // hasProperty
        registerMetaMethod(mc, 'hasProperty', { String name ->
            Object obj = getDelegate()
            boolean result = superMc.hasProperty(obj, name)
            if (!result) {
                Object unwrapped = GrailsHibernateUtil.unwrapProxy((HibernateProxy)obj)
                result = unwrapped.getMetaClass().hasProperty(obj, name)
            }
            return result
        })
        // respondsTo
        registerMetaMethod(mc, 'respondsTo', { String name ->
            Object obj = getDelegate()
            def result = superMc.respondsTo(obj, name)
            if (!result) {
                Object unwrapped = GrailsHibernateUtil.unwrapProxy((HibernateProxy)obj)
                result = unwrapped.getMetaClass().respondsTo(obj, name)
            }
            result
        })
        registerMetaMethod(mc, 'respondsTo', { String name, Object[] args ->
            Object obj = getDelegate()
            def result = superMc.respondsTo(obj, name, args)
            if (!result) {
                Object unwrapped = GrailsHibernateUtil.unwrapProxy((HibernateProxy)obj)
                result = unwrapped.getMetaClass().respondsTo(obj, name, args)
            }
            result
        })

        // setter
        registerMetaMethod(mc, 'propertyMissing', { String name, Object val ->
            Object obj = getDelegate()
            try {
                superMc.setProperty(proxyClass, obj, name, val, true, true)
            } catch (MissingPropertyException e) {
                Object unwrapped = GrailsHibernateUtil.unwrapProxy((HibernateProxy)obj)
                unwrapped.getMetaClass().setProperty(unwrapped, name, val)
            }
        })

        // getter
        registerMetaMethod(mc, 'propertyMissing', { String name ->
            Object obj = getDelegate()
            try {
                return superMc.getProperty(proxyClass, obj, name, true, true)
            } catch (MissingPropertyException e) {
                Object unwrapped = GrailsHibernateUtil.unwrapProxy((HibernateProxy)obj)
                unwrapped.getMetaClass().getProperty(unwrapped, name)
            }
        })

        registerMetaMethod(mc, 'methodMissing', { String name, Object args ->
            Object obj = getDelegate()
            Object[] argsArray = (Object[])args
            try {
                superMc.invokeMethod(proxyClass, obj, name, argsArray, true, true)
            } catch (MissingMethodException e) {
                Object unwrapped = GrailsHibernateUtil.unwrapProxy((HibernateProxy)obj)
                unwrapped.getMetaClass().invokeMethod(unwrapped, name, argsArray)
            }
        })

        mc.static.grailsEnhanced = {->proxyClass}
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    private static final registerMetaMethod(MetaClass mc, String name, Closure c) {
        mc."$name" = c
    }


}
