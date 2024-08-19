/* Copyright (C) 2014 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.datastore.gorm.boot.compiler

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.customizers.ImportCustomizer

// https://github.com/spring-projects/spring-boot/commit/0555dda63d17bbd4364c4eb2268da27480be4d05#diff-26fb6a79071ed389aa8e3ffe28cc8b0d171930c4e9a5277357170704e52f32c6
import org.springframework.boot.cli.compiler.AstUtils;
import org.springframework.boot.cli.compiler.CompilerAutoConfiguration
import org.springframework.boot.cli.compiler.DependencyCustomizer;

/**
 * A compiler configuration that automatically adds the necessary imports
 *
 * @author Graeme Rocher
 * @since 1.0
 *
 */
@CompileStatic
class GormCompilerAutoConfiguration extends CompilerAutoConfiguration{
    @Override
    boolean matches(ClassNode classNode) {
        return AstUtils.hasAtLeastOneAnnotation(classNode, "grails.persistence.Entity", "grails.gorm.annotation.Entity" ,"Entity")
    }

    @Override
    void applyDependencies(DependencyCustomizer dependencies) throws CompilationFailedException {
        dependencies.ifAnyMissingClasses("grails.persistence.Entity", "grails.gorm.annotation.Entity")
                        .add("grails-datastore-gorm-hibernate5")
    }

    @Override
    void applyImports(ImportCustomizer imports) throws CompilationFailedException {
        imports.addStarImports("grails.gorm", "grails.gorm.annotation")
    }
}