package example

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import spock.lang.Specification

@Integration
@Rollback
class TestServiceSpec extends Specification {

    TestService testService

    void "test data-service is loaded correctly"() {
        when:
        testService.testDataService()

        then:
        noExceptionThrown()
    }
}
