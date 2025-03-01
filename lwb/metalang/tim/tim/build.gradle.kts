import mb.spoofax.compiler.adapter.*
import mb.spoofax.compiler.adapter.data.*
import mb.spoofax.compiler.util.*
import mb.spoofax.core.language.command.*
import mb.spoofax.common.*

plugins {
  id("org.metaborg.gradle.config.java-library")
  id("org.metaborg.spoofax.compiler.gradle.spoofax2.language")
  id("org.metaborg.spoofax.compiler.gradle.adapter")
}

fun compositeBuild(name: String) = "$group:$name:$version"

dependencies {
  api(compositeBuild("spoofax.common"))
  api(compositeBuild("spoofax.compiler"))

  compileOnly("org.derive4j:derive4j-annotation")

  // Required because @Nullable has runtime retention (which includes classfile retention), and the Java compiler requires access to it.
  compileOnly("com.google.code.findbugs:jsr305")

  compileOnly("org.checkerframework:checker-qual-android")
  compileOnly("org.immutables:value-annotations")
  annotationProcessor("org.immutables:value")
  annotationProcessor("org.derive4j:derive4j")
}

val packageId = "mb.tim"
val taskPackageId = "$packageId.task"
val spoofaxTaskPackageId = "$taskPackageId.spoofax"
val debugTaskPackageId = "$taskPackageId.debug"
val commandPackageId = "$packageId.command"

languageProject {
  shared {
    name("Tim")
    defaultClassPrefix("Tim")
    defaultPackageId("mb.tim")
    addFileExtensions("tim")
  }
  compilerInput {
    withParser().run {
      startSymbol("TProgram")
    }
    withStyler()
    withConstraintAnalyzer().run {
      enableNaBL2(false)
      enableStatix(true)
      multiFile(false)
    }
    withStrategoRuntime().run {
      addStrategyPackageIds("tim.strategies")
      addInteropRegisterersByReflection("tim.strategies.InteropRegisterer")
    }
  }
}
spoofax2BasedLanguageProject {
  compilerInput {
    withParser()
    withStyler()
    withConstraintAnalyzer().run {
      copyStatix(true)
    }
    withStrategoRuntime().run {
      copyCtree(true)
      copyClasses(true)
    }
    project.languageSpecificationDependency(GradleDependency.project(":tim.spoofax2"))
  }
}

languageAdapterProject {
  compilerInput {
    withParser()
    withStyler()
    withConstraintAnalyzer()
    withStrategoRuntime()
    withReferenceResolution().run {
      resolveStrategy("editor-resolve")
    }
    withHover().run {
      hoverStrategy("editor-hover")
    }
    withGetSourceFiles()
    project.configureCompilerInput()
  }
}

fun AdapterProjectCompiler.Input.Builder.configureCompilerInput() {
  compositionGroup("mb.spoofax.lwb")

  // Symbols
  addLineCommentSymbols("//")
  addBlockCommentSymbols(BlockCommentSymbols("/*", "*/"))
  addBracketSymbols(BracketSymbols('[', ']'))
  addBracketSymbols(BracketSymbols('{', '}'))
  addBracketSymbols(BracketSymbols('(', ')'))

  isMultiFile(false)

  val showEvaluated = TypeInfo.of(taskPackageId, "TimShowEvaluated")
  val compileToLLVM = TypeInfo.of(taskPackageId, "TimCompileToLLVM")
  val showTyped = TypeInfo.of(taskPackageId, "TimShowTyped")
  addTaskDefs(showEvaluated, compileToLLVM, showTyped)

  val showEvaluatedCommand = CommandDefRepr.builder()
    .type(commandPackageId, showEvaluated.id() + "Command")
    .taskDefType(showEvaluated)
    .argType(showEvaluated.appendToId(".Args"))
    .displayName("Evaluate file to string")
    .description("Execute the file and return everything it printed to standard out.")
    .addSupportedExecutionTypes(CommandExecutionType.ManualOnce, CommandExecutionType.ManualContinuous)
    .addAllParams(listOf(
      ParamRepr.of("rootDirectory", TypeInfo.of("mb.resource.hierarchical", "ResourcePath"), true, ArgProviderRepr.enclosingContext(EnclosingCommandContextType.Project)),
      ParamRepr.of("file", TypeInfo.of("mb.resource", "ResourceKey"), true, ArgProviderRepr.context(CommandContextType.File))
    ))
    .build()
  val showEvaluatedCommandMenuItem = CommandActionRepr.builder()
    .manualOnce(showEvaluatedCommand)
    .fileRequired()
    .enclosingProjectRequired()
    .buildItem()

  val compileToLLVMCommand = CommandDefRepr.builder()
    .type(commandPackageId, compileToLLVM.id() + "Command")
    .taskDefType(compileToLLVM)
    .argType(compileToLLVM.appendToId(".Args"))
    .displayName("Compile file to LLVM")
    .description("Compile the Tim file to LLVM IR.")
    .addSupportedExecutionTypes(CommandExecutionType.ManualOnce, CommandExecutionType.ManualContinuous)
    .addAllParams(listOf(
      ParamRepr.of("rootDirectory", TypeInfo.of("mb.resource.hierarchical", "ResourcePath"), true, ArgProviderRepr.enclosingContext(EnclosingCommandContextType.Project)),
      ParamRepr.of("file", TypeInfo.of("mb.resource.hierarchical", "ResourcePath"), true, ArgProviderRepr.context(CommandContextType.File))
    ))
    .build()
  val compileToLLVMCommandMenuItem = CommandActionRepr.builder()
    .manualOnce(compileToLLVMCommand)
    .fileRequired()
    .enclosingProjectRequired()
    .buildItem()


  val showTypedCommand = CommandDefRepr.builder()
    .type(commandPackageId, showTyped.id() + "Command")
    .taskDefType(showTyped)
    .argType(showTyped.appendToId(".Args"))
    .displayName("Show processed representation")
    .description("Compile the Tim to a limited version.")
    .addSupportedExecutionTypes(CommandExecutionType.ManualOnce, CommandExecutionType.ManualContinuous)
    .addAllParams(listOf(
      ParamRepr.of("rootDirectory", TypeInfo.of("mb.resource.hierarchical", "ResourcePath"), true, ArgProviderRepr.enclosingContext(EnclosingCommandContextType.Project)),
      ParamRepr.of("file", TypeInfo.of("mb.resource.hierarchical", "ResourcePath"), true, ArgProviderRepr.context(CommandContextType.File))
    ))
    .build()
  val showTypedCommandMenuItem = CommandActionRepr.builder()
    .manualOnce(showTypedCommand)
    .fileRequired()
    .enclosingProjectRequired()
    .buildItem()

  val commands = listOf(
    showEvaluatedCommand,
    compileToLLVMCommand,
    showTypedCommand
  )
  addAllCommandDefs(commands)

  val menuItems = listOf(
    MenuItemRepr.menu("Run", listOf(showEvaluatedCommandMenuItem, compileToLLVMCommandMenuItem)),
    MenuItemRepr.menu("Debug", listOf(showTypedCommandMenuItem))
  )
  addAllMainMenuItems(menuItems)
  addAllEditorContextMenuItems(menuItems)
}
