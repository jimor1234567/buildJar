package com.adison.gradle.plugin

import com.android.SdkConstants
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.api.ApplicationVariant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.tasks.bundling.Jar
import proguard.gradle.ProGuardTask


class BuildJarPlugin implements Plugin<Project> {
    public static final String EXTENSION_NAME = "BuildJar";

    @Override
    public void apply(Project project) {
        DefaultDomainObjectSet<ApplicationVariant> variants
        if (project.getPlugins().hasPlugin(AppPlugin)) {
            variants = project.android.applicationVariants;

            project.extensions.create(EXTENSION_NAME, BuildJarExtension);

            applyTask(project, variants);
        }
    }

    private void applyTask(Project project, variants) {

        project.afterEvaluate {
            BuildJarExtension jarExtension = BuildJarExtension.getConfig(project);
            def includePackage = jarExtension.includePackage
            def excludeClass = jarExtension.excludeClass
            def excludePackage = jarExtension.excludePackage
            def excludeJar = jarExtension.excludeJar

            variants.all { variant ->
                if (variant.name.capitalize() == "Debug") {
                    def dexTask = project.tasks.findByName(BuildJarUtils.getDexTaskName(project, variant))
                    DebugUtils.debug("-------------------dexTask:" + dexTask)
                    if (dexTask != null) {
                        def buildJarBeforeDex = "buildJarBeforeDex${variant.name.capitalize()}"

                        def buildJar = project.tasks.create("buildJar", Jar)
                        buildJar.setDescription("构建jar包")
                        Closure buildJarClosure = {
                            buildJar.exclude("**/BuildConfig.class")
                            buildJar.exclude("**/BuildConfig\$*.class")
                            buildJar.exclude("**/R.class")
                            buildJar.exclude("**/R\$*.class")
                            buildJar.archiveName = jarExtension.outputFileName
                            buildJar.destinationDir = project.file(jarExtension.outputFileDir)
                            if (excludeClass != null && excludeClass.size() > 0) {
                                excludeClass.each {
                                    buildJar.exclude(it)
                                }

                            }
                            if (excludePackage != null && excludePackage.size() > 0) {
                                excludePackage.each {
                                    buildJar.exclude("${it}/**/*.class")
                                }

                            }
                            if (includePackage != null && includePackage.size() > 0) {
                                includePackage.each {
                                    DebugUtils.debug("includePackage---->${it}")
                                    buildJar.include("${it}/**/*.class")
                                }

                            } else {
                                //默认全项目构建jar
                                buildJar.include("**/*.class")

                            }
                        }
                        project.task(buildJarBeforeDex) << {
                            Set<File> inputFiles = BuildJarUtils.getDexTaskInputFiles(project, variant, dexTask)

                            inputFiles.each { inputFile ->

                                def path = inputFile.absolutePath
                                DebugUtils.debug("patchJarBefore----->" + path)
                                if (path.endsWith(SdkConstants.DOT_JAR) && !BuildJarUtils.isExcludedJar(path, excludeJar)) {
                                    buildJar.from(project.zipTree(path))
                                } else if (inputFile.isDirectory()) {
                                    //intermediates/classes/debug
                                    buildJar.from(inputFile)
                                }
                            }
                        }

                        def buildProguardJar = project.tasks.create("buildProguardJar", ProGuardTask);
                        buildProguardJar.setDescription("混淆jar包")
                        buildProguardJar.dependsOn buildJar
                        //设置不删除未引用的资源(类，方法等)
                        buildProguardJar.dontshrink();
                        //忽略警告
                        buildProguardJar.ignorewarnings()
                        buildProguardJar.injars(jarExtension.outputFileDir + "/" + jarExtension.outputFileName)
                        buildProguardJar.outjars(jarExtension.outputFileDir + "/" + jarExtension.outputProguardFileName)
                        buildProguardJar.libraryjars(project.android.getSdkDirectory().toString() + "/platforms/" + "${project.android.compileSdkVersion}" + "/android.jar")
                        // JAVA HOME
                        def javaBase = System.properties["java.home"]
                        def javaRt = "/lib/rt.jar"
                        if (System.properties["os.name"].toString().toLowerCase().contains("mac")) {
                            if (!new File(javaBase + javaRt).exists()) {
                                javaRt = "/../Classes/classes.jar"
                            }
                        }
                        buildProguardJar.libraryjars(javaBase + "/" + javaRt)
                        buildProguardJar.configuration(jarExtension.proguardConfigFile)
                        if (jarExtension.needDefaultProguard) {
                            buildProguardJar.configuration(project.android.getDefaultProguardFile('proguard-android.txt'))
                        }
                        buildProguardJar.printmapping(jarExtension.outputFileDir+"/"+"mapping.txt")
                        def buildJarBeforeDexTask = project.tasks[buildJarBeforeDex]
                        buildJarBeforeDexTask.dependsOn dexTask.taskDependencies.getDependencies(dexTask)
                        buildJar.dependsOn buildJarBeforeDexTask
                        buildJar.doFirst(buildJarClosure)
                    }
                }

            }
        }
    }


}
