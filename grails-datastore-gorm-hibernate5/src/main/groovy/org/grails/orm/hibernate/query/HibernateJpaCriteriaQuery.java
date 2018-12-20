package org.grails.orm.hibernate.query;

import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.QueryException;
import org.grails.datastore.mapping.query.event.PostQueryEvent;
import org.grails.datastore.mapping.query.event.PreQueryEvent;
import org.grails.orm.hibernate.HibernateSession;
import org.hibernate.Session;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.RowCountProjection;
import org.springframework.context.ApplicationEventPublisher;

import javax.persistence.criteria.*;
import java.util.*;

/**
 * A Query implementation for JPA criteria queries.
 *
 * @author graemerocher
 */
public class HibernateJpaCriteriaQuery extends Query {
    private static final Map<Class<? extends Criterion>, CriterionToPredicateAdapter> CRITERION_ADAPTERS = new HashMap<>(30);
    private static final Map<Class<? extends Projection>, ProjectionToSelectionAdapter> PROJECTION_ADAPTERS = new HashMap<>(20);
    private final CriteriaBuilder criteriaBuilder;
    private final CriteriaQuery criteriaQuery;
    private final Session hibernateSession;
    private final Root queryRoot;

    static {

        PROJECTION_ADAPTERS.put(CountProjection.class, new ProjectionToSelectionAdapter<CountProjection>() {
            @Override
            public Selection<?> toSelection(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, CountProjection projection) {
                return criteriaBuilder.count(path);
            }
        });

        PROJECTION_ADAPTERS.put(AvgProjection.class, new ProjectionToSelectionAdapter<AvgProjection>() {
            @Override
            public Selection<?> toSelection(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, AvgProjection projection) {
                return criteriaBuilder.avg(path.get(projection.getPropertyName()));
            }
        });

        PROJECTION_ADAPTERS.put(SumProjection.class, new ProjectionToSelectionAdapter<SumProjection>() {
            @Override
            public Selection<?> toSelection(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, SumProjection projection) {
                return criteriaBuilder.sum(path.get(projection.getPropertyName()));
            }
        });

        PROJECTION_ADAPTERS.put(MaxProjection.class, new ProjectionToSelectionAdapter<MaxProjection>() {
            @Override
            public Selection<?> toSelection(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, MaxProjection projection) {
                return criteriaBuilder.max(path.get(projection.getPropertyName()));
            }
        });

        PROJECTION_ADAPTERS.put(MinProjection.class, new ProjectionToSelectionAdapter<MinProjection>() {
            @Override
            public Selection<?> toSelection(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, MinProjection projection) {
                return criteriaBuilder.min(path.get(projection.getPropertyName()));
            }
        });

        PROJECTION_ADAPTERS.put(IdProjection.class, new ProjectionToSelectionAdapter<IdProjection>() {
            @Override
            public Selection<?> toSelection(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, IdProjection projection) {
                return path.get(entity.getIdentity().getName());
            }
        });

        PROJECTION_ADAPTERS.put(DistinctPropertyProjection.class, new ProjectionToSelectionAdapter<DistinctPropertyProjection>() {
            @Override
            public Selection<?> toSelection(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, DistinctPropertyProjection projection) {
                return path.get(projection.getPropertyName());
            }

            @Override
            public boolean isDistinct() {
                return true;
            }
        });

        PROJECTION_ADAPTERS.put(PropertyProjection.class, new ProjectionToSelectionAdapter<PropertyProjection>() {
            @Override
            public Selection<?> toSelection(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, PropertyProjection projection) {
                return path.get(projection.getPropertyName());
            }
        });

        PROJECTION_ADAPTERS.put(CountDistinctProjection.class, new ProjectionToSelectionAdapter<CountDistinctProjection>() {
            @Override
            public Selection<?> toSelection(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, CountDistinctProjection projection) {
                return criteriaBuilder.count(path.get(projection.getPropertyName()));
            }

            @Override
            public boolean isDistinct() {
                return true;
            }
        });

        // TODO: RLIKE
        CRITERION_ADAPTERS.put(IdEquals.class, new CriterionToPredicateAdapter<IdEquals>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, IdEquals criterion) {
                return criteriaBuilder.equal(path.get(entity.getIdentity().getName()), criterion.getValue());
            }
        });
        CRITERION_ADAPTERS.put(Equals.class, new CriterionToPredicateAdapter<Equals>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, Equals criterion) {
                return criteriaBuilder.equal(path.get(criterion.getProperty()), criterion.getValue());
            }
        });
        CRITERION_ADAPTERS.put(NotEquals.class, new CriterionToPredicateAdapter<NotEquals>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, NotEquals criterion) {
                return criteriaBuilder.notEqual(path.get(criterion.getProperty()), criterion.getValue());
            }
        });
        CRITERION_ADAPTERS.put(GreaterThan.class, new CriterionToPredicateAdapter<GreaterThan>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, GreaterThan criterion) {
                final Object value = criterion.getValue();
                if (value instanceof Comparable) {
                    return criteriaBuilder.greaterThan(path.get(criterion.getProperty()), (Comparable) value);
                } else {
                    throw new QueryException("Values passed to comparison queries must be comparable and implement the java.lang.Comparable interface");
                }
            }
        });
        CRITERION_ADAPTERS.put(GreaterThanEquals.class, new CriterionToPredicateAdapter<GreaterThanEquals>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, GreaterThanEquals criterion) {
                final Object value = criterion.getValue();
                if (value instanceof Comparable) {
                    return criteriaBuilder.greaterThanOrEqualTo(path.get(criterion.getProperty()), (Comparable) value);
                } else {
                    throw new QueryException("Values passed to comparison queries must be comparable and implement the java.lang.Comparable interface");
                }
            }
        });
        CRITERION_ADAPTERS.put(LessThan.class, new CriterionToPredicateAdapter<LessThan>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, LessThan criterion) {
                final Object value = criterion.getValue();
                if (value instanceof Comparable) {
                    return criteriaBuilder.lessThan(path.get(criterion.getProperty()), (Comparable) value);
                } else {
                    throw new QueryException("Values passed to comparison queries must be comparable and implement the java.lang.Comparable interface");
                }
            }
        });
        CRITERION_ADAPTERS.put(LessThanEquals.class, new CriterionToPredicateAdapter<LessThanEquals>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, LessThanEquals criterion) {
                final Object value = criterion.getValue();
                if (value instanceof Comparable) {
                    return criteriaBuilder.lessThanOrEqualTo(path.get(criterion.getProperty()), (Comparable) value);
                } else {
                    throw new QueryException("Values passed to comparison queries must be comparable and implement the java.lang.Comparable interface");
                }
            }
        });

        CRITERION_ADAPTERS.put(Like.class, new CriterionToPredicateAdapter<Like>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, Like criterion) {
                final Object value = criterion.getValue();
                Objects.requireNonNull(value, "Value cannot be null");
                return criteriaBuilder.like(path.get(criterion.getProperty()), value.toString());
            }
        });

        CRITERION_ADAPTERS.put(ILike.class, new CriterionToPredicateAdapter<ILike>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, ILike criterion) {
                final Object value = criterion.getValue();
                Objects.requireNonNull(value, "Value cannot be null");
                return criteriaBuilder.like(path.get(criterion.getProperty()), criteriaBuilder.lower(criteriaBuilder.literal(value.toString())));
            }
        });

        CRITERION_ADAPTERS.put(In.class, new CriterionToPredicateAdapter<In>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, In criterion) {
                Collection values = criterion.getValues();
                if (values == null) {
                    values = Collections.emptyList();
                }
                return path.get(criterion.getProperty()).in(values);
            }
        });

        CRITERION_ADAPTERS.put(Between.class, new CriterionToPredicateAdapter<Between>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, Between criterion) {
                final Object from = criterion.getFrom();
                final Object to = criterion.getTo();
                if (!(from instanceof Comparable) || !(to instanceof Comparable)) {
                    throw new QueryException("Values passed to comparison queries must be comparable and implement the java.lang.Comparable interface");
                }
                return criteriaBuilder.between(path.get(criterion.getProperty()), (Comparable) from, (Comparable) to);
            }
        });

        CRITERION_ADAPTERS.put(Conjunction.class, new CriterionToPredicateAdapter<Conjunction>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, Conjunction criterion) {
                final List<Criterion> criteria = criterion.getCriteria();
                List<Predicate> predicateList = new ArrayList<>(criteria.size());
                for (Criterion c : criteria) {
                    final CriterionToPredicateAdapter adapter = CRITERION_ADAPTERS.get(c.getClass());
                    if (adapter != null) {
                        predicateList.add(adapter.toPredicate(entity, criteriaBuilder, path, c));
                    }
                }
                return criteriaBuilder.and(predicateList.toArray(new Predicate[0]));
            }
        });

        CRITERION_ADAPTERS.put(Disjunction.class, new CriterionToPredicateAdapter<Disjunction>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, Disjunction criterion) {
                final List<Criterion> criteria = criterion.getCriteria();
                List<Predicate> predicateList = new ArrayList<>(criteria.size());
                for (Criterion c : criteria) {
                    final CriterionToPredicateAdapter adapter = CRITERION_ADAPTERS.get(c.getClass());
                    if (adapter != null) {
                        predicateList.add(adapter.toPredicate(entity, criteriaBuilder, path, c));
                    }
                }
                return criteriaBuilder.or(predicateList.toArray(new Predicate[0]));
            }
        });

        CRITERION_ADAPTERS.put(Negation.class, new CriterionToPredicateAdapter<Negation>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, Negation criterion) {
                final List<Criterion> criteria = criterion.getCriteria();
                List<Predicate> predicateList = new ArrayList<>(criteria.size());
                for (Criterion c : criteria) {
                    final CriterionToPredicateAdapter adapter = CRITERION_ADAPTERS.get(c.getClass());
                    if (adapter != null) {
                        predicateList.add(adapter.toPredicate(entity, criteriaBuilder, path, c));
                    }
                }
                return criteriaBuilder.not(criteriaBuilder.and(predicateList.toArray(new Predicate[0])));
            }
        });

        // isNull
        CRITERION_ADAPTERS.put(Query.IsNull.class, new CriterionToPredicateAdapter<Query.IsNull>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, IsNull criterion) {
                return criteriaBuilder.isNull(path.get(criterion.getProperty()));
            }
        });

        // isNotNull
        CRITERION_ADAPTERS.put(Query.IsNotNull.class, new CriterionToPredicateAdapter<Query.IsNotNull>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, IsNotNull criterion) {
                return criteriaBuilder.isNotNull(path.get(criterion.getProperty()));
            }
        });

        // isEmpty
        CRITERION_ADAPTERS.put(Query.IsEmpty.class, new CriterionToPredicateAdapter<Query.IsEmpty>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, IsEmpty criterion) {
                return criteriaBuilder.isEmpty(path.get(criterion.getProperty()));
            }
        });

        // isNotEmpty
        CRITERION_ADAPTERS.put(Query.IsNotEmpty.class, new CriterionToPredicateAdapter<Query.IsNotEmpty>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, IsNotEmpty criterion) {
                return criteriaBuilder.isNotEmpty(path.get(criterion.getProperty()));
            }
        });

        CRITERION_ADAPTERS.put(Query.SizeEquals.class, new CriterionToPredicateAdapter<SizeEquals>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, SizeEquals criterion) {
                final Path<Collection<?>> collection = path.get(criterion.getProperty());
                return criteriaBuilder.equal(
                        criteriaBuilder.size(collection),
                        criterion.getValue()
                );
            }
        });

        CRITERION_ADAPTERS.put(Query.SizeGreaterThan.class, new CriterionToPredicateAdapter<SizeGreaterThan>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, SizeGreaterThan criterion) {
                final Path<Collection<?>> collection = path.get(criterion.getProperty());
                final Object v = criterion.getValue();
                if (!(v instanceof Number)) {
                    throw new QueryException("Values passed to size comparison queries must be numbers");
                }
                final Expression<Integer> sizeExpr = criteriaBuilder.size(collection);
                final Number comparable = (Number ) v;
                return criteriaBuilder.greaterThan(
                        sizeExpr,
                        comparable.intValue()
                );
            }
        });

        CRITERION_ADAPTERS.put(Query.SizeGreaterThanEquals.class, new CriterionToPredicateAdapter<SizeGreaterThanEquals>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, SizeGreaterThanEquals criterion) {
                final Path<Collection<?>> collection = path.get(criterion.getProperty());
                final Object v = criterion.getValue();
                if (!(v instanceof Number)) {
                    throw new QueryException("Values passed to size comparison queries must numbers");
                }
                return criteriaBuilder.greaterThanOrEqualTo(
                        criteriaBuilder.size(collection),
                        ((Number)v).intValue()
                );
            }
        });

        CRITERION_ADAPTERS.put(Query.SizeLessThan.class, new CriterionToPredicateAdapter<SizeLessThan>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, SizeLessThan criterion) {
                final Path<Collection<?>> collection = path.get(criterion.getProperty());
                final Object v = criterion.getValue();
                if (!(v instanceof Number)) {
                    throw new QueryException("Values passed to size comparison queries must numbers");
                }

                return criteriaBuilder.lessThan(
                        criteriaBuilder.size(collection),
                        ((Number)v).intValue()
                );
            }
        });

        CRITERION_ADAPTERS.put(Query.SizeLessThanEquals.class, new CriterionToPredicateAdapter<SizeLessThanEquals>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, SizeLessThanEquals criterion) {
                final Path<Collection<?>> collection = path.get(criterion.getProperty());
                final Object v = criterion.getValue();
                if (!(v instanceof Number)) {
                    throw new QueryException("Values passed to size comparison queries must numbers");
                }
                return criteriaBuilder.lessThanOrEqualTo(
                        criteriaBuilder.size(collection),
                        ((Number)v).intValue()
                );
            }
        });


        CRITERION_ADAPTERS.put(Query.EqualsProperty.class, new CriterionToPredicateAdapter<Query.EqualsProperty>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, EqualsProperty criterion) {
                final String property = criterion.getProperty();
                final String otherProperty = criterion.getOtherProperty();
                return criteriaBuilder.equal(path.get(property), path.get(otherProperty));
            }
        });
        CRITERION_ADAPTERS.put(Query.GreaterThanProperty.class, new CriterionToPredicateAdapter<Query.GreaterThanProperty>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, GreaterThanProperty criterion) {
                final String property = criterion.getProperty();
                final String otherProperty = criterion.getOtherProperty();
                return criteriaBuilder.greaterThan(path.get(property), path.get(otherProperty));
            }
        });
        CRITERION_ADAPTERS.put(Query.GreaterThanEqualsProperty.class, new CriterionToPredicateAdapter<Query.GreaterThanEqualsProperty>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, GreaterThanEqualsProperty criterion) {
                final String property = criterion.getProperty();
                final String otherProperty = criterion.getOtherProperty();
                return criteriaBuilder.greaterThanOrEqualTo(path.get(property), path.get(otherProperty));
            }
        });
        CRITERION_ADAPTERS.put(Query.LessThanProperty.class, new CriterionToPredicateAdapter<Query.LessThanProperty>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, LessThanProperty criterion) {
                final String property = criterion.getProperty();
                final String otherProperty = criterion.getOtherProperty();
                return criteriaBuilder.lessThan(path.get(property), path.get(otherProperty));

            }
        });
        CRITERION_ADAPTERS.put(Query.LessThanEqualsProperty.class, new CriterionToPredicateAdapter<Query.LessThanEqualsProperty>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, LessThanEqualsProperty criterion) {
                final String property = criterion.getProperty();
                final String otherProperty = criterion.getOtherProperty();
                return criteriaBuilder.lessThanOrEqualTo(path.get(property), path.get(otherProperty));
            }
        });

        CRITERION_ADAPTERS.put(Query.NotEqualsProperty.class, new CriterionToPredicateAdapter<Query.NotEqualsProperty>() {
            @Override
            public Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, NotEqualsProperty criterion) {
                final String property = criterion.getProperty();
                final String otherProperty = criterion.getOtherProperty();
                return criteriaBuilder.notEqual(path.get(property), path.get(otherProperty));
            }
        });
    }

    public HibernateJpaCriteriaQuery(HibernateSession session, Session hibernateSession, PersistentEntity entity) {
        super(session, entity);
        this.criteriaBuilder = hibernateSession.getCriteriaBuilder();
        this.criteriaQuery = criteriaBuilder.createQuery(entity.getJavaClass());
        this.hibernateSession = hibernateSession;
        this.queryRoot = criteriaQuery.from(entity.getJavaClass());
    }

    @Override
    protected List executeQuery(PersistentEntity entity, Junction criteria) {
        Predicate[] predicates = null;
        List<Predicate> predicateList = new ArrayList<>(5);
        final CriteriaBuilder criteriaBuilder = this.criteriaBuilder;
        final Path path = this.queryRoot;
        for (Criterion criterion : criteria.getCriteria()) {
            final CriterionToPredicateAdapter adapter = CRITERION_ADAPTERS.get(criterion.getClass());
            if (adapter != null) {
                final Predicate predicate = adapter.toPredicate(entity, criteriaBuilder, path, criterion);
                if (predicate != null) {
                    predicateList.add(predicate);
                }
            } else {
                throw new UnsupportedOperationException("Unsupported criterion type: " + criterion);
            }
        }
        if (!predicateList.isEmpty()) {
            predicates = predicateList.toArray(new Predicate[0]);
        }

        if (predicates != null) {
            criteriaQuery.where(predicates);
        }

        if (!orderBy.isEmpty()) {
            for (Order order : orderBy) {
                Expression p = queryRoot.get(order.getProperty());
                if (order.isIgnoreCase()) {
                    p = criteriaBuilder.lower(p);
                }
                if (order.getDirection() == Order.Direction.ASC) {
                    criteriaQuery.orderBy(
                            criteriaBuilder.asc(p)
                    );
                } else {
                    criteriaQuery.orderBy(
                            criteriaBuilder.desc(p)
                    );
                }
            }
        }

        if (!projections.isEmpty()) {
            for (Projection projection : projections.getProjectionList()) {
                final ProjectionToSelectionAdapter adapter = PROJECTION_ADAPTERS.get(projection.getClass());
                if (adapter != null) {
                    final CriteriaQuery cq = criteriaQuery.select(adapter.toSelection(entity, criteriaBuilder, queryRoot, projection));
                    if (adapter.isDistinct()) {
                        cq.distinct(true);
                    }

                } else {
                    throw new UnsupportedOperationException("Unsupported projection type: " + projection);
                }
            }
        }

        final org.hibernate.query.Query hibernateQuery = hibernateSession.createQuery(criteriaQuery);

        Datastore datastore = getSession().getDatastore();
        ApplicationEventPublisher applicationEventPublisher = datastore.getApplicationEventPublisher();
        PreQueryEvent preQueryEvent = new PreQueryEvent(datastore, this);
        applicationEventPublisher.publishEvent(preQueryEvent);

        if (uniqueResult) {
            hibernateQuery.setMaxResults(1);
            List results = hibernateQuery.list();
            applicationEventPublisher.publishEvent(new PostQueryEvent(datastore, this, results));
            return results;
        } else {
            if (queryCache != null) {
                hibernateQuery.setCacheable(queryCache);
            }

            if (max > -1) {
                hibernateQuery.setMaxResults(max);
            }
            if (offset > 0) {
                hibernateQuery.setFirstResult(offset);
            }
            List results = hibernateQuery.list();
            applicationEventPublisher.publishEvent(new PostQueryEvent(datastore, this, results));
            return results;
        }
    }

    private interface CriterionToPredicateAdapter<T extends Criterion> {
        Predicate toPredicate(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, T criterion);
    }

    private interface ProjectionToSelectionAdapter<T extends Projection> {
        Selection<?> toSelection(PersistentEntity entity, CriteriaBuilder criteriaBuilder, Path<?> path, T projection);

        default boolean isDistinct() {
            return false;
        }
    }
}
