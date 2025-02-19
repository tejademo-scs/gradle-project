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

package com.palantir.baseline

import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult

class BaselineNullAwayIntegrationTest extends IntegrationSpec {

    def standardBuildFile = '''
        apply plugin: 'com.palantir.baseline-java-versions'
        apply plugin: 'com.palantir.baseline-null-away'
        apply plugin: 'com.palantir.baseline-error-prone'
        apply plugin: 'java'
        repositories {
            mavenLocal()
            mavenCentral()
        }
        javaVersions {
            libraryTarget = 17
        }
        allprojects {
            afterEvaluate {
                plugins.withId('net.ltgt.errorprone', {
                    tasks.withType(JavaCompile).configureEach({
                      options.errorprone.excludedPaths = null
                      options.compilerArgs += ['-Werror']
                    })
                })
            }
        }
    '''.stripIndent()

    def validJavaFile = '''
        package com.palantir.test;
        public class Test { void test() {} }
        '''.stripIndent()

    def invalidJavaFile = '''
        package com.palantir.test;
        public class Test {
            int test(Throwable throwable) {
                // uh-oh, getMessage may be null!
                return throwable.getMessage().hashCode();
            }
        }
        '''.stripIndent()

    def 'Can apply plugin'() {
        when:
        buildFile << standardBuildFile

        then:
        runTasksSuccessfully('compileJava', '--info')
    }

    def 'compileJava fails when null-away finds errors'() {
        when:
        buildFile << standardBuildFile
        writeJavaSourceFile(invalidJavaFile)

        then:
        ExecutionResult result = runTasksWithFailure('compileJava')
        result.standardError.contains("[NullAway] dereferenced expression throwable.getMessage() is @Nullable")
    }

    def 'Test tasks are not impacted by null-away'() {
        when:
        buildFile << standardBuildFile
        writeJavaSourceFile(invalidJavaFile, "src/test/java")

        then:
        runTasksSuccessfully('compileTestJava')
    }

    def 'Integration test tasks are not impacted by null-away'() {
        when:
        buildFile << '''
            apply plugin: 'org.unbroken-dome.test-sets'
        '''.stripIndent(true)
        buildFile << standardBuildFile
        buildFile << '''
        testSets {
            integrationTest
        }
        '''.stripIndent(true)
        writeJavaSourceFile(invalidJavaFile, "src/integrationTest/java")

        then:
        runTasksSuccessfully('compileIntegrationTestJava')
    }

    def 'compileJava succeeds when null-away finds no errors'() {
        when:
        buildFile << standardBuildFile
        writeJavaSourceFile(validJavaFile)

        then:
        runTasksSuccessfully('compileJava')
    }
}
