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

import org.junit.jupiter.api.Test;

class SafeLoggingPropagationTest {

    @Test
    void testAddsAnnotation_dnlType() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  BearerToken token();",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@DoNotLog",
                        "@Value.Immutable",
                        "interface Test {",
                        "  BearerToken token();",
                        "}")
                .doTest();
    }

    @Test
    void testAddsAnnotation_extendsDnlInterface() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "class Test {",
                        "  @DoNotLog",
                        "  interface DnlIface {",
                        "      Object value();",
                        "  }",
                        "  @Value.Immutable",
                        "  interface ImmutablesIface extends DnlIface {",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "class Test {",
                        "  @DoNotLog",
                        "  interface DnlIface {",
                        "      Object value();",
                        "  }",
                        "  @DoNotLog",
                        "  @Value.Immutable",
                        "  interface ImmutablesIface extends DnlIface {",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testAddsAnnotation_extendsInterfaceWithDnlType() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "class Test {",
                        "  interface DnlIface {",
                        "      BearerToken value();",
                        "  }",
                        "  @Value.Immutable",
                        "  interface ImmutablesIface extends DnlIface {",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "class Test {",
                        "  interface DnlIface {",
                        "      BearerToken value();",
                        "  }",
                        "  @DoNotLog",
                        "  @Value.Immutable",
                        "  interface ImmutablesIface extends DnlIface {",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testMixedSafety() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @Safe String one();",
                        "  @Unsafe String two();",
                        "  String three();",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Unsafe",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @Safe String one();",
                        "  @Unsafe String two();",
                        "  String three();",
                        "}")
                .doTest();
    }

    @Test
    void testAddsAnnotation_dnlReturnValue() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  String token();",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@DoNotLog",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  String token();",
                        "}")
                .doTest();
    }

    @Test
    void testReplacesAnnotation_dnlReturnValue() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Unsafe",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  String token();",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@DoNotLog",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  String token();",
                        "}")
                .doTest();
    }

    @Test
    void testDoesNotReplaceStrictAnnotation() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "@DoNotLog",
                        "interface Test {",
                        "  @Unsafe",
                        "  String token();",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testPropagationBasedOnToString() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "abstract class Test {",
                        "  @DoNotLog",
                        "  abstract String token();",
                        "  @Override @DoNotLog public String toString() {",
                        "    return \"Test\" + token();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "@DoNotLog",
                        "abstract class Test {",
                        "  @DoNotLog",
                        "  abstract String token();",
                        "  @Override @DoNotLog public String toString() {",
                        "    return \"Test\" + token();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testPropagationReplacementBasedOnToString() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "@Unsafe",
                        "abstract class Test {",
                        "  @DoNotLog",
                        "  abstract String token();",
                        "  @Override @DoNotLog public String toString() {",
                        "    return \"Test\" + token();",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "@DoNotLog",
                        "abstract class Test {",
                        "  @DoNotLog",
                        "  abstract String token();",
                        "  @Override @DoNotLog public String toString() {",
                        "    return \"Test\" + token();",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testMethodDoesNotReturn() {
        // Ensure we don't fail when no return statement safety info is collected
        fix().addInputLines(
                        "Test.java",
                        "abstract class Test {",
                        "  @Override public String toString() {",
                        "    throw new RuntimeException();",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testRecordWithUnsafeTypes() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "record Test(BearerToken token) {}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.tokens.auth.*;",
                        "import com.palantir.logsafe.*;",
                        "@DoNotLog",
                        "record Test(BearerToken token) {}")
                .doTest();
    }

    @Test
    void testWrappedRecordComponents() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.*;",
                        "class Test {",
                        "  @Unsafe",
                        "  class UnsafeType {}",
                        "  record Rec1(UnsafeType val) {}",
                        "  record Rec2(List<UnsafeType> val) {}",
                        "  record Rec3(Optional<UnsafeType> val) {}",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.*;",
                        "class Test {",
                        "  @Unsafe",
                        "  class UnsafeType {}",
                        "  @Unsafe",
                        "  record Rec1(UnsafeType val) {}",
                        "  @Unsafe",
                        "  record Rec2(List<UnsafeType> val) {}",
                        "  @Unsafe",
                        "  record Rec3(Optional<UnsafeType> val) {}",
                        "}")
                .doTest();
    }

    @Test
    void testDoesNotAddSafeAnnotation() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @Safe",
                        "  String token();",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testIgnoresStaticMethods() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  static String token() { return \"\"; }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testIgnoresPrivateMethods() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  private String token() { return \"\"; }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void includesDefaultMethodWhenDefaultAsDefault() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "@Value.Style(defaultAsDefault = true)",
                        "interface Test {",
                        "  @DoNotLog",
                        "  default String token() { return \"\"; }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@DoNotLog",
                        "@Value.Immutable",
                        "@Value.Style(defaultAsDefault = true)",
                        "interface Test {",
                        "  @DoNotLog",
                        "  default String token() { return \"\"; }",
                        "}")
                .doTest();
    }

    @Test
    void includesDefaultMethodWhenDefaultAsDefault_indirectAnnotation() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "interface Test {",
                        "  @Value.Immutable",
                        "  @CustomStyle",
                        "  interface Sub {",
                        "    @DoNotLog",
                        "    default String token() { return \"\"; }",
                        "  }",
                        "  @Value.Style(defaultAsDefault = true)",
                        "  public @interface CustomStyle {}",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "interface Test {",
                        "  @DoNotLog",
                        "  @Value.Immutable",
                        "  @CustomStyle",
                        "  interface Sub {",
                        "    @DoNotLog",
                        "    default String token() { return \"\"; }",
                        "  }",
                        "  @Value.Style(defaultAsDefault = true)",
                        "  public @interface CustomStyle {}",
                        "}")
                .doTest();
    }

    @Test
    void includesDefaultMethodWhenDefault() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  @Value.Default",
                        "  default String token() { return \"\"; }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@DoNotLog",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  @Value.Default",
                        "  default String token() { return \"\"; }",
                        "}")
                .doTest();
    }

    @Test
    void includesDefaultMethodWhenDerived() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  @Value.Derived",
                        "  default String token() { return \"\"; }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@DoNotLog",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  @Value.Derived",
                        "  default String token() { return \"\"; }",
                        "}")
                .doTest();
    }

    @Test
    void includesDefaultMethodWhenLazy() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  @Value.Lazy",
                        "  default String token() { return \"\"; }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@DoNotLog",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  @Value.Lazy",
                        "  default String token() { return \"\"; }",
                        "}")
                .doTest();
    }

    @Test
    void testIgnoresHelperMethods_iface() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  default String token() { return \"\"; }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testIgnoresHelperMethods_abstract() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "abstract class Test {",
                        "  @DoNotLog",
                        "  String token() { return \"\"; }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testIncludesJacksonAnnotatedHelperMethods_iface() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.fasterxml.jackson.annotation.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  @JsonProperty",
                        "  default String token() { return \"\"; }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.fasterxml.jackson.annotation.*;",
                        "import org.immutables.value.Value;",
                        "@DoNotLog",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  @JsonProperty",
                        "  default String token() { return \"\"; }",
                        "}")
                .doTest();
    }

    @Test
    void testIncludesJacksonAnnotatedHelperMethods_abstract() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.fasterxml.jackson.annotation.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "abstract class Test {",
                        "  @DoNotLog",
                        "  @JsonProperty",
                        "  String token() { return \"\"; }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.fasterxml.jackson.annotation.*;",
                        "import org.immutables.value.Value;",
                        "@DoNotLog",
                        "@Value.Immutable",
                        "abstract class Test {",
                        "  @DoNotLog",
                        "  @JsonProperty",
                        "  String token() { return \"\"; }",
                        "}")
                .doTest();
    }

    @Test
    void testIgnoresJsonIgnoreHelperMethod() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.fasterxml.jackson.annotation.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  @JsonIgnore",
                        "  default String token() { return \"\"; }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testIncludesJsonIgnored() {
        // JsonIgnored methods are included in safety because they're
        // still included in the toString value.
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.fasterxml.jackson.annotation.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  @JsonIgnore",
                        "  String token();",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.fasterxml.jackson.annotation.*;",
                        "import org.immutables.value.Value;",
                        "@DoNotLog",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  @JsonIgnore",
                        "  String token();",
                        "}")
                .doTest();
    }

    @Test
    void testRedactedIsDoNotLog() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @Value.Redacted",
                        "  String token();",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  @Value.Redacted",
                        "  String token();",
                        "}")
                .doTest();
    }

    @Test
    void testRedactedMayBeUnsafe() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.fasterxml.jackson.annotation.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @Unsafe",
                        "  @JsonValue",
                        "  @Value.Redacted",
                        "  String token();",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.fasterxml.jackson.annotation.*;",
                        "import org.immutables.value.Value;",
                        "@Unsafe",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @Unsafe",
                        "  @JsonValue",
                        "  @Value.Redacted",
                        "  String token();",
                        "}")
                .doTest();
    }

    @Test
    void testRedactedIsDoNotLog_jsonSerializable() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.fasterxml.jackson.databind.annotation.*;",
                        "import org.immutables.value.Value;",
                        "@JsonSerialize",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @Value.Redacted",
                        "  String token();",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.fasterxml.jackson.databind.annotation.*;",
                        "import org.immutables.value.Value;",
                        "@DoNotLog",
                        "@JsonSerialize",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  @Value.Redacted",
                        "  String token();",
                        "}")
                .doTest();
    }

    @Test
    void testIgnoresRedacted() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  @Value.Redacted",
                        "  String token();",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testIgnoresRedacted_jsonIgnore() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.fasterxml.jackson.annotation.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  @JsonIgnore",
                        "  @Value.Redacted",
                        "  String token();",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testRedacted_jsonValue() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.fasterxml.jackson.annotation.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  @JsonValue",
                        "  @Value.Redacted",
                        "  String token();",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.fasterxml.jackson.annotation.*;",
                        "import org.immutables.value.Value;",
                        "@DoNotLog",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  @JsonValue",
                        "  @Value.Redacted",
                        "  String token();",
                        "}")
                .doTest();
    }

    @Test
    void testRedacted_jsonSerialize() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.fasterxml.jackson.databind.annotation.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "@JsonSerialize",
                        "interface Test {",
                        "  @DoNotLog",
                        "  @Value.Redacted",
                        "  String token();",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.fasterxml.jackson.databind.annotation.*;",
                        "import org.immutables.value.Value;",
                        "@DoNotLog",
                        "@Value.Immutable",
                        "@JsonSerialize",
                        "interface Test {",
                        "  @DoNotLog",
                        "  @Value.Redacted",
                        "  String token();",
                        "}")
                .doTest();
    }

    @Test
    void testIgnoresVoidMethods() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  void token();",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testIgnoresMethodsWithParameters() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  String token(int i);",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testIgnoresInterfacesWithMethodsWithParameters() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "interface Test {",
                        "  @DoNotLog",
                        "  String getToken();",
                        "  @DoNotLog",
                        "  String token(int i);",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testIncludesIfacesWithMethodsWithParametersIfImmutables() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  String getToken();",
                        "  @DoNotLog",
                        "  default String token(int i) { return \"\"; };",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import org.immutables.value.Value;",
                        "@DoNotLog",
                        "@Value.Immutable",
                        "interface Test {",
                        "  @DoNotLog",
                        "  String getToken();",
                        "  @DoNotLog",
                        "  default String token(int i) { return \"\"; };",
                        "}")
                .doTest();
    }

    @Test
    void testIgnoresThrowable() {
        // exceptions are unsafe-by-default, it's unnecessary to annotate every exception as unsafe.
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class MyException extends RuntimeException {",
                        "  @Override public String getMessage() {",
                        "     return super.getMessage();",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testIgnoresAnonymous() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.palantir.tokens.auth.*;",
                        "import java.util.function.*;",
                        "public final class Test {",
                        "  private static final Supplier<?> supplier = new Supplier<BearerToken>() {",
                        "    @Override public BearerToken get() {",
                        "      return BearerToken.valueOf(\"abcdefghijklmnopq\");",
                        "    }",
                        "  };",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testAddsMethodAnnotations() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.palantir.tokens.auth.*;",
                        "public final class Test {",
                        "  public Object get() {",
                        "    return BearerToken.valueOf(\"abcdefghijklmnopq\");",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.palantir.tokens.auth.*;",
                        "public final class Test {",
                        "  @DoNotLog public Object get() {",
                        "    return BearerToken.valueOf(\"abcdefghijklmnopq\");",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testDoesNotAnnotateSafe() {
        // Perhaps some day
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "public final class Test {",
                        "  public Object get(@Safe String safe) {",
                        "    return safe;",
                        "  }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testAddsMethodAnnotationByMergingSafety() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "public final class Test {",
                        "  public Object get(int in, @Safe String safe, @Unsafe String unsafe, String unknown) {",
                        "    switch (in) {",
                        "      case 0:",
                        "        return safe;",
                        "      case 1:",
                        "        return unsafe;",
                        "      default:",
                        "        return unknown;",
                        "    }",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "public final class Test {",
                        "  @Unsafe",
                        "  public Object get(int in, @Safe String safe, @Unsafe String unsafe, String unknown) {",
                        "    switch (in) {",
                        "      case 0:",
                        "        return safe;",
                        "      case 1:",
                        "        return unsafe;",
                        "      default:",
                        "        return unknown;",
                        "    }",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testIgnoresOutOfScopeReturns() {
        // Perhaps some day
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.palantir.tokens.auth.*;",
                        "import java.util.concurrent.Callable;",
                        "public final class Test {",
                        "  public Object get(@Unsafe String unsafe) throws Exception {",
                        "    Callable<Object> one = new Callable<Object>() {",
                        "        @Override",
                        "        public Object call() throws Exception {",
                        "            return BearerToken.valueOf(\"abcdefghijklmnopq\");",
                        "        }",
                        "    };",
                        "    Callable<Object> two = () -> {",
                        "        return BearerToken.valueOf(\"abcdefghijklmnopq\");",
                        "    };",
                        "    return unsafe;",
                        "  }",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import com.palantir.tokens.auth.*;",
                        "import java.util.concurrent.Callable;",
                        "public final class Test {",
                        "  @Unsafe",
                        "  public Object get(@Unsafe String unsafe) throws Exception {",
                        "    Callable<Object> one = new Callable<Object>() {",
                        "        @Override",
                        "        public Object call() throws Exception {",
                        "            return BearerToken.valueOf(\"abcdefghijklmnopq\");",
                        "        }",
                        "    };",
                        "    Callable<Object> two = () -> {",
                        "        return BearerToken.valueOf(\"abcdefghijklmnopq\");",
                        "    };",
                        "    return unsafe;",
                        "  }",
                        "}")
                .doTest();
    }

    @Test
    void testSafetyAnnotatedReturnTypeDoesNotAnnotateMethod() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "public final class Test {",
                        "  @Unsafe",
                        "  private static class UnsafeType {}",
                        "  public UnsafeType getType() { return new UnsafeType(); }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testAddsAnnotation_sealedTypes() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  sealed interface Base permits Dnl {}",
                        "  @DoNotLog",
                        "  final class Dnl implements Base {}",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog",
                        "  sealed interface Base permits Dnl {}",
                        "  @DoNotLog",
                        "  final class Dnl implements Base {}",
                        "}")
                .doTest();
    }

    @Test
    void testSafetyAnnotatedArrayTypeDoesNotAnnotateMethod() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "public final class Test {",
                        "  @Unsafe",
                        "  private static class UnsafeType {}",
                        "  public UnsafeType[] getType() { return new UnsafeType[]{ new UnsafeType() }; }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testAddsAnnotation_jacksonSubTypes_defaultImpl() {
        fix().addInputLines(
                        "Test.java",
                        "import com.fasterxml.jackson.annotation.*;",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @JsonTypeInfo(",
                        "    use = JsonTypeInfo.Id.NAME,",
                        "    property = \"type\",",
                        "    defaultImpl = Dnl.class)",
                        "  @JsonSubTypes(value = {",
                        "    @JsonSubTypes.Type(value = Unmarked.class, name = \"u\")",
                        "  })",
                        "  interface Base {}",
                        "  @DoNotLog",
                        "  class Dnl implements Base {}",
                        "  class Unmarked implements Base {}",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.fasterxml.jackson.annotation.*;",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog",
                        "  @JsonTypeInfo(",
                        "    use = JsonTypeInfo.Id.NAME,",
                        "    property = \"type\",",
                        "    defaultImpl = Dnl.class)",
                        "  @JsonSubTypes(value = {",
                        "    @JsonSubTypes.Type(value = Unmarked.class, name = \"u\")",
                        "  })",
                        "  interface Base {}",
                        "  @DoNotLog",
                        "  class Dnl implements Base {}",
                        "  class Unmarked implements Base {}",
                        "}")
                .doTest();
    }

    @Test
    void testSafetyAnnotatedCollectionTypeDoesNotAnnotateMethod() {
        fix().addInputLines(
                        "Test.java",
                        "import com.palantir.logsafe.*;",
                        "import java.util.*;",
                        "public final class Test {",
                        "  @Unsafe",
                        "  private static class UnsafeType {}",
                        "  public List<UnsafeType> getType() { return new ArrayList<UnsafeType>(); }",
                        "}")
                .expectUnchanged()
                .doTest();
    }

    @Test
    void testAddsAnnotation_jacksonSubTypes_subtypes_array() {
        fix().addInputLines(
                        "Test.java",
                        "import com.fasterxml.jackson.annotation.*;",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @JsonTypeInfo(",
                        "    use = JsonTypeInfo.Id.NAME,",
                        "    property = \"type\")",
                        "  @JsonSubTypes(value = {",
                        "    @JsonSubTypes.Type(value = Dnl.class, name = \"dnl\")",
                        "  })",
                        "  interface Base {}",
                        "  @DoNotLog",
                        "  class Dnl implements Base {}",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.fasterxml.jackson.annotation.*;",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog",
                        "  @JsonTypeInfo(",
                        "    use = JsonTypeInfo.Id.NAME,",
                        "    property = \"type\")",
                        "  @JsonSubTypes(value = {",
                        "    @JsonSubTypes.Type(value = Dnl.class, name = \"dnl\")",
                        "  })",
                        "  interface Base {}",
                        "  @DoNotLog",
                        "  class Dnl implements Base {}",
                        "}")
                .doTest();
    }

    @Test
    void testAddsAnnotation_jacksonSubTypes_subtypes_implicitArray() {
        fix().addInputLines(
                        "Test.java",
                        "import com.fasterxml.jackson.annotation.*;",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @JsonTypeInfo(",
                        "    use = JsonTypeInfo.Id.NAME,",
                        "    property = \"type\")",
                        "  @JsonSubTypes(",
                        "    @JsonSubTypes.Type(value = Dnl.class, name = \"dnl\")",
                        "  )",
                        "  interface Base {}",
                        "  @DoNotLog",
                        "  class Dnl implements Base {}",
                        "}")
                .addOutputLines(
                        "Test.java",
                        "import com.fasterxml.jackson.annotation.*;",
                        "import com.palantir.logsafe.*;",
                        "class Test {",
                        "  @DoNotLog",
                        "  @JsonTypeInfo(",
                        "    use = JsonTypeInfo.Id.NAME,",
                        "    property = \"type\")",
                        "  @JsonSubTypes(",
                        "    @JsonSubTypes.Type(value = Dnl.class, name = \"dnl\")",
                        "  )",
                        "  interface Base {}",
                        "  @DoNotLog",
                        "  class Dnl implements Base {}",
                        "}")
                .doTest();
    }

    private RefactoringValidator fix(String... args) {
        return RefactoringValidator.of(SafeLoggingPropagation.class, getClass(), args);
    }
}
