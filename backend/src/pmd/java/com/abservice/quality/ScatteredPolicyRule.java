package com.abservice.quality;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTExecutableDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTFieldDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTMethodReference;
import net.sourceforge.pmd.lang.java.ast.ASTVariableId;
import net.sourceforge.pmd.lang.java.ast.ModifierOwner.Visibility;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;
import net.sourceforge.pmd.lang.java.types.TypeTestUtil;

/**
 * Finds private, single-use Policy fragments consumed together in one
 * declaration.
 */
public final class ScatteredPolicyRule extends AbstractJavaRule {
    private static final String POLICY = "com.abservice.domain.model.policy.Policy";

    @Override
    public Object visit(ASTCompilationUnit unit, Object data) {
        Map<Node, List<Node>> fragments = new LinkedHashMap<>();
        for (ASTVariableId field : unit.descendants(ASTVariableId.class).crossFindBoundaries().toList()) {
            if (field.isField() && field.hasVisibility(Visibility.V_PRIVATE) && TypeTestUtil.isA(POLICY, field)) {
                register(fragments, field, field.getLocalUsages());
            }
        }
        var calls = unit.descendants(ASTMethodCall.class).crossFindBoundaries().toList();
        var references = unit.descendants(ASTMethodReference.class).crossFindBoundaries().toList();
        for (ASTMethodDeclaration method : unit.descendants(ASTMethodDeclaration.class).crossFindBoundaries()
                .toList()) {
            if (!method.hasVisibility(Visibility.V_PRIVATE) || !TypeTestUtil.isA(POLICY, method.getResultTypeNode())) {
                continue;
            }
            List<Node> uses = new ArrayList<>();
            calls.stream().filter(call -> method.getSymbol().equals(call.getMethodType().getSymbol()))
                    .forEach(uses::add);
            references.stream().filter(ref -> ref.getReferencedMethod() != null)
                    .filter(ref -> method.getSymbol().equals(ref.getReferencedMethod().getSymbol()))
                    .forEach(uses::add);
            register(fragments, method, uses);
        }
        fragments.forEach((consumer, definitions) -> {
            if (definitions.size() > 1) {
                asCtx(data).addViolation(consumer);
            }
        });
        return data;
    }

    private static void register(Map<Node, List<Node>> fragments, Node definition, List<? extends Node> uses) {
        if (uses.size() != 1) {
            return;
        }
        Node consumer = uses.getFirst().ancestors()
                .filter(node -> node instanceof ASTExecutableDeclaration || node instanceof ASTFieldDeclaration)
                .first();
        if (consumer != null) {
            fragments.computeIfAbsent(consumer, unused -> new ArrayList<>()).add(definition);
        }
    }
}
