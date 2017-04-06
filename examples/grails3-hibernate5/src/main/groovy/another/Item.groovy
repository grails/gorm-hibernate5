package another

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

/**
 * Created by graemerocher on 27/01/2017.
 */
@Entity
class Item {
    @Id
    @GeneratedValue
    Long id

    String name
}
