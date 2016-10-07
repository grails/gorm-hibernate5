package grails.gorm.tests

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.gorm.annotation.Entity
import grails.validation.ConstrainedProperty
import org.apache.commons.lang.builder.HashCodeBuilder
import org.grails.core.DefaultGrailsDomainClass
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.datastore.gorm.validation.constraints.UniqueConstraintFactory
import org.grails.datastore.mapping.model.MappingContext
import org.grails.orm.hibernate.validation.PersistentConstraintFactory
import org.grails.orm.hibernate.validation.UniqueConstraint
import org.grails.validation.GrailsDomainClassValidator
import org.springframework.context.support.GenericApplicationContext
import spock.lang.Specification

/**
 * Created by graemerocher on 07/10/2016.
 */
class UniqueConstraintWithCompositeIdSpec extends GormDatastoreSpec {

    void "test unique constraint with composite id"() {
        expect:
        new UniqueWithCompositeId(firstName: 'John', lastName: 'Doe').save(flush: true)
    }

    void "test unique constraint with composite id with Grails validator"() {


        given:
        GenericApplicationContext applicationContext = new GenericApplicationContext()
        ConstrainedProperty.registerNewConstraint("unique", new PersistentConstraintFactory(applicationContext, UniqueConstraint))
        GrailsApplication grailsApplication = new DefaultGrailsApplication(UniqueWithCompositeId)
        applicationContext.beanFactory.registerSingleton("sessionFactory", session.datastore.sessionFactory)
        applicationContext.beanFactory.registerSingleton("mappingContext", session.datastore.mappingContext)
        applicationContext.beanFactory.registerSingleton("grailsApplication", grailsApplication)
        grailsApplication.mainContext = applicationContext
        grailsApplication.initialise()
        applicationContext.refresh()
        def mappingContext = session.datastore.mappingContext
        mappingContext.addEntityValidator(
                mappingContext.getPersistentEntity(UniqueWithCompositeId.name),
                new GrailsDomainClassValidator(grailsApplication: grailsApplication, domainClass: grailsApplication.getArtefact(DomainClassArtefactHandler.TYPE, UniqueWithCompositeId.name))
        )
        expect:
        new UniqueWithCompositeId(firstName: 'John', lastName: 'Doe').save(flush: true)

        cleanup:
        ConstrainedProperty.removeConstraint("unique")
    }

    @Override
    List getDomainClasses() {
        [UniqueWithCompositeId]
    }
}

@Entity
class UniqueWithCompositeId implements Serializable {

    String firstName, lastName

    static mapping = {
        id composite: ['firstName', 'lastName']
    }

    static constraints = {
        firstName(unique: ['lastName'])
    }

    boolean equals(other) {
        if (!(other instanceof UniqueWithCompositeId)) {
            return false
        }

        other.firstName == firstName && other.lastName == lastName
    }

    int hashCode() {
        def builder = new HashCodeBuilder()
        builder.append firstName
        builder.append lastName
        builder.toHashCode()
    }
}

