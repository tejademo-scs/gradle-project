/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.LinkType;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.fixes.SuggestedFixes;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.tools.javac.code.Kinds.Kind;
import com.sun.tools.javac.code.Symbol;

@AutoService(BugChecker.class)
@BugPattern(
        link = "https://github.com/palantir/gradle-baseline#baseline-error-prone-checks",
        linkType = LinkType.CUSTOM,
        severity = SeverityLevel.WARNING,
        summary = "Types should not be qualified if they are also imported")
public final class UnnecessarilyQualified extends BugChecker implements BugChecker.MemberSelectTreeMatcher {

    @Override
    public Description matchMemberSelect(MemberSelectTree tree, VisitorState state) {
        // Only consider member selects in which the left hand side is a package and right is a class.
        // This means we won't do anything clever for imported nested classes or method imports.
        if (!isQualifiedClassReference(tree)) {
            return Description.NO_MATCH;
        }
        String nameString = state.getSourceForNode(tree);
        if (nameString == null) {
            return Description.NO_MATCH;
        }
        CompilationUnitTree compilationUnitTree =
                ASTHelpers.findEnclosingNode(state.getPath(), CompilationUnitTree.class);
        if (compilationUnitTree == null || ASTHelpers.findEnclosingNode(state.getPath(), ImportTree.class) != null) {
            return Description.NO_MATCH;
        }

        for (ImportTree importTree : compilationUnitTree.getImports()) {
            if (!importTree.isStatic()
                    && nameString.equals(state.getSourceForNode(importTree.getQualifiedIdentifier()))) {
                SuggestedFix.Builder fix = SuggestedFix.builder();
                String updated = nameString.replace(nameString, SuggestedFixes.qualifyType(state, fix, nameString));
                return buildDescription(tree)
                        .addFix(fix.replace(tree, updated).build())
                        .build();
            }
        }
        return Description.NO_MATCH;
    }

    private static boolean isQualifiedClassReference(MemberSelectTree tree) {
        Symbol selectedSymbol = ASTHelpers.getSymbol(tree);
        if (selectedSymbol == null || selectedSymbol.kind != Kind.TYP) {
            return false;
        }
        Symbol selectedSymbolQualifier = ASTHelpers.getSymbol(tree.getExpression());
        return selectedSymbolQualifier != null && selectedSymbolQualifier.kind == Kind.PCK;
    }
}
