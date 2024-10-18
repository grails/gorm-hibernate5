package functional.tests

import grails.testing.mixin.integration.Integration
import spock.lang.Ignore
import spock.lang.Specification

/**
 * Created by graemerocher on 04/05/2017.
 */
@Integration(applicationClass = Application)
class CascadeValidationSpec extends Specification {

    @Ignore("org.codehaus.groovy.runtime.typehandling.GroovyCastException: Cannot cast object 'functional.tests.Employee : (unsaved)' with class 'functional.tests.Employee' to class 'functional.tests.Person'")
    void "validation cascades correctly"() {
        given: "an invalid business"
        Business b = new Business(name: null)

        and: "a valid employee that belongs to the business"
        Person p = new Employee(business: b)
        b.addToPeople(p)

        when:
        b.save()

        then:
        b.errors.hasFieldErrors('name')
        b.hasErrors()
    }
}
