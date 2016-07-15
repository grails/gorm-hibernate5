package grails.test.mixin.gorm

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Annotation for registering domain classes to the {@link TestRuntime} of the current test class.
 *
 * @author Lari Hotari
 * @since 2.4.1
 *
 * @deprecated Use {@link grails.test.hibernate.HibernateSpec} instead
 */
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.PACKAGE, ElementType.TYPE])
public @interface Domain {
    /**
     * @return Domain classes to register in the runtime
     */
    Class<?>[] value()
}