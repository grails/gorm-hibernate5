package grails.test.mixin.hibernate

import grails.test.hibernate.HibernateSpec
import org.grails.datastore.mapping.config.Settings

class HibernateOverrideSpec extends HibernateSpec {
    @Override
    Map getConfiguration() {
        [(Settings.SETTING_FAIL_ON_ERROR): true]
    }

    void "Test setting"() {
        expect:
        hibernateDatastore.failOnError == true
    }
}


