package mb.spoofax.lwb.compiler.esv;

import mb.cfg.metalang.CfgEsvConfig;
import mb.cfg.metalang.CfgEsvSource;
import mb.cfg.task.CfgRootDirectoryToObject;
import mb.cfg.task.CfgRootDirectoryToObjectException;
import mb.cfg.task.CfgToObject;
import mb.common.option.Option;
import mb.common.result.Result;
import mb.common.util.ListView;
import mb.esv.task.EsvConfig;
import mb.libspoofax2.LibSpoofax2ClassLoaderResources;
import mb.libspoofax2.LibSpoofax2ResourceExports;
import mb.pie.api.ExecContext;
import mb.pie.api.Interactivity;
import mb.pie.api.STask;
import mb.pie.api.SerializableFunction;
import mb.pie.api.StatelessSerializableFunction;
import mb.pie.api.Supplier;
import mb.pie.api.TaskDef;
import mb.pie.api.ValueSupplier;
import mb.pie.api.exec.UncheckedInterruptedException;
import mb.pie.api.stamp.resource.ResourceStampers;
import mb.pie.task.archive.UnarchiveFromJar;
import mb.resource.classloader.ClassLoaderResourceLocations;
import mb.resource.classloader.JarFileWithPath;
import mb.resource.fs.FSResource;
import mb.resource.hierarchical.HierarchicalResource;
import mb.resource.hierarchical.ResourcePath;
import mb.resource.hierarchical.match.path.string.PathStringMatcher;
import mb.sdf3.task.Sdf3ToCompletionColorer;
import mb.spoofax.core.language.Export;
import mb.spoofax.lwb.compiler.sdf3.SpoofaxSdf3ConfigureException;
import mb.spoofax.lwb.compiler.sdf3.SpoofaxSdf3GenerationUtil;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spoofax.interpreter.terms.IStrategoTerm;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Configuration task for ESV in the context of the Spoofax LWB compiler.
 */
public class SpoofaxEsvConfigure implements TaskDef<ResourcePath, Result<Option<SpoofaxEsvConfig>, SpoofaxEsvConfigureException>> {
    private final CfgRootDirectoryToObject cfgRootDirectoryToObject;

    private final UnarchiveFromJar unarchiveFromJar;
    private final LibSpoofax2ResourceExports libSpoofax2ResourceExports;
    private final LibSpoofax2ClassLoaderResources libSpoofax2ClassLoaderResources;

    private final SpoofaxSdf3GenerationUtil spoofaxSdf3GenerationUtil;
    private final Sdf3ToCompletionColorer sdf3ToCompletionColorer;


    @Inject public SpoofaxEsvConfigure(
        CfgRootDirectoryToObject cfgRootDirectoryToObject,

        UnarchiveFromJar unarchiveFromJar,
        LibSpoofax2ResourceExports libSpoofax2ResourceExports,
        LibSpoofax2ClassLoaderResources libSpoofax2ClassLoaderResources,

        SpoofaxSdf3GenerationUtil spoofaxSdf3GenerationUtil,
        Sdf3ToCompletionColorer sdf3ToCompletionColorer
    ) {
        this.cfgRootDirectoryToObject = cfgRootDirectoryToObject;

        this.unarchiveFromJar = unarchiveFromJar;
        this.libSpoofax2ResourceExports = libSpoofax2ResourceExports;
        this.libSpoofax2ClassLoaderResources = libSpoofax2ClassLoaderResources;

        this.spoofaxSdf3GenerationUtil = spoofaxSdf3GenerationUtil;
        this.sdf3ToCompletionColorer = sdf3ToCompletionColorer;
    }


    @Override public String getId() {
        return getClass().getName();
    }

    @Override
    public Result<Option<SpoofaxEsvConfig>, SpoofaxEsvConfigureException> exec(ExecContext context, ResourcePath rootDirectory) throws IOException {
        return context.requireMapping(cfgRootDirectoryToObject, rootDirectory, new EsvConfigMapper())
            .mapErr(SpoofaxEsvConfigureException::getLanguageCompilerConfigurationFail)
            .<Option<SpoofaxEsvConfig>, IOException>flatMapThrowing(o -> Result.transpose(o.mapThrowing(cfgEsvConfig -> configure(context, rootDirectory, cfgEsvConfig))));
    }

    @Override public boolean shouldExecWhenAffected(ResourcePath input, Set<?> tags) {
        return tags.isEmpty() || tags.contains(Interactivity.NonInteractive);
    }

    public Result<SpoofaxEsvConfig, SpoofaxEsvConfigureException> configure(
        ExecContext context,
        ResourcePath rootDirectory,
        CfgEsvConfig cfgEsvConfig
    ) throws IOException {
        try {
            return cfgEsvConfig.source().caseOf()
                .files(files -> configureSourcesCatching(
                    context,
                    rootDirectory,
                    cfgEsvConfig,
                    files
                ))
                .prebuilt(esvAtermFile -> configurePreBuilt(esvAtermFile, cfgEsvConfig))
                ;
        } catch(UncheckedIOException e) {
            throw e.getCause();
        } // No need to unwrap UncheckedInterruptedException here, PIE handles UncheckedInterruptedException.
    }

    public Result<SpoofaxEsvConfig, SpoofaxEsvConfigureException> configureSourcesCatching(
        ExecContext context,
        ResourcePath rootDirectory,
        CfgEsvConfig cfgEsvConfig,
        CfgEsvSource.Files files
    ) {
        try {
            return configureSourceFiles(context, rootDirectory, cfgEsvConfig, files);
        } catch(IOException e) {
            throw new UncheckedIOException(e);
        } catch(InterruptedException e) {
            throw new UncheckedInterruptedException(e);
        }
    }

    public Result<SpoofaxEsvConfig, SpoofaxEsvConfigureException> configureSourceFiles(
        ExecContext context,
        ResourcePath rootDirectory,
        CfgEsvConfig cfgEsvConfig,
        CfgEsvSource.Files files
    ) throws IOException, InterruptedException {
        // Check main source directory, main file, and include directories.
        final HierarchicalResource mainSourceDirectory = context.require(files.mainSourceDirectory(), ResourceStampers.<HierarchicalResource>exists());
        if(!mainSourceDirectory.exists() || !mainSourceDirectory.isDirectory()) {
            return Result.ofErr(SpoofaxEsvConfigureException.mainSourceDirectoryFail(mainSourceDirectory.getPath()));
        }
        final HierarchicalResource mainFile = context.require(files.mainFile(), ResourceStampers.<HierarchicalResource>exists());
        if(!mainFile.exists() || !mainFile.isFile()) {
            return Result.ofErr(SpoofaxEsvConfigureException.mainFileFail(mainFile.getPath()));
        }
        for(ResourcePath includeDirectoryPath : files.includeDirectories()) {
            final HierarchicalResource includeDirectory = context.require(includeDirectoryPath, ResourceStampers.<HierarchicalResource>exists());
            if(!includeDirectory.exists() || !includeDirectory.isDirectory()) {
                return Result.ofErr(SpoofaxEsvConfigureException.includeDirectoryFail(includeDirectoryPath));
            }
        }

        // Unarchive ESV files from libspoofax2.
        final LinkedHashSet<HierarchicalResource> libSpoofax2DefinitionDirs = new LinkedHashSet<>(); // LinkedHashSet to remove duplicates while keeping insertion order.
        final LinkedHashSet<Supplier<ResourcePath>> libSpoofax2UnarchiveDirSuppliers = new LinkedHashSet<>();
        if(files.includeLibSpoofax2Exports()) {
            final ClassLoaderResourceLocations<FSResource> locations = libSpoofax2ClassLoaderResources.definitionDirectory.getLocations();
            libSpoofax2DefinitionDirs.addAll(locations.directories);
            for(JarFileWithPath<FSResource> jarFileWithPath : locations.jarFiles) {
                final ResourcePath jarFilePath = jarFileWithPath.file.getPath();
                @SuppressWarnings("ConstantConditions") // JAR files always have leaves.
                final ResourcePath unarchiveDirectory = files.libSpoofax2UnarchiveDirectory().appendRelativePath(jarFilePath.getLeaf());
                libSpoofax2UnarchiveDirSuppliers.add(unarchiveFromJar
                    .createSupplier(new UnarchiveFromJar.Input(jarFilePath, unarchiveDirectory, PathStringMatcher.ofExtension("esv"), false, false))
                    .map(new AppendPath(jarFileWithPath.path))
                );
            }
        }

        // Gather source file origins and include directories.
        final LinkedHashSet<Supplier<?>> sourceFileOrigins = new LinkedHashSet<>();
        final LinkedHashSet<Supplier<Result<ResourcePath, ?>>> includeDirectorySuppliers = new LinkedHashSet<>();
        // Add main source directory as an include for resolving imports.
        includeDirectorySuppliers.add(new ValueSupplier<>(Result.ofOk(files.mainSourceDirectory())));
        for(ResourcePath includeDirectory : files.includeDirectories()) {
            includeDirectorySuppliers.add(new ValueSupplier<>(Result.ofOk(includeDirectory)));
        }
        for(Export export : libSpoofax2ResourceExports.esvExports) {
            final String directoryRelativePath = export.caseOf().directory(path -> path).otherwiseEmpty()
                .orElseThrow(() -> new IllegalStateException("libspoofax2 Stratego export '" + export + "' is not a directory export. Only directory exports are supported"));
            for(HierarchicalResource definitionDir : libSpoofax2DefinitionDirs) {
                final HierarchicalResource exportDirectory = definitionDir.appendAsRelativePath(directoryRelativePath);
                if(exportDirectory.exists()) {
                    includeDirectorySuppliers.add(new ValueSupplier<>(Result.ofOk(exportDirectory.getPath())));
                }
            }
            for(Supplier<ResourcePath> unarchiveDirSupplier : libSpoofax2UnarchiveDirSuppliers) {
                final Supplier<Result<ResourcePath, ?>> supplier = unarchiveDirSupplier.map(new AppendPath(directoryRelativePath)).map(MakeOk.instance);
                includeDirectorySuppliers.add(supplier);
                sourceFileOrigins.add(supplier);
            }
        }

        final ArrayList<Supplier<Result<IStrategoTerm, ?>>> includeAstSuppliers = new ArrayList<>();

        // Compile each SDF3 source file (if SDF3 is enabled) to a completion colorer.
        try {
            spoofaxSdf3GenerationUtil.performSdf3GenerationIfEnabled(context, rootDirectory, new SpoofaxSdf3GenerationUtil.Callbacks<SpoofaxEsvConfigureException>() {
                @Override
                public void generateFromAst(ExecContext context, STask<Result<IStrategoTerm, ?>> astSupplier) {
                    includeAstSuppliers.add(sdf3ToCompletionColorer.createSupplier(astSupplier));
                }
            });
        } catch(SpoofaxEsvConfigureException e) {
            return Result.ofErr(e);
        } catch(SpoofaxSdf3ConfigureException e) {
            return Result.ofErr(SpoofaxEsvConfigureException.sdf3ConfigureFail(e));
        }

        final EsvConfig config = new EsvConfig(rootDirectory, files.mainFile(), ListView.copyOf(sourceFileOrigins), ListView.copyOf(includeDirectorySuppliers), ListView.of(includeAstSuppliers));
        return Result.ofOk(SpoofaxEsvConfig.files(config, cfgEsvConfig.outputFile()));
    }

    public Result<SpoofaxEsvConfig, SpoofaxEsvConfigureException> configurePreBuilt(ResourcePath inputFile, CfgEsvConfig cfgEsvConfig) {
        return Result.ofOk(SpoofaxEsvConfig.prebuilt(inputFile, cfgEsvConfig.outputFile()));
    }


    private static class AppendPath implements SerializableFunction<ResourcePath, ResourcePath> {
        private final String path;

        private AppendPath(String path) {
            this.path = path;
        }

        @Override public ResourcePath apply(ResourcePath dir) {
            return dir.appendAsRelativePath(path);
        }

        @Override public boolean equals(@Nullable Object o) {
            if(this == o) return true;
            if(o == null || getClass() != o.getClass()) return false;
            final AppendPath that = (AppendPath)o;
            return path.equals(that.path);
        }

        @Override public int hashCode() {
            return path.hashCode();
        }

        @Override public String toString() {
            return "AppendPath{" +
                "path='" + path + '\'' +
                '}';
        }
    }

    private static class MakeOk implements SerializableFunction<ResourcePath, Result<ResourcePath, ?>> {
        private static final MakeOk instance = new MakeOk();

        private MakeOk() {}

        @Override public Result<ResourcePath, ?> apply(ResourcePath dir) {
            return Result.ofOk(dir);
        }

        @Override public boolean equals(@Nullable Object other) {
            return this == other || other != null && this.getClass() == other.getClass();
        }

        @Override public int hashCode() {return 0;}

        @Override public String toString() {return "MakeOk()";}

        private Object readResolve() {return instance;}
    }

    private static class EsvConfigMapper extends StatelessSerializableFunction<Result<CfgToObject.Output, CfgRootDirectoryToObjectException>, Result<Option<CfgEsvConfig>, CfgRootDirectoryToObjectException>> {
        @Override
        public Result<Option<CfgEsvConfig>, CfgRootDirectoryToObjectException> apply(Result<CfgToObject.Output, CfgRootDirectoryToObjectException> result) {
            return result.map(o -> Option.ofOptional(o.compileLanguageInput.compileLanguageSpecificationInput().esv()));
        }
    }
}
