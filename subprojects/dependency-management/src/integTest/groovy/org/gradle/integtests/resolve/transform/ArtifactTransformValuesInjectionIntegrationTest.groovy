/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.resolve.transform

import org.gradle.api.file.FileCollection
import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import spock.lang.Unroll

class ArtifactTransformValuesInjectionIntegrationTest extends AbstractDependencyResolutionTest implements ArtifactTransformTestFixture {

    def "transform can receive parameters via abstract getter"() {
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorAttributes()
        buildFile << """
            allprojects {
                dependencies {
                    registerTransform(MakeGreen) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'green')
                        parameters {
                            extension = 'green'
                        }
                    }
                }
            }
            
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
            
            @TransformAction(MakeGreenAction)
            interface MakeGreen {
                String getExtension()
                void setExtension(String value)
            }
            
            abstract class MakeGreenAction extends ArtifactTransform {
                @TransformParameters
                abstract MakeGreen getConf()
                
                List<File> transform(File input) {
                    println "processing \${input.name}"
                    def output = new File(outputDirectory, input.name + "." + conf.extension)
                    output.text = "ok"
                    return [output]
                }
            }
"""

        when:
        run(":a:resolve")

        then:
        outputContains("processing b.jar")
        outputContains("processing c.jar")
        outputContains("result = [b.jar.green, c.jar.green]")
    }

    def "transform can receive file collection via parameter object"() {
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorAttributes()
        buildFile << """
            allprojects {
                dependencies {
                    registerTransform(MakeGreen) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'green')
                        parameters {
                            someFiles.from('a.txt')
                            someFiles.from('b.txt')
                        }
                    }
                }
            }
            
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
            
            @TransformAction(MakeGreenAction)
            interface MakeGreen {
                ConfigurableFileCollection getSomeFiles()
            }
            
            abstract class MakeGreenAction extends ArtifactTransform {
                @TransformParameters
                abstract MakeGreen getConf()
                
                List<File> transform(File input) {
                    println "processing \${input.name} using \${conf.someFiles*.name}"
                    def output = new File(outputDirectory, input.name + ".green")
                    output.text = "ok"
                    return [output]
                }
            }
"""

        when:
        run(":a:resolve")

        then:
        outputContains("processing b.jar using [a.txt, b.txt]")
        outputContains("processing c.jar using [a.txt, b.txt]")
        outputContains("result = [b.jar.green, c.jar.green]")
    }

    @Unroll
    def "transform can receive dependencies via abstract getter of type #targetType"() {
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransform()
        buildFile << """

project(':a') {
    dependencies {
        implementation project(':b')
    }
}
project(':b') {
    dependencies {
        implementation project(':c')
    }
}

abstract class MakeGreen extends ArtifactTransform {
    @PrimaryInputDependencies
    abstract ${targetType} getDependencies()
    
    List<File> transform(File input) {
        println "received dependencies files \${dependencies*.name} for processing \${input.name}"
        def output = new File(outputDirectory, input.name + ".green")
        output.text = "ok"
        return [output]
    }
}

"""

        when:
        run(":a:resolve")

        then:
        outputContains("received dependencies files [] for processing c.jar")
        outputContains("received dependencies files [c.jar] for processing b.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        where:
        targetType << ["FileCollection", "Iterable<File>"]
    }

    def "transform can receive workspace and primary input via abstract getter"() {
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransform()
        buildFile << """

project(':a') {
    dependencies {
        implementation project(':b')
        implementation project(':c')
    }
}

abstract class MakeGreen extends ArtifactTransform {
    @Workspace
    abstract File getWorkspace()
    
    @PrimaryInput
    abstract File getInputFile()
    
    List<File> transform(File input) {
        println "processing \${input.name}"
        assert workspace == outputDirectory
        assert inputFile == input
        def output = new File(outputDirectory, input.name + ".green")
        output.text = "ok"
        return [output]
    }
}

"""

        when:
        run(":a:resolve")

        then:
        outputContains("processing c.jar")
        outputContains("processing b.jar")
        outputContains("result = [b.jar.green, c.jar.green]")
    }

    def "transform cannot receive parameter object via constructor parameter"() {
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorAttributes()
        buildFile << """
            allprojects {
                dependencies {
                    registerTransform(MakeGreen) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'green')
                        parameters {
                            extension = 'green'
                        }
                    }
                }
            }
            
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
            
            @TransformAction(MakeGreenAction)
            interface MakeGreen {
                String getExtension()
                void setExtension(String value)
            }
            
            class MakeGreenAction extends ArtifactTransform {
                MakeGreen conf
                
                @Inject
                MakeGreenAction(MakeGreen conf) {
                    this.conf = conf
                }
                
                List<File> transform(File input) {
                    println "processing \${input.name}"
                    def output = new File(outputDirectory, input.name + "." + conf.extension)
                    output.text = "ok"
                    return [output]
                }
            }
"""

        when:
        fails(":a:resolve")

        then:
        // Documents existing behaviour. Should fail eagerly and with a better error message
        failure.assertHasDescription("Execution failed for task ':a:resolve'.")
        failure.assertHasCause("Execution failed for MakeGreenAction: ${file('b/build/b.jar')}.")
        failure.assertHasCause("Unable to determine constructor argument #1: missing parameter of interface MakeGreen, or no service of type interface MakeGreen")
    }

    @Unroll
    def "transform cannot use #annotation to receive dependencies"() {
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransform()
        buildFile << """

project(':a') {
    dependencies {
        implementation project(':b')
    }
}

abstract class MakeGreen extends ArtifactTransform {
    ${annotation}
    abstract FileCollection getDependencies()
    
    List<File> transform(File input) {
        dependencies.files
        throw new RuntimeException("broken")
    }
}
"""

        when:
        fails(":a:resolve")

        then:
        // Documents existing behaviour. Should fail eagerly and with a better error message
        failure.assertHasDescription("Execution failed for task ':a:resolve'.")
        failure.assertHasCause("Execution failed for MakeGreen: ${file('b/build/b.jar')}.")
        failure.assertHasCause("No service of type interface ${FileCollection.name} available.")

        where:
        annotation << ["@Workspace", "@PrimaryInput"]
    }

    def "transform cannot use @Inject to receive workspace or input file"() {
        settingsFile << """
            include 'a', 'b', 'c'
        """
        setupBuildWithColorTransform()
        buildFile << """

project(':a') {
    dependencies {
        implementation project(':b')
    }
}

abstract class MakeGreen extends ArtifactTransform {
    @Inject
    abstract File getWorkspace()
    
    List<File> transform(File input) {
        workspace
        throw new RuntimeException("broken")
    }
}
"""

        when:
        fails(":a:resolve")

        then:
        // Documents existing behaviour. Should fail eagerly and with a better error message
        failure.assertHasDescription("Execution failed for task ':a:resolve'.")
        failure.assertHasCause("Execution failed for MakeGreen: ${file('b/build/b.jar')}.")
        failure.assertHasCause("No service of type class ${File.name} available.")
    }

    @Unroll
    def "task implementation cannot use #annotation"() {
        buildFile << """
            class MyTask extends DefaultTask {
                ${annotation}
                File getThing() { null }
            }

            tasks.create('broken', MyTask)
        """

        expect:
        fails('broken')
        failure.assertHasCause("Could not create task of type 'MyTask'.")
        failure.assertHasCause("Could not generate a decorated class for class MyTask.")
        failure.assertHasCause("Cannot use ${annotation} annotation on method MyTask.getThing().")

        where:
        annotation << ["@Workspace", "@PrimaryInput"]
    }
}
