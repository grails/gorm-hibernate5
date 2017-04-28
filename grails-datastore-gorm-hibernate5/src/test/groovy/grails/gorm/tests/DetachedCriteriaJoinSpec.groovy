package grails.gorm.tests

import grails.gorm.DetachedCriteria
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.orm.hibernate.GormSpec
import org.grails.orm.hibernate.query.HibernateQuery

import javax.persistence.criteria.JoinType

class DetachedCriteriaJoinSpec  extends GormSpec {
    @Override
    List getDomainClasses() {
        [Team,Club]
    }

    def 'check if inner join is applied correctly'(){
        given: 
            def dc = new DetachedCriteria(Team).build{
                join('club', JoinType.INNER)
                createAlias('club','c')
            }
            HibernateQuery query = session.createQuery(Team)
            
            DynamicFinder.applyDetachedCriteria(query,dc)
            def joinType = query.hibernateCriteria.subcriteriaList.first().joinType
        expect: 
            joinType == org.hibernate.sql.JoinType.INNER_JOIN
    }

    def 'check if left join is applied correctly'(){
        given:
            def dc = new DetachedCriteria(Team).build{
                join('club', JoinType.LEFT)
                createAlias('club','c')
            }
            HibernateQuery query = session.createQuery(Team)

            DynamicFinder.applyDetachedCriteria(query,dc)
            def joinType = query.hibernateCriteria.subcriteriaList.first().joinType
        expect:
            joinType == org.hibernate.sql.JoinType.LEFT_OUTER_JOIN
    }

    def 'check if right join is applied correctly'(){
        given:
            def dc = new DetachedCriteria(Team).build{
                join('club', JoinType.RIGHT)
                createAlias('club','c')
            }
            HibernateQuery query = session.createQuery(Team)

            DynamicFinder.applyDetachedCriteria(query,dc)
            def joinType = query.hibernateCriteria.subcriteriaList.first().joinType
        expect:
            joinType == org.hibernate.sql.JoinType.RIGHT_OUTER_JOIN
    }
}
