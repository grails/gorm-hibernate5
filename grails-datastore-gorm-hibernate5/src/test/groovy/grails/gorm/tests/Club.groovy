package grails.gorm.tests

import grails.persistence.Entity

@Entity
class Club {
    String name

    @Override
    String toString() {
        name
    }
}
