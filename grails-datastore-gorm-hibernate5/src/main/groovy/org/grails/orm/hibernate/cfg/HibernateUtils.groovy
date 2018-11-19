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

}
