package io.github.lambig.toolbox.harness;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.lang.annotation.Annotation;
import java.util.Objects;

/**
 * Rules for direct calls and method references to selected methods.
 *
 * <p>The caller supplies the domain policy and imports both target and caller classes.
 * This bytecode check does not establish business ordering or observe reflective access.
 */
public final class MethodAccessRules {
    private MethodAccessRules() {
    }

    /**
     * Restricts access to selected methods to matching caller classes.
     *
     * <p>Both calls and method references are checked. A class is the owner of the
     * originating code unit; enclosing classes are not automatically authorized.
     * No selected method is an error, even if ArchUnit's global empty-rule default
     * has been disabled. An imported method without any incoming access is allowed.
     *
     * @param targets methods whose incoming accesses must be checked
     * @param allowedCallers permitted owners of originating code units
     * @return an ArchUnit rule to evaluate against the consumer's imported classes
     * @throws NullPointerException if either predicate is null
     */
    public static ArchRule onlyAccessedBy(
            DescribedPredicate<? super JavaMethod> targets,
            DescribedPredicate<? super JavaClass> allowedCallers) {
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(allowedCallers, "allowedCallers");
        return methods().that(targets)
                .should(new ArchCondition<JavaMethod>("only be accessed by classes " + allowedCallers.getDescription()) {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        // Unlike onlyBeCalled(), this includes Foo::method accesses.
                        method.getAccessesToSelf().forEach(access -> {
                            if (!allowedCallers.test(access.getOriginOwner())) {
                                events.add(SimpleConditionEvent.violated(access,
                                        access.getDescription() + "; expected caller class "
                                                + allowedCallers.getDescription()));
                            }
                        });
                    }
                })
                .allowEmptyShould(false);
    }

    /**
     * Selects methods and caller classes using annotations owned by the consumer.
     *
     * <p>Annotations must be retained in class files. Matching uses ArchUnit's
     * direct {@code annotatedWith} predicate; meta-annotations and annotations on
     * enclosing classes do not grant permission. See {@link #onlyAccessedBy} for
     * import requirements and the empty-selection contract.
     *
     * @param targetAnnotation annotation marking restricted methods
     * @param callerAnnotation annotation marking permitted caller classes
     * @return the configured access rule
     * @throws NullPointerException if either annotation type is null
     */
    public static ArchRule annotatedMethodsOnlyAccessedByAnnotatedClasses(
            Class<? extends Annotation> targetAnnotation,
            Class<? extends Annotation> callerAnnotation) {
        Objects.requireNonNull(targetAnnotation, "targetAnnotation");
        Objects.requireNonNull(callerAnnotation, "callerAnnotation");
        return onlyAccessedBy(annotatedWith(targetAnnotation), annotatedWith(callerAnnotation));
    }
}
