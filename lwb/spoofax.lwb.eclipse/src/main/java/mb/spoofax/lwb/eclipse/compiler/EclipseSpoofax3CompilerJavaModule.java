package mb.spoofax.lwb.eclipse.compiler;

import dagger.Module;
import dagger.Provides;
import mb.pie.task.java.CompileJava;
import mb.pie.task.java.jdk.FileManagerFactory;
import mb.pie.task.java.jdk.JavaFileObjectFactory;
import mb.pie.task.java.jdk.JavaResourceManager;
import mb.resource.ResourceService;
import mb.resource.hierarchical.HierarchicalResource;
import mb.spoofax.lwb.compiler.dagger.Spoofax3CompilerScope;

import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import java.util.ArrayList;

@Module
public abstract class EclipseSpoofax3CompilerJavaModule {
    @Provides @Spoofax3CompilerScope
    static CompileJava provideCompileJava() {
        //return new CompileJava(new EclipseCompiler(), new EclipseFileManagerFactory(), new EclipseJavaFileObjectFactory());
        // For now, do not use ECJ, as the Dagger code generated by ECJ in Eclipse somehow does not work correctly.
        return new CompileJava();
    }

    private static class EclipseFileManagerFactory implements FileManagerFactory {
        @Override
        public JavaFileManager create(
            StandardJavaFileManager fileManager,
            JavaFileObjectFactory javaFileObjectFactory,
            ResourceService resourceService,
            ArrayList<HierarchicalResource> sourcePath,
            HierarchicalResource sourceFileOutputDir,
            HierarchicalResource classFileOutputDir
        ) {
            return new JavaResourceManager(fileManager, javaFileObjectFactory, resourceService, sourcePath, sourceFileOutputDir, classFileOutputDir);
        }
    }

    private static class EclipseJavaFileObjectFactory implements JavaFileObjectFactory {
        @Override public JavaFileObject create(HierarchicalResource resource) {
            return new EclipseJavaResource(resource);
        }

        @Override public JavaFileObject create(HierarchicalResource resource, JavaFileObject.Kind kind) {
            return new EclipseJavaResource(resource, kind);
        }
    }
}
