package grails.gorm.tests.inheritance

import grails.gorm.annotation.Entity
import grails.gorm.tests.GormDatastoreSpec

class SubclassToOneProxySpec extends GormDatastoreSpec {

    void "the hasOne is a proxy and unwraps"() {
        given:
        SubclassProxy dog = new SubclassProxy().save()
        new HasOneProxy(superclassProxy: dog).save()
        session.flush()
        session.clear()
        HasOneProxy owner = HasOneProxy.first()

        expect:
        session.mappingContext.proxyFactory.isProxy(owner.@superclassProxy)
    }

    @Override
    List getDomainClasses() {
        [SuperclassProxy, SubclassProxy, HasOneProxy]
    }
}

@Entity
class SuperclassProxy {
}

// @Entity
// https://issues.apache.org/jira/browse/GROOVY-5106 - The interface GormEntity cannot be implemented more than once with different arguments: org.grails.datastore.gorm.GormEntity<grails.gorm.tests.XXX> and org.grails.datastore.gorm.GormEntity<grails.gorm.tests.XXX>
class SubclassProxy extends SuperclassProxy {
}

@Entity
class HasOneProxy {
    SuperclassProxy superclassProxy
}
