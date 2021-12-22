package example

import grails.gorm.MultiTenant

// Issue: https://github.com/grails/gorm-hibernate5/issues/450
// Pr: https://github.com/grails/gorm-hibernate5/pull/451
class MultitenantBook implements MultiTenant<MultitenantBook>, Serializable {
    String id
    String tenantId
    String title

    static mapping  = {
        id composite: ['id', 'tenantId']
    }
}