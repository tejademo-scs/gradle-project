/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.baseline.errorprone;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.util.ASTHelpers;
import com.google.errorprone.util.MoreAnnotations;
import com.palantir.baseline.errorprone.safety.Safety;
import com.palantir.baseline.errorprone.safety.SafetyAnalysis;
import com.palantir.baseline.errorprone.safety.SafetyAnnotations;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.RecordComponent;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.util.Name;
import javax.lang.model.element.Modifier;
import org.checkerframework.errorprone.javacutil.TreePathUtil;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = BugPattern.LinkType.CUSTOM,
        // This will be promoted after an initial rollout period
        severity = BugPattern.SeverityLevel.SUGGESTION,
        summary = "Safe logging annotations should be propagated to encapsulating elements to allow static analysis "
                + "tooling to work with as much information as possible. This check can be auto-fixed using "
                + "`./gradlew classes testClasses -PerrorProneApply=SafeLoggingPropagation`")
public final class SafeLoggingPropagation extends BugChecker
        implements BugChecker.ClassTreeMatcher, BugChecker.MethodTreeMatcher {
    private static final Matcher<Tree> SAFETY_ANNOTATION_MATCHER = Matchers.anyOf(
            Matchers.isSameType(SafetyAnnotations.SAFE),
            Matchers.isSameType(SafetyAnnotations.UNSAFE),
            Matchers.isSameType(SafetyAnnotations.DO_NOT_LOG));

    private static final Matcher<MethodTree> TO_STRING = Matchers.allOf(
            Matchers.methodIsNamed("toString"),
            Matchers.methodHasNoParameters(),
            Matchers.not(Matchers.isStatic()),
            Matchers.methodReturns(Matchers.isSameType(String.class)));
    private static final Matcher<MethodTree> METHOD_RETURNS_VOID = Matchers.methodReturns(Matchers.isVoidType());

    private static final com.google.errorprone.suppliers.Supplier<Name> TO_STRING_NAME =
            VisitorState.memoize(state -> state.getName("toString"));

    private static final com.google.errorprone.suppliers.Supplier<Name> IMMUTABLES_STYLE =
            VisitorState.memoize(state -> state.getName("org.immutables.value.Value.Style"));

    private static final com.google.errorprone.suppliers.Supplier<Name> JACKSON_ANNOTATION =
            VisitorState.memoize(state -> state.getName("com.fasterxml.jackson.annotation.JacksonAnnotation"));

    @Override
    public Description matchClass(ClassTree classTree, VisitorState state) {
        ClassSymbol classSymbol = ASTHelpers.getSymbol(classTree);
        if (classSymbol == null || classSymbol.isAnonymous()) {
            return Description.NO_MATCH;
        }
        TypeSymbol tsym = classSymbol.type.tsym;
        tsym.getModifiers();
        if (classSymbol.isRecord()) {
            return matchRecord(classTree, classSymbol, state);
        } else {
            return matchClassOrInterface(classTree, classSymbol, state);
        }
    }

    /** Matches any jackson annotation based on the meta-annotation {@code @JacksonAnnotation}. */
    private static boolean hasJacksonAnnotation(Symbol typeSymbol, VisitorState state) {
        return hasJacksonAnnotation(typeSymbol, state, 1)
                && !ASTHelpers.hasAnnotation(typeSymbol, "com.fasterxml.jackson.annotation.JsonIgnore", state);
    }

    private static boolean hasJacksonAnnotation(Symbol typeSymbol, VisitorState state, int nestedDepth) {
        if (typeSymbol != null) {
            Name jacksonAnnotationName = JACKSON_ANNOTATION.get(state);
            for (Attribute.Compound metadata : typeSymbol.getRawAttributes()) {
                if (jacksonAnnotationName.equals(metadata.type.tsym.getQualifiedName())) {
                    return true;
                }
                if (nestedDepth > 0 && hasJacksonAnnotation(metadata.type.tsym, state, nestedDepth - 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean immutablesDefaultAsDefault(TypeSymbol typeSymbol, VisitorState state) {
        return immutablesDefaultAsDefault(typeSymbol, state, 1);
    }

    private static boolean immutablesDefaultAsDefault(TypeSymbol typeSymbol, VisitorState state, int nestedDepth) {
        Name styleName = IMMUTABLES_STYLE.get(state);
        for (Attribute.Compound metadata : typeSymbol.getRawAttributes()) {
            if (styleName.equals(metadata.type.tsym.getQualifiedName())) {
                return immutablesDefaultAsDefault(metadata);
            }
            if (nestedDepth > 0 && immutablesDefaultAsDefault(metadata.type.tsym, state, nestedDepth - 1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean immutablesDefaultAsDefault(Attribute.Compound styleAnnotation) {
        return MoreAnnotations.getValue(styleAnnotation, "defaultAsDefault")
                .map(attr -> (boolean) attr.getValue())
                .orElse(false);
    }

    private Description matchRecord(ClassTree classTree, ClassSymbol classSymbol, VisitorState state) {
        Safety existingClassSafety = SafetyAnnotations.getSafety(classTree, state);
        Safety safety = SafetyAnnotations.getTypeSafetyFromAncestors(classTree, state);
        safety = safety.leastUpperBound(SafetyAnnotations.getTypeSafetyFromKnownSubtypes(classTree, state));
        for (RecordComponent recordComponent : classSymbol.getRecordComponents()) {
            Safety symbolSafety = SafetyAnnotations.getSafety(recordComponent, state);
            Safety typeSafety = SafetyAnnotations.getSafety(recordComponent.type, state);
            Safety typeSymSafety = SafetyAnnotations.getSafety(recordComponent.type.tsym, state);
            Safety recordComponentSafety = Safety.mergeAssumingUnknownIsSame(symbolSafety, typeSafety, typeSymSafety);
            safety = safety.leastUpperBound(recordComponentSafety);
        }
        return handleSafety(classTree, classTree.getModifiers(), state, existingClassSafety, safety);
    }

    private Description matchClassOrInterface(ClassTree classTree, ClassSymbol classSymbol, VisitorState state) {
        if (ASTHelpers.hasAnnotation(classSymbol, "org.immutables.value.Value.Immutable", state)) {
            return matchImmutables(classTree, classSymbol, state);
        }
        return matchArbitraryObject(classTree, classSymbol, state);
    }

    private static boolean isImmutablesField(
            ClassSymbol enclosingClass, MethodSymbol methodSymbol, VisitorState state) {
        return methodSymbol.getModifiers().contains(Modifier.ABSTRACT)
                || ASTHelpers.hasAnnotation(methodSymbol, "org.immutables.value.Value.Default", state)
                || ASTHelpers.hasAnnotation(methodSymbol, "org.immutables.value.Value.Derived", state)
                || ASTHelpers.hasAnnotation(methodSymbol, "org.immutables.value.Value.Lazy", state)
                || immutablesDefaultAsDefault(enclosingClass, state)
                || hasJacksonAnnotation(methodSymbol, state);
    }

    private static boolean isToString(MethodSymbol methodSymbol, VisitorState state) {
        return !methodSymbol.isConstructor()
                && !methodSymbol.isStaticOrInstanceInit()
                && state.getTypes().isSameType(methodSymbol.getReturnType(), state.getSymtab().stringType)
                && methodSymbol.name.contentEquals("toString")
                && methodSymbol.getParameters().isEmpty();
    }

    private static boolean isGetterMethod(ClassSymbol enclosingClass, MethodSymbol methodSymbol, VisitorState state) {
        return !methodSymbol.isConstructor()
                && !methodSymbol.isStaticOrInstanceInit()
                && !state.getTypes().isSameType(methodSymbol.getReturnType(), state.getSymtab().voidType)
                && methodSymbol.getParameters().isEmpty()
                && (isImmutablesField(enclosingClass, methodSymbol, state) || isToString(methodSymbol, state));
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private static Safety scanSymbolMethods(ClassSymbol begin, VisitorState state, boolean usesJackson) {
        Safety safety = Safety.UNKNOWN;
        for (Symbol enclosed : ASTHelpers.getEnclosedElements(begin)) {
            if (enclosed instanceof MethodSymbol) {
                MethodSymbol methodSymbol = (MethodSymbol) enclosed;
                if (isGetterMethod(begin, methodSymbol, state)) {
                    boolean redacted =
                            ASTHelpers.hasAnnotation(methodSymbol, "org.immutables.value.Value.Redacted", state);
                    if (redacted && !usesJackson && !hasJacksonAnnotation(methodSymbol, state)) {
                        // Redacted fields can be ignored so long as the object is not json-serializable, in which
                        // case logging may occur using jackson rather than toString.
                        continue;
                    }
                    Safety getterSafety =
                            Safety.mergeAssumingUnknownIsSame(safety, SafetyAnnotations.getSafety(methodSymbol, state));
                    getterSafety = Safety.mergeAssumingUnknownIsSame(
                            getterSafety, SafetyAnnotations.getSafety(methodSymbol.getReturnType(), state));
                    getterSafety = Safety.mergeAssumingUnknownIsSame(
                            getterSafety, SafetyAnnotations.getSafety(methodSymbol.getReturnType().tsym, state));
                    // The redaction check allows us to add @DoNotLog to redacted fields in the same sweep as
                    // adding class-level safety annotations. Otherwise, we would have to run the automatic
                    // fixes twice.
                    if (redacted && (getterSafety == Safety.UNKNOWN || getterSafety == Safety.SAFE)) {
                        // unsafe data may be redacted, however we assume redaction means do-not-log by default
                        getterSafety = Safety.DO_NOT_LOG;
                    }
                    safety = safety.leastUpperBound(getterSafety);
                }
            }
        }
        Type superClassType = begin.getSuperclass();
        if (superClassType != null && superClassType.tsym instanceof ClassSymbol) {
            ClassSymbol superClassSym = (ClassSymbol) superClassType.tsym;
            Safety superClassMethodSafety = scanSymbolMethods(superClassSym, state, usesJackson);
            safety = Safety.mergeAssumingUnknownIsSame(safety, superClassMethodSafety);
        }
        for (Type superIface : begin.getInterfaces()) {
            if (superIface.tsym instanceof ClassSymbol) {
                ClassSymbol superIfaceClassSymbol = (ClassSymbol) superIface.tsym;
                Safety superClassMethodSafety = scanSymbolMethods(superIfaceClassSymbol, state, usesJackson);
                safety = Safety.mergeAssumingUnknownIsSame(safety, superClassMethodSafety);
            }
        }
        return safety;
    }

    private Description matchImmutables(ClassTree classTree, ClassSymbol classSymbol, VisitorState state) {
        Safety existingClassSafety = SafetyAnnotations.getAnnotatedSafety(classTree, state);
        Safety safety = SafetyAnnotations.getTypeSafetyFromAncestors(classTree, state);
        safety = safety.leastUpperBound(SafetyAnnotations.getTypeSafetyFromKnownSubtypes(classTree, state));
        boolean isJson = hasJacksonAnnotation(classSymbol, state);
        ClassSymbol symbol = ASTHelpers.getSymbol(classTree);
        Safety scanned = scanSymbolMethods(symbol, state, isJson);
        safety = safety.leastUpperBound(scanned);
        return handleSafety(classTree, classTree.getModifiers(), state, existingClassSafety, safety);
    }

    private Safety getToStringSafety(ClassSymbol classSymbol, VisitorState state) {
        MethodSymbol toStringSymbol = ASTHelpers.resolveExistingMethod(
                state, classSymbol, TO_STRING_NAME.get(state), ImmutableList.of(), ImmutableList.of());
        return SafetyAnnotations.getSafety(toStringSymbol, state);
    }

    private Description matchArbitraryObject(ClassTree classTree, ClassSymbol classSymbol, VisitorState state) {
        Safety toStringSafety = getToStringSafety(classSymbol, state);
        Safety subtypeSafety = SafetyAnnotations.getTypeSafetyFromKnownSubtypes(classTree, state);
        Safety ancestorSafety = SafetyAnnotations.getTypeSafetyFromAncestors(classTree, state);
        Safety existingClassSafety = SafetyAnnotations.getSafety(classTree, state);
        return handleSafety(
                classTree,
                classTree.getModifiers(),
                state,
                existingClassSafety,
                Safety.mergeAssumingUnknownIsSame(toStringSafety, subtypeSafety, ancestorSafety));
    }

    private Description handleSafety(
            Tree tree, ModifiersTree treeModifiers, VisitorState state, Safety existingSafety, Safety computedSafety) {
        if (existingSafety != Safety.UNKNOWN && existingSafety.allowsValueWith(computedSafety)) {
            // Do not suggest promotion, this check is not exhaustive.
            return Description.NO_MATCH;
        }
        switch (computedSafety) {
            case UNKNOWN:
                // Nothing to do
                return Description.NO_MATCH;
            case SAFE:
                // Do not suggest promotion to safe, this check is not exhaustive.
                return Description.NO_MATCH;
            case DO_NOT_LOG:
                return annotate(tree, treeModifiers, state, SafetyAnnotations.DO_NOT_LOG);
            case UNSAFE:
                return annotate(tree, treeModifiers, state, SafetyAnnotations.UNSAFE);
        }
        return Description.NO_MATCH;
    }

    private Description annotate(Tree tree, ModifiersTree treeModifiers, VisitorState state, String annotationName) {
        // Don't cause churn in test-code.
        if (TestCheckUtils.isTestCode(state)) {
            return Description.NO_MATCH;
        }
        SuggestedFix.Builder fix = SuggestedFix.builder();
        String qualifiedAnnotation = SuggestedFixes.qualifyType(state, fix, annotationName);
        for (AnnotationTree annotationTree : treeModifiers.getAnnotations()) {
            Tree annotationType = annotationTree.getAnnotationType();
            if (SAFETY_ANNOTATION_MATCHER.matches(annotationType, state)) {
                fix.replace(annotationTree, "");
            }
        }
        fix.prefixWith(tree, String.format("@%s ", qualifiedAnnotation));
        return buildDescription(tree).addFix(fix.build()).build();
    }

    @Override
    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    public Description matchMethod(MethodTree method, VisitorState state) {
        if (METHOD_RETURNS_VOID.matches(method, state) || method.getReturnType() == null) {
            return Description.NO_MATCH;
        }
        MethodSymbol methodSymbol = ASTHelpers.getSymbol(method);
        // Removing this check may be helpful once we begin to use the 'var' keyword.
        if (methodSymbol.owner.isAnonymous()) {
            return Description.NO_MATCH;
        }
        Safety methodDeclaredSafety = Safety.mergeAssumingUnknownIsSame(
                SafetyAnnotations.getSafety(methodSymbol, state),
                SafetyAnnotations.getSafety(method.getReturnType(), state));
        // Redacted is do-not-log by default, but may be unsafe.
        if (methodDeclaredSafety != Safety.DO_NOT_LOG
                && methodDeclaredSafety != Safety.UNSAFE
                && ASTHelpers.hasAnnotation(methodSymbol, "org.immutables.value.Value.Redacted", state)) {
            return handleSafety(method, method.getModifiers(), state, methodDeclaredSafety, Safety.DO_NOT_LOG);
        }
        if (methodDeclaredSafety != Safety.UNKNOWN) {
            // No need to verify, that's handled by 'IllegalSafeLoggingArgument'
            return Description.NO_MATCH;
        }
        if ((methodSymbol.flags() & Flags.ABSTRACT) != 0) {
            return Description.NO_MATCH;
        }
        // Don't cause churn in test-code. This is checked prior to the more expensive safety analysis
        if (TestCheckUtils.isTestCode(state)) {
            return Description.NO_MATCH;
        }
        Safety combinedReturnSafety = method.accept(new ReturnStatementSafetyScanner(method), state);
        if (combinedReturnSafety == null) {
            return Description.NO_MATCH;
        }
        return handleSafety(method, method.getModifiers(), state, methodDeclaredSafety, combinedReturnSafety);
    }

    private static final class ReturnStatementSafetyScanner extends TreeScanner<Safety, VisitorState> {

        private final MethodTree target;

        ReturnStatementSafetyScanner(MethodTree target) {
            this.target = target;
        }

        @Override
        public Safety visitReturn(ReturnTree node, VisitorState visitorState) {
            ExpressionTree expression = node.getExpression();
            if (expression == null) {
                return null;
            }
            // Validate that the discovered ReturnTree is from the same scope as the 'target' method.
            TreePath path = TreePath.getPath(visitorState.getPath().getCompilationUnit(), expression);
            if (target.equals(TreePathUtil.enclosingMethodOrLambda(path))) {
                return SafetyAnalysis.of(visitorState.withPath(path));
            } else {
                // Unclear what's happening in this case, so we definitely don't want to claim SAFE
                return Safety.UNKNOWN;
            }
        }

        // Don't search beyond the scope of the method
        @Override
        public Safety visitClass(ClassTree _node, VisitorState _obj) {
            return null;
        }

        @Override
        public Safety visitNewClass(NewClassTree node, VisitorState _state) {
            return null;
        }

        @Override
        public Safety visitLambdaExpression(LambdaExpressionTree node, VisitorState _state) {
            return null;
        }

        @Override
        public Safety reduce(Safety lhs, Safety rhs) {
            if (lhs == null) {
                return rhs;
            }
            if (rhs == null) {
                return lhs;
            }
            return lhs.leastUpperBound(rhs);
        }
    }
}
