package mb.spoofax.lwb.compiler.definition;

import mb.cfg.CompileLanguageInput;
import mb.cfg.task.CfgRootDirectoryToObject;
import mb.cfg.task.CfgRootDirectoryToObjectException;
import mb.cfg.task.CfgToObject;
import mb.common.message.KeyedMessages;
import mb.common.message.KeyedMessagesBuilder;
import mb.common.option.Option;
import mb.common.result.Result;
import mb.common.util.ListView;
import mb.pie.api.ExecContext;
import mb.pie.api.Interactivity;
import mb.pie.api.None;
import mb.pie.api.STask;
import mb.pie.api.Stateful1Supplier;
import mb.pie.api.StatelessSerializableFunction;
import mb.pie.api.Supplier;
import mb.pie.api.Task;
import mb.pie.api.TaskDef;
import mb.pie.api.ValueSupplier;
import mb.pie.task.java.CompileJava;
import mb.resource.fs.FSPath;
import mb.resource.hierarchical.ResourcePath;
import mb.spoofax.compiler.adapter.AdapterProjectCompiler;
import mb.spoofax.compiler.language.LanguageProjectCompiler;
import mb.spoofax.compiler.platform.EclipseProjectCompiler;
import mb.spoofax.lwb.compiler.SpoofaxLwbCompilerScope;
import org.immutables.value.Value;

import javax.inject.Inject;
import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Function;

/**
 * Fully compiles a language definition by running the {@link LanguageProjectCompiler language project compiler},
 * meta-language compilers, {@link AdapterProjectCompiler adapter project compiler}, and the {@link CompileJava Java
 * compiler}. This fully compiles a language from its sources to Java class files and corresponding resources.
 *
 * Takes an {@link CompileLanguageDefinition.Args} as input, which contains the root directory that is used to find the
 * CFG file configuring the various compilers, and fields to add additional Java class and annotation processor paths.
 *
 * Produces a {@link Result} that is either an {@link Output} or a {@link CompileLanguageDefinitionException} when
 * compilation fails. The {@link Output} contains all {@link KeyedMessages messages} produced during compilation, and
 * the Java class path that can be used to run the language, dynamically load it, or to package it into a JAR file.
 */
@SpoofaxLwbCompilerScope @Value.Enclosing
public class CompileLanguageDefinition implements TaskDef<CompileLanguageDefinition.Args, Result<CompileLanguageDefinition.Output, CompileLanguageDefinitionException>> {
    @Value.Immutable
    public interface Args extends Serializable {
        class Builder extends CompileLanguageDefinitionData.Args.Builder {}

        static Builder builder() {return new Builder();}

        ResourcePath rootDirectory();

        List<Supplier<ListView<File>>> javaClassPathSuppliers();

        List<Supplier<ListView<File>>> javaAnnotationProcessorPathSuppliers();
    }

    @Value.Immutable
    public interface Output extends Serializable {
        class Builder extends CompileLanguageDefinitionData.Output.Builder {}

        static Builder builder() {return new Builder();}

        ResourcePath rootDirectory();

        @Value.NaturalOrder
        SortedSet<ResourcePath> javaClassPaths();

        String participantClassQualifiedId();

        KeyedMessages messages();
    }


    private final CfgRootDirectoryToObject cfgRootDirectoryToObject;
    private final LanguageDefinitionManager languageDefinitionManager;
    private final LanguageProjectCompiler languageProjectCompiler;
    private final CompileMetaLanguageSources compileMetaLanguageSources;
    private final AdapterProjectCompiler adapterProjectCompiler;
    private final EclipseProjectCompiler eclipseProjectCompiler;
    private final CompileJava compileJava;
    private final Function<CompileLanguageInput, String> participantClassQualifiedIdSelector;


    @Inject public CompileLanguageDefinition(
        CfgRootDirectoryToObject cfgRootDirectoryToObject,
        LanguageDefinitionManager languageDefinitionManager,
        LanguageProjectCompiler languageProjectCompiler,
        CompileMetaLanguageSources compileMetaLanguageSources,
        AdapterProjectCompiler adapterProjectCompiler,
        EclipseProjectCompiler eclipseProjectCompiler,
        CompileJava compileJava,
        Function<CompileLanguageInput, String> participantClassQualifiedIdSelector
    ) {
        this.cfgRootDirectoryToObject = cfgRootDirectoryToObject;
        this.languageDefinitionManager = languageDefinitionManager;
        this.languageProjectCompiler = languageProjectCompiler;
        this.compileMetaLanguageSources = compileMetaLanguageSources;
        this.adapterProjectCompiler = adapterProjectCompiler;
        this.eclipseProjectCompiler = eclipseProjectCompiler;
        this.compileJava = compileJava;
        this.participantClassQualifiedIdSelector = participantClassQualifiedIdSelector;
    }


    @Override public String getId() {
        return getClass().getName();
    }

    @Override
    public Result<Output, CompileLanguageDefinitionException> exec(ExecContext context, Args args) {
        // Check CFG configuration.
        final ResourcePath rootDirectory = args.rootDirectory();
        final Task<Result<CfgToObject.Output, CfgRootDirectoryToObjectException>> cfgTask = cfgRootDirectoryToObject.createTask(rootDirectory);
        final STask<Result<CfgToObject.Output, CfgRootDirectoryToObjectException>> cfgSupplier = cfgTask.toSupplier();
        final Result<CfgToObject.Output, CfgRootDirectoryToObjectException> cfgResult = context.require(cfgTask);
        if(cfgResult.isErr()) {
            // noinspection ConstantConditions (error is present)
            return Result.ofErr(CompileLanguageDefinitionException.getConfigurationFail(cfgResult.getErr()));
        }

        final KeyedMessagesBuilder messagesBuilder = new KeyedMessagesBuilder();
        @SuppressWarnings("ConstantConditions") final CfgToObject.Output cfgOutput = cfgResult.get();
        // noinspection ConstantConditions (value is present)
        languageDefinitionManager.registerLanguageDefinition(cfgOutput.compileLanguageInput.adapterProjectInput().adapterProject().project().coordinate(), rootDirectory);
        messagesBuilder.addMessages(cfgOutput.messages);

        final CompileLanguageInput input = cfgOutput.compileLanguageInput;
        final CompileJava.Input.Builder compileJavaInputBuilder = CompileJava.Input.builder().key(args.rootDirectory());
        final Output.Builder outputBuilder = Output.builder();

        // Generate language project
        final Task<Result<None, ?>> languageProjectCompilerTask = languageProjectCompiler.createTask(new LanguageProjectInputSupplier(cfgSupplier));
        // TODO: check result? It can only fail due to a CFG failure atm.
        context.require(languageProjectCompilerTask);
        compileJavaInputBuilder.addOriginTasks(languageProjectCompilerTask.toSupplier());

        final Task<Result<CompileMetaLanguageSources.Output, CompileMetaLanguageSourcesException>> compileMetaLanguageSourcesTask = compileMetaLanguageSources.createTask(rootDirectory);
        final Result<CompileMetaLanguageSources.Output, CompileMetaLanguageSourcesException> compileMetaLanguageSourcesResult = context.require(compileMetaLanguageSourcesTask);
        compileJavaInputBuilder.addSourceTasks(new LanguageSpecificationJavaSourcesSupplier(compileMetaLanguageSourcesTask.toSupplier()));
        if(compileMetaLanguageSourcesResult.isErr()) {
            // noinspection ConstantConditions (error is present)
            return Result.ofErr(CompileLanguageDefinitionException.compileMetaLanguageSourcesFail(compileMetaLanguageSourcesResult.getErr()));
        } else {
            // noinspection ConstantConditions (value is present)
            final CompileMetaLanguageSources.Output output = compileMetaLanguageSourcesResult.get();
            // noinspection ConstantConditions (value is really present)
            messagesBuilder.addMessages(output.messages());
            compileJavaInputBuilder.addClassPathSuppliers(new ValueSupplier<>(ListView.of(output.javaClassPaths()))); // TODO: use real supplier
            for(File javaClassPath : output.javaClassPaths()) {
                outputBuilder.addJavaClassPaths(new FSPath(javaClassPath));
            }
        }

        // Generate adapter project.
        final Task<Result<None, ?>> adapterProjectCompilerTask = adapterProjectCompiler.createTask(new AdapterProjectInputSupplier(cfgSupplier));
        // TODO: check result? It can only fail due to a CFG failure atm.
        context.require(adapterProjectCompilerTask);
        compileJavaInputBuilder.addOriginTasks(adapterProjectCompilerTask.toSupplier());

        // Generate Eclipse project.
        final Task<Result<None, ?>> eclipseProjectCompilerTask = eclipseProjectCompiler.createTask(new EclipseProjectInputSupplier(cfgSupplier));
        // TODO: check result? It can only fail due to a CFG failure atm.
        context.require(eclipseProjectCompilerTask);
        compileJavaInputBuilder.addOriginTasks(eclipseProjectCompilerTask.toSupplier());

        // Compile Java source code.
        compileJavaInputBuilder
            .addSourceTasks(new JavaSourcesSupplier(cfgSupplier))
            .addClassPathSuppliers(new ValueSupplier<>(ListView.of(input.javaClassPaths())))
            .addAllClassPathSuppliers(args.javaClassPathSuppliers())
            .addEnvironmentToClassPaths(true)
            .addAnnotationProcessorPathSuppliers(new ValueSupplier<>(ListView.of(input.javaAnnotationProcessorPaths())))
            .addAllAnnotationProcessorPathSuppliers(args.javaAnnotationProcessorPathSuppliers())
            .addEnvironmentToAnnotationProcessorPaths(true)
            .release(input.javaRelease())
            .sourceFileOutputDirectory(input.javaSourceFileOutputDirectory())
            .classFileOutputDirectory(input.javaClassFileOutputDirectory())
            .reportWarnings(false)
            .addShouldExecWhenAffectedTags(Interactivity.NonInteractive)
        ;
        final KeyedMessages javaCompilationMessages = context.require(compileJava, compileJavaInputBuilder.build());
        messagesBuilder.addMessages(javaCompilationMessages);
        if(javaCompilationMessages.containsError()) {
            return Result.ofErr(CompileLanguageDefinitionException.javaCompilationFail(messagesBuilder.build()));
        }

        return Result.ofOk(outputBuilder
            .rootDirectory(rootDirectory)
            .addJavaClassPaths(input.javaClassFileOutputDirectory())
            .addAllJavaClassPaths(input.resourcePaths())
            .participantClassQualifiedId(participantClassQualifiedIdSelector.apply(input))
            .messages(messagesBuilder.build())
            .build()
        );
    }

    @Override public boolean shouldExecWhenAffected(Args input, Set<?> tags) {
        return tags.isEmpty() || tags.contains(Interactivity.NonInteractive);
    }


    private static class LanguageProjectInputSupplier extends Stateful1Supplier<STask<Result<CfgToObject.Output, CfgRootDirectoryToObjectException>>, Result<LanguageProjectCompiler.Input, ?>> {
        public LanguageProjectInputSupplier(STask<Result<CfgToObject.Output, CfgRootDirectoryToObjectException>> state) {
            super(state);
        }

        @Override public Result<LanguageProjectCompiler.Input, ?> get(ExecContext context) {
            return context.requireMapping(state, new LanguageProjectInputMapper());
        }

        private static class LanguageProjectInputMapper extends StatelessSerializableFunction<Result<CfgToObject.Output, CfgRootDirectoryToObjectException>, Result<LanguageProjectCompiler.Input, CfgRootDirectoryToObjectException>> {
            @Override
            public Result<LanguageProjectCompiler.Input, CfgRootDirectoryToObjectException> apply(Result<CfgToObject.Output, CfgRootDirectoryToObjectException> result) {
                return result.map(o -> o.compileLanguageInput.languageProjectInput());
            }
        }
    }

    private static class AdapterProjectInputSupplier extends Stateful1Supplier<STask<Result<CfgToObject.Output, CfgRootDirectoryToObjectException>>, Result<AdapterProjectCompiler.Input, ?>> {
        public AdapterProjectInputSupplier(STask<Result<CfgToObject.Output, CfgRootDirectoryToObjectException>> state) {
            super(state);
        }

        @Override public Result<AdapterProjectCompiler.Input, ?> get(ExecContext context) {
            return context.requireMapping(state, new AdapterProjectInputMapper());
        }

        private static class AdapterProjectInputMapper extends StatelessSerializableFunction<Result<CfgToObject.Output, CfgRootDirectoryToObjectException>, Result<AdapterProjectCompiler.Input, CfgRootDirectoryToObjectException>> {
            @Override
            public Result<AdapterProjectCompiler.Input, CfgRootDirectoryToObjectException> apply(Result<CfgToObject.Output, CfgRootDirectoryToObjectException> result) {
                return result.map(o -> o.compileLanguageInput.adapterProjectInput());
            }
        }
    }

    private static class EclipseProjectInputSupplier extends Stateful1Supplier<STask<Result<CfgToObject.Output, CfgRootDirectoryToObjectException>>, Result<Option<EclipseProjectCompiler.Input>, ?>> {
        public EclipseProjectInputSupplier(STask<Result<CfgToObject.Output, CfgRootDirectoryToObjectException>> state) {
            super(state);
        }

        @Override public Result<Option<EclipseProjectCompiler.Input>, ?> get(ExecContext context) {
            return context.requireMapping(state, new EclipseProjectInputMapper());
        }

        private static class EclipseProjectInputMapper extends StatelessSerializableFunction<Result<CfgToObject.Output, CfgRootDirectoryToObjectException>, Result<Option<EclipseProjectCompiler.Input>, CfgRootDirectoryToObjectException>> {
            @Override
            public Result<Option<EclipseProjectCompiler.Input>, CfgRootDirectoryToObjectException> apply(Result<CfgToObject.Output, CfgRootDirectoryToObjectException> result) {
                return result.map(o -> Option.ofOptional(o.compileLanguageInput.eclipseProjectInput()));
            }
        }
    }


    private static class LanguageSpecificationJavaSourcesSupplier extends Stateful1Supplier<STask<Result<CompileMetaLanguageSources.Output, CompileMetaLanguageSourcesException>>, Result<CompileJava.Sources, ?>> {
        public LanguageSpecificationJavaSourcesSupplier(STask<Result<CompileMetaLanguageSources.Output, CompileMetaLanguageSourcesException>> state) {
            super(state);
        }

        @Override public Result<CompileJava.Sources, ?> get(ExecContext context) {
            return context.requireMapping(state, new LangaugeSpecificationJavaSourcesMapper());
        }

        private static class LangaugeSpecificationJavaSourcesMapper extends StatelessSerializableFunction<Result<CompileMetaLanguageSources.Output, CompileMetaLanguageSourcesException>, Result<CompileJava.Sources, CompileMetaLanguageSourcesException>> {
            @Override
            public Result<CompileJava.Sources, CompileMetaLanguageSourcesException> apply(Result<CompileMetaLanguageSources.Output, CompileMetaLanguageSourcesException> result) {
                return result.map(o -> CompileJava.Sources.builder().addAllSourceFiles(o.providedJavaFiles()).build());
            }
        }
    }

    private static class JavaSourcesSupplier extends Stateful1Supplier<STask<Result<CfgToObject.Output, CfgRootDirectoryToObjectException>>, Result<CompileJava.Sources, ?>> {
        public JavaSourcesSupplier(STask<Result<CfgToObject.Output, CfgRootDirectoryToObjectException>> state) {
            super(state);
        }

        @Override public Result<CompileJava.Sources, ?> get(ExecContext context) {
            return context.requireMapping(state, new JavaSourcesMapper());
        }

        private static class JavaSourcesMapper extends StatelessSerializableFunction<Result<CfgToObject.Output, CfgRootDirectoryToObjectException>, Result<CompileJava.Sources, CfgRootDirectoryToObjectException>> {
            @Override
            public Result<CompileJava.Sources, CfgRootDirectoryToObjectException> apply(Result<CfgToObject.Output, CfgRootDirectoryToObjectException> result) {
                return result.map(o -> CompileJava.Sources.builder()
                    .addAllSourceFiles(o.compileLanguageInput.javaSourceFiles())
                    .sourceFilesFromPaths(o.compileLanguageInput.userJavaSourcePaths())
                    .sourcePaths(o.compileLanguageInput.javaSourcePaths())
                    .packagePaths(o.compileLanguageInput.javaSourceDirectoryPaths())
                    .build()
                );
            }
        }
    }
}

