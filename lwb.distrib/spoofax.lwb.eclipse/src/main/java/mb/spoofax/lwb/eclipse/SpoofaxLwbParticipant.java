package mb.spoofax.lwb.eclipse;

import mb.cfg.CfgComponent;
import mb.cfg.eclipse.CfgLanguageFactory;
import mb.esv.eclipse.EsvEclipseComponent;
import mb.esv.eclipse.EsvLanguageFactory;
import mb.gpp.GppComponent;
import mb.gpp.GppResourcesComponent;
import mb.gpp.eclipse.GppLanguageFactory;
import mb.libspoofax2.LibSpoofax2ResourcesComponent;
import mb.libspoofax2.eclipse.LibSpoofax2EclipseComponent;
import mb.libspoofax2.eclipse.LibSpoofax2LanguageFactory;
import mb.libstatix.LibStatixResourcesComponent;
import mb.libstatix.eclipse.LibStatixEclipseComponent;
import mb.libstatix.eclipse.LibStatixLanguageFactory;
import mb.pie.api.TaskDef;
import mb.pie.dagger.PieComponent;
import mb.pie.dagger.RootPieModule;
import mb.pie.dagger.TaskDefsProvider;
import mb.pie.runtime.PieBuilderImpl;
import mb.pie.runtime.store.SerializingStoreBuilder;
import mb.pie.runtime.tracer.LoggingTracer;
import mb.pie.serde.fst.FstSerde;
import mb.resource.dagger.ResourceRegistriesProvider;
import mb.resource.dagger.ResourceServiceComponent;
import mb.resource.fs.FSResource;
import mb.sdf3.Sdf3Component;
import mb.sdf3.eclipse.Sdf3LanguageFactory;
import mb.sdf3_ext_statix.Sdf3ExtStatixComponent;
import mb.sdf3_ext_statix.eclipse.Sdf3ExtStatixLanguageFactory;
import mb.spoofax.compiler.dagger.DaggerSpoofaxCompilerComponent;
import mb.spoofax.compiler.dagger.SpoofaxCompilerComponent;
import mb.spoofax.compiler.dagger.SpoofaxCompilerModule;
import mb.spoofax.compiler.util.TemplateCompiler;
import mb.spoofax.core.Coordinate;
import mb.spoofax.core.Version;
import mb.spoofax.core.component.ComponentDependencyResolver;
import mb.spoofax.core.component.SubcomponentRegistry;
import mb.spoofax.eclipse.EclipseLanguageComponent;
import mb.spoofax.eclipse.EclipseParticipant;
import mb.spoofax.eclipse.EclipsePlatformComponent;
import mb.spoofax.eclipse.EclipseResourceServiceComponent;
import mb.spoofax.eclipse.log.EclipseLoggerComponent;
import mb.spoofax.eclipse.util.ResourceUtil;
import mb.spoofax.lwb.compiler.CompileLanguage;
import mb.spoofax.lwb.compiler.dagger.Spoofax3Compiler;
import mb.spoofax.lwb.compiler.dagger.Spoofax3CompilerModule;
import mb.spoofax.lwb.dynamicloading.DynamicLoadingPieModule;
import mb.spoofax.lwb.eclipse.compiler.DaggerEclipseSpoofax3CompilerComponent;
import mb.spoofax.lwb.eclipse.compiler.EclipseSpoofax3CompilerComponent;
import mb.spoofax.lwb.eclipse.dynamicloading.DaggerEclipseDynamicLoadingComponent;
import mb.spoofax.lwb.eclipse.dynamicloading.EclipseDynamicLoadingComponent;
import mb.spoofax.lwb.eclipse.util.ClassPathUtil;
import mb.spt.dynamicloading.DynamicLanguageUnderTestProvider;
import mb.spt.eclipse.SptLanguageFactory;
import mb.statix.eclipse.StatixEclipseComponent;
import mb.statix.eclipse.StatixLanguageFactory;
import mb.str.eclipse.StrategoEclipseComponent;
import mb.str.eclipse.StrategoLanguageFactory;
import mb.strategolib.StrategoLibComponent;
import mb.strategolib.StrategoLibResourcesComponent;
import mb.strategolib.eclipse.StrategoLibLanguageFactory;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.core.runtime.IExecutableExtensionFactory;
import org.eclipse.core.runtime.IPath;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.function.Consumer;

public class SpoofaxLwbParticipant implements EclipseParticipant {
    private static @Nullable SpoofaxLwbParticipant instance;

    private SpoofaxLwbParticipant() {}

    public static SpoofaxLwbParticipant getInstance() {
        if(instance == null) {
            instance = new SpoofaxLwbParticipant();
        }
        return instance;
    }

    public static class Factory implements IExecutableExtensionFactory {
        @Override public SpoofaxLwbParticipant create() {
            return SpoofaxLwbParticipant.getInstance();
        }
    }


    private @Nullable ResourceServiceComponent resourceServiceComponent;
    private @Nullable Spoofax3Compiler spoofax3Compiler;
    private @Nullable EclipseDynamicLoadingComponent dynamicLoadingComponent;
    private @Nullable PieComponent pieComponent;
    private @Nullable SpoofaxLwbComponent spoofaxLwbComponent;


    public ResourceServiceComponent getResourceServiceComponent() {
        if(resourceServiceComponent == null) {
            throw new RuntimeException("ResourceServiceComponent has not been initialized yet or has been closed");
        }
        return resourceServiceComponent;
    }

    public Spoofax3Compiler getSpoofax3Compiler() {
        if(spoofax3Compiler == null) {
            throw new RuntimeException("Spoofax3Compiler has not been initialized yet or has been closed");
        }
        return spoofax3Compiler;
    }

    public EclipseDynamicLoadingComponent getDynamicLoadingComponent() {
        if(dynamicLoadingComponent == null) {
            throw new RuntimeException("EclipseDynamicLoadingComponent has not been initialized yet or has been closed");
        }
        return dynamicLoadingComponent;
    }

    public PieComponent getPieComponent() {
        if(pieComponent == null) {
            throw new RuntimeException("PieComponent has not been initialized yet or has been closed");
        }
        return pieComponent;
    }

    public SpoofaxLwbComponent getSpoofaxLwbComponent() {
        if(spoofaxLwbComponent == null) {
            throw new RuntimeException("SpoofaxLwbComponent has not been initialized yet or has been closed");
        }
        return spoofaxLwbComponent;
    }


    @Override
    public Coordinate getCoordinate() {
        return new Coordinate("org.metaborg", "spoofax.eclipse.lwb", new Version(3)); // TODO: get actual version.
    }

    @Override
    public @Nullable String getCompositionGroup() {
        return "mb.spoofax.lwb";
    }

    @Override
    public @Nullable ResourceRegistriesProvider getResourceRegistriesProvider(
        EclipseLoggerComponent loggerComponent,
        EclipseResourceServiceComponent baseResourceServiceComponent,
        EclipsePlatformComponent platformComponent,
        SubcomponentRegistry subcomponentRegistry, ComponentDependencyResolver dependencyResolver) {
        return null;
    }

    @Override
    public TaskDefsProvider getTaskDefsProvider(
        EclipseLoggerComponent loggerComponent,
        EclipseResourceServiceComponent baseResourceServiceComponent,
        ResourceServiceComponent resourceServiceComponent,
        EclipsePlatformComponent platformComponent,
        SubcomponentRegistry subcomponentRegistry, ComponentDependencyResolver dependencyResolver) {
        this.resourceServiceComponent = resourceServiceComponent;
        return () -> {
            // Inside closure so that it is lazily initialized -> meta-language instances should be available.
            if(spoofax3Compiler == null) {
                final TemplateCompiler templateCompiler = new TemplateCompiler(StandardCharsets.UTF_8);
                final SpoofaxCompilerComponent spoofaxCompilerComponent = DaggerSpoofaxCompilerComponent.builder()
                    .spoofaxCompilerModule(new SpoofaxCompilerModule(templateCompiler))
                    .loggerComponent(loggerComponent)
                    .resourceServiceComponent(resourceServiceComponent)
                    .build();

                final CfgComponent cfgComponent = CfgLanguageFactory.getLanguage().getComponent();
                final Sdf3Component sdf3Component = Sdf3LanguageFactory.getLanguage().getComponent();
                final StrategoEclipseComponent strategoComponent = StrategoLanguageFactory.getLanguage().getComponent();
                final EsvEclipseComponent esvComponent = EsvLanguageFactory.getLanguage().getComponent();
                final StatixEclipseComponent statixComponent = StatixLanguageFactory.getLanguage().getComponent();

                final Sdf3ExtStatixComponent sdf3ExtStatixComponent = Sdf3ExtStatixLanguageFactory.getLanguage().getComponent();

                final StrategoLibComponent strategoLibComponent = StrategoLibLanguageFactory.getLanguage().getComponent();
                final StrategoLibResourcesComponent strategoLibResourcesComponent = StrategoLibLanguageFactory.getLanguage().getResourcesComponent();
                final GppComponent gppComponent = GppLanguageFactory.getLanguage().getComponent();
                final GppResourcesComponent gppResourcesComponent = GppLanguageFactory.getLanguage().getResourcesComponent();
                final LibSpoofax2EclipseComponent libSpoofax2Component = LibSpoofax2LanguageFactory.getLanguage().getComponent();
                final LibSpoofax2ResourcesComponent libSpoofax2ResourcesComponent = LibSpoofax2LanguageFactory.getLanguage().getResourcesComponent();
                final LibStatixEclipseComponent libStatixComponent = LibStatixLanguageFactory.getLanguage().getComponent();
                final LibStatixResourcesComponent libStatixResourcesComponent = LibStatixLanguageFactory.getLanguage().getResourcesComponent();

                final EclipseSpoofax3CompilerComponent component = DaggerEclipseSpoofax3CompilerComponent.builder()
                    .spoofax3CompilerModule(new Spoofax3CompilerModule(templateCompiler))
                    .loggerComponent(loggerComponent)
                    .resourceServiceComponent(resourceServiceComponent)
                    .cfgComponent(cfgComponent)
                    .sdf3Component(sdf3Component)
                    .strategoComponent(strategoComponent)
                    .esvComponent(esvComponent)
                    .statixComponent(statixComponent)

                    .sdf3ExtStatixComponent(sdf3ExtStatixComponent)

                    .strategoLibComponent(strategoLibComponent)
                    .strategoLibResourcesComponent(strategoLibResourcesComponent)
                    .gppComponent(gppComponent)
                    .gppResourcesComponent(gppResourcesComponent)
                    .libSpoofax2Component(libSpoofax2Component)
                    .libSpoofax2ResourcesComponent(libSpoofax2ResourcesComponent)
                    .libStatixComponent(libStatixComponent)
                    .libStatixResourcesComponent(libStatixResourcesComponent)
                    .build();

                spoofax3Compiler = new Spoofax3Compiler(
                    loggerComponent,
                    resourceServiceComponent,
                    platformComponent,

                    cfgComponent,
                    sdf3Component,
                    strategoComponent,
                    esvComponent,
                    statixComponent,
                    sdf3ExtStatixComponent,

                    strategoLibComponent,
                    strategoLibResourcesComponent,
                    gppComponent,
                    gppResourcesComponent,
                    libSpoofax2Component,
                    libSpoofax2ResourcesComponent,
                    libStatixComponent,
                    libStatixResourcesComponent,

                    templateCompiler,
                    spoofaxCompilerComponent,
                    component
                );
            }
            if(dynamicLoadingComponent == null) {
                dynamicLoadingComponent = DaggerEclipseDynamicLoadingComponent.builder()
                    .dynamicLoadingPieModule(new DynamicLoadingPieModule(() -> new RootPieModule(PieBuilderImpl::new)))
                    .loggerComponent(loggerComponent)
                    .resourceServiceComponent(resourceServiceComponent)
                    .platformComponent(platformComponent)
                    .cfgComponent(CfgLanguageFactory.getLanguage().getComponent())
                    .spoofax3CompilerComponent(spoofax3Compiler.component)
                    .build();
            }
            SptLanguageFactory.getLanguage().getComponent().getLanguageUnderTestProviderWrapper().set(new DynamicLanguageUnderTestProvider(
                SpoofaxLwbParticipant.getInstance().getDynamicLoadingComponent().getDynamicComponentManager(),
                SpoofaxLwbParticipant.getInstance().getDynamicLoadingComponent().getDynamicLoad(),
                spoofax3Compiler.component.getCompileLanguage(),
                rootDirectory -> {
                    // TODO: reduce code duplication with SpoofaxLwbBuilder
                    return CompileLanguage.Args.builder()
                        .rootDirectory(rootDirectory)
                        .addJavaClassPathSuppliers(ClassPathUtil.getClassPathSupplier())
                        .addJavaAnnotationProcessorPathSuppliers(ClassPathUtil.getClassPathSupplier())
                        .build();
                }
            ));
            if(spoofaxLwbComponent == null) {
                spoofaxLwbComponent = DaggerSpoofaxLwbComponent.builder()
                    .loggerComponent(loggerComponent)
                    .resourceServiceComponent(resourceServiceComponent)
                    .dynamicLoadingComponent(dynamicLoadingComponent)
                    .build();
            }
            final HashSet<TaskDef<?, ?>> taskDefs = new HashSet<>();
            taskDefs.addAll(spoofax3Compiler.component.getTaskDefs());
            taskDefs.addAll(dynamicLoadingComponent.getTaskDefs());
            return taskDefs;
        };
    }

    @Override
    public @Nullable EclipseLanguageComponent getLanguageComponent(
        EclipseLoggerComponent loggerComponent,
        EclipseResourceServiceComponent baseResourceServiceComponent,
        ResourceServiceComponent resourceServiceComponent,
        EclipsePlatformComponent platformComponent,
        SubcomponentRegistry subcomponentRegistry, ComponentDependencyResolver dependencyResolver) {
        return null;
    }

    @Override public @Nullable Consumer<RootPieModule> getPieModuleCustomizer() {
        return pieModule -> pieModule
            // Use Fst Serde implementation for better serialization performance.
            .withSerdeFactory((loggerFactory) -> new FstSerde())
            // Use logging tracer to create build logs.
            .withTracerFactory(LoggingTracer::new)
            // Override store factory for statically loaded Spoofax LWB languages, so their store gets serialized to
            // the `pieStore` file.
            .withStoreFactory((serde, resourceService, loggerFactory) -> {
                final IPath statePath = SpoofaxLwbPlugin.getPlugin().getStateLocation();
                final FSResource stateDir = ResourceUtil.toFsResource(statePath);
                return SerializingStoreBuilder.ofInMemoryStore(serde)
                    .withResourceStorage(stateDir.appendRelativePath("pieStore"))
                    .withLoggingDeserializeFailHandler(loggerFactory)
                    .build();
            });
    }

    @Override
    public void start(
        EclipseLoggerComponent loggerComponent,
        EclipseResourceServiceComponent baseResourceServiceComponent, ResourceServiceComponent resourceServiceComponent,
        EclipsePlatformComponent platformComponent,
        PieComponent pieComponent,
        ComponentDependencyResolver dependencyResolver) {
        this.pieComponent = pieComponent;
        spoofaxLwbComponent.getDynamicChangeProcessor().register();
        spoofaxLwbComponent.getDynamicEditorTracker().register();
    }

    @Override
    public void close() {
        pieComponent = null;
        spoofaxLwbComponent.close();
        spoofaxLwbComponent = null;
        dynamicLoadingComponent.close();
        dynamicLoadingComponent = null;
        spoofax3Compiler.close();
        spoofax3Compiler = null;
        resourceServiceComponent = null;
    }
}
