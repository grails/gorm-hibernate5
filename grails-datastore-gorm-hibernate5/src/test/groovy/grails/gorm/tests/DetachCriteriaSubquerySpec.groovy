package grails.gorm.tests

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity

@SuppressWarnings("GrMethodMayBeStatic")
class DetachCriteriaSubquerySpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        return [User, Group, GroupAssignment]
    }

    void "test detached associated criteria in subquery"() {

        setup:
        User supVisor = createUser('supervisor@company.com')
        User user1 = createUser('user1@company.com')
        User user2 = createUser('user2@company.com')

        Group group1 = createGroup('Group 1', supVisor)
        Group group2 = createGroup('Group 2', supVisor)

        assignGroup(user1, group1)
        assignGroup(user1, group2)

        when:
        String supervisorEmail = 'supervisor@company.com'
        DetachedCriteria<User> criteria = User.where {
            def u = User
            exists(
                    GroupAssignment.where {
                        def ga0 = GroupAssignment
                        user.id == u.id && group.supervisor.email == supervisorEmail
                    }.id()
            )
        }
        List<User> result = criteria.list()

        then:
        noExceptionThrown()
        result.size() == 1
    }

    private User createUser(String email) {
        User user = new User()
        user.email = email
        user.save(flush: true)
    }

    private Group createGroup(String name, User supervisor) {
        Group group = new Group()
        group.name = name
        group.supervisor = supervisor
        group.save(flush: true)
    }

    private void assignGroup(User user, Group group) {
        GroupAssignment groupAssignment = new GroupAssignment()
        groupAssignment.user = user
        groupAssignment.group = group
        groupAssignment.save(flush: true)
    }

}


@Entity
class User implements HibernateEntity<User> {
    String email
    static mapping = {
        table 'T_USER'
    }
}

@Entity
class Group implements HibernateEntity<Group> {
    String name
    User supervisor
    static mapping = {
        table 'T_GROUP'
    }
}

@Entity
class GroupAssignment implements HibernateEntity<GroupAssignment> {
    User user
    Group group
    static mapping = {
        table 'T_GROUP_ASSIGNMENT'
    }
}
