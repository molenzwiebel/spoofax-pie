package mb.spoofax.lwb.compiler;

import mb.common.message.KeyedMessages;
import mb.common.message.Message;
import mb.common.util.ExceptionPrinter;
import mb.log.dagger.DaggerLoggerComponent;
import mb.log.dagger.LoggerComponent;
import mb.log.dagger.LoggerModule;
import mb.pie.api.ExecException;
import mb.pie.api.MixedSession;
import mb.pie.dagger.PieComponent;
import mb.pie.runtime.PieBuilderImpl;
import mb.pie.runtime.store.SerializingStoreBuilder;
import mb.pie.serde.fst.FstSerde;
import mb.pie.task.archive.UnarchiveCommon;
import mb.resource.ResourceService;
import mb.resource.WritableResource;
import mb.resource.classloader.ClassLoaderResource;
import mb.resource.classloader.ClassLoaderResourceLocations;
import mb.resource.classloader.ClassLoaderResourceRegistry;
import mb.resource.classloader.JarFileWithPath;
import mb.resource.dagger.DaggerRootResourceServiceComponent;
import mb.resource.dagger.ResourceServiceComponent;
import mb.resource.dagger.RootResourceServiceComponent;
import mb.resource.dagger.RootResourceServiceModule;
import mb.resource.fs.FSResource;
import mb.resource.hierarchical.HierarchicalResource;
import mb.spoofax.core.component.StaticComponentBuilder;
import mb.spoofax.core.platform.DaggerPlatformComponent;
import mb.spoofax.core.platform.PlatformComponent;
import mb.spoofax.lwb.compiler.dagger.Spoofax3CompilerComponent;
import mb.spoofax.lwb.compiler.dagger.StandaloneSpoofax3Compiler;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestBase {
    final ClassLoaderResourceRegistry classLoaderResourceRegistry =
        new ClassLoaderResourceRegistry("language-compiler", CompileLanguageTest.class.getClassLoader());

    HierarchicalResource tempDirectory;
    HierarchicalResource rootDirectory;
    ExceptionPrinter exceptionPrinter;
    LoggerComponent loggerComponent;
    RootResourceServiceComponent baseResourceServiceComponent;
    PlatformComponent platformComponent;
    StandaloneSpoofax3Compiler compiler;
    ResourceService resourceService;
    PieComponent pieComponent;
    CheckLanguageSpecification checkLanguageSpecification;
    CompileLanguage compileLanguage;

    void setup(Path temporaryDirectoryPath) throws IOException {
        tempDirectory = new FSResource(temporaryDirectoryPath);
        rootDirectory = tempDirectory.appendRelativePath("language").ensureDirectoryExists();
        exceptionPrinter = new ExceptionPrinter().addCurrentDirectoryContext(rootDirectory);
        loggerComponent = DaggerLoggerComponent.builder()
            .loggerModule(LoggerModule.stdOutVeryVerbose())
            .build();
        baseResourceServiceComponent = DaggerRootResourceServiceComponent.builder()
            .rootResourceServiceModule(new RootResourceServiceModule(classLoaderResourceRegistry))
            .loggerComponent(loggerComponent)
            .build();
        platformComponent = DaggerPlatformComponent.builder()
            .loggerComponent(loggerComponent)
            .resourceServiceComponent(baseResourceServiceComponent)
            .build();
        recreateCompiler();
    }

    void teardown() throws Exception {
        compileLanguage = null;
        pieComponent.close();
        pieComponent = null;
        resourceService = null;
        compiler.close();
        compiler = null;
        platformComponent.close();
        platformComponent = null;
        baseResourceServiceComponent.close();
        baseResourceServiceComponent = null;
        loggerComponent = null;
        exceptionPrinter = null;
        rootDirectory.close();
        rootDirectory = null;
    }

    void copyResourcesToTemporaryDirectory(String sourceFilesPath) throws IOException {
        final ClassLoaderResource sourceFilesDirectory = classLoaderResourceRegistry.getResource(sourceFilesPath);
        final ClassLoaderResourceLocations<FSResource> locations = sourceFilesDirectory.getLocations();
        for(FSResource directory : locations.directories) {
            directory.copyRecursivelyTo(rootDirectory);
        }
        for(JarFileWithPath<FSResource> jarFileWithPath : locations.jarFiles) {
            UnarchiveCommon.unarchiveJar(jarFileWithPath.file, rootDirectory, false, false);
        }
    }

    void checkAndCompileLanguage() throws CompileLanguageException, ExecException, InterruptedException {
        final CompileLanguage.Args args = CompileLanguage.Args.builder()
            .rootDirectory(rootDirectory.getPath())
            .build();
        try(final MixedSession session = pieComponent.getPie().newSession()) {
            final KeyedMessages messages = session.require(checkLanguageSpecification.createTask(rootDirectory.getPath()));
            assertNoErrors(messages);
            session.require(compileLanguage.createTask(args)).unwrap();
        } catch(CompileLanguageException e) {
            System.err.println(exceptionPrinter.printExceptionToString(e));
            throw e;
        }
    }

    void recreateCompiler() throws IOException {
        compileLanguage = null;
        checkLanguageSpecification = null;
        pieComponent = null;
        resourceService = null;
        if(compiler != null) {
            compiler.close();
            compiler = null;
        }

        final WritableResource pieStoreFile = tempDirectory.appendRelativePath(".build/compiler.piestore").createParents();
        final StaticComponentBuilder<LoggerComponent, ResourceServiceComponent, PlatformComponent> builder = new StaticComponentBuilder<>(
            loggerComponent,
            baseResourceServiceComponent,
            platformComponent,
            PieBuilderImpl::new
        );
        builder.registerPieModuleCustomizer(pieModule -> {
            pieModule.withSerdeFactory((loggerFactory -> new FstSerde()));
            pieModule.withStoreFactory((serde, resourceService, loggerFactory) -> SerializingStoreBuilder.ofInMemoryStore(serde)
                .withResourceStorage(pieStoreFile)
                .build()
            );
        });
        compiler = new StandaloneSpoofax3Compiler(builder);
        final Spoofax3CompilerComponent spoofax3CompilerComponent = compiler.spoofax3CompilerComponent;
        resourceService = spoofax3CompilerComponent.getResourceServiceComponent().getResourceService();
        pieComponent = compiler.pieComponent;
        compileLanguage = spoofax3CompilerComponent.getCompileLanguage();
        checkLanguageSpecification = spoofax3CompilerComponent.getCheckLanguageSpecification();
    }

    void assertNoErrors(KeyedMessages messages) {
        assertNoErrors(messages, "no errors, but found errors");
    }

    void assertNoErrors(KeyedMessages messages, String failure) {
        assertFalse(messages.containsError(), () -> "Expected " + failure + ".\n" + exceptionPrinter.printMessagesToString(messages.filter(Message::isErrorOrHigher)));
    }
}
