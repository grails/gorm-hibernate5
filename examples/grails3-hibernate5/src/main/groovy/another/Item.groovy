package another

import grails.artefact.Artefact
import org.grails.core.artefact.DomainClassArtefactHandler

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

/**
 * Created by graemerocher on 27/01/2017.
 */
@Entity
@Artefact(DomainClassArtefactHandler.TYPE)
class Item {
    @Id
    @GeneratedValue
    Long id

    String name
}
