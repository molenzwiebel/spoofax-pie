package mb.spoofax.compiler.spoofaxcore;

import mb.common.util.ListView;
import mb.resource.hierarchical.ResourcePath;
import mb.spoofax.compiler.util.ClassKind;
import mb.spoofax.compiler.util.GradleConfiguredDependency;
import mb.spoofax.compiler.util.TemplateCompiler;
import mb.spoofax.compiler.util.TemplateWriter;
import mb.spoofax.compiler.util.TypeInfo;
import org.immutables.value.Value;

import java.io.IOException;
import java.io.Serializable;
import java.util.Optional;

@Value.Enclosing
public class Styler {
    private final TemplateWriter rulesTemplate;
    private final TemplateWriter stylerTemplate;
    private final TemplateWriter factoryTemplate;
    private final TemplateWriter styleTaskDefTemplate;

    public Styler(TemplateCompiler templateCompiler) {
        this.rulesTemplate = templateCompiler.getOrCompileToWriter("styler/StylingRules.java.mustache");
        this.stylerTemplate = templateCompiler.getOrCompileToWriter("styler/Styler.java.mustache");
        this.factoryTemplate = templateCompiler.getOrCompileToWriter("styler/StylerFactory.java.mustache");
        this.styleTaskDefTemplate = templateCompiler.getOrCompileToWriter("styler/StyleTaskDef.java.mustache");
    }

    // Language project

    public ListView<GradleConfiguredDependency> getLanguageProjectDependencies(Input input) {
        return ListView.of(GradleConfiguredDependency.api(input.shared().esvCommonDep()));
    }

    public ListView<String> getLanguageProjectCopyResources(Input input) {
        return ListView.of(input.packedESVSourceRelPath());
    }

    public void compileLanguageProject(Input input) throws IOException {
        if(input.classKind().isManualOnly()) return; // Nothing to generate: return.
        final ResourcePath classesGenDirectory = input.languageClassesGenDirectory();
        rulesTemplate.write(input, input.genRules().file(classesGenDirectory));
        stylerTemplate.write(input, input.genStyler().file(classesGenDirectory));
        factoryTemplate.write(input, input.genFactory().file(classesGenDirectory));
    }

    // Adapter project

    public void compileAdapterProject(Input input) throws IOException {
        if(input.classKind().isManualOnly()) return; // Nothing to generate: return.
        final ResourcePath classesGenDirectory = input.adapterClassesGenDirectory();
        styleTaskDefTemplate.write(input, input.genStyleTaskDef().file(classesGenDirectory));
    }

    // Input

    @Value.Immutable
    public interface Input extends Serializable {
        class Builder extends StylerData.Input.Builder {}

        static Builder builder() {
            return new Builder();
        }


        Shared shared();

        Parser.Input parser();


        /// Packed ESV source file (to copy from), and destination file

        @Value.Default default String packedESVSourceRelPath() {
            return "target/metaborg/editor.esv.af";
        }

        @Value.Default default String packedESVTargetRelPath() {
            return shared().languageProject().packagePath() + "/" + packedESVSourceRelPath();
        }


        /// Kinds of classes (generated/extended/manual)

        @Value.Default default ClassKind classKind() {
            return ClassKind.Generated;
        }


        /// Language project classes

        default ResourcePath languageClassesGenDirectory() {
            return shared().languageProject().genSourceSpoofaxJavaDirectory();
        }

        // Styling rules

        @Value.Default default TypeInfo genRules() {
            return TypeInfo.of(shared().languagePackage(), shared().classPrefix() + "StylingRules");
        }

        // Styler

        @Value.Default default TypeInfo genStyler() {
            return TypeInfo.of(shared().languagePackage(), shared().classPrefix() + "Styler");
        }

        Optional<TypeInfo> manualStyler();

        default TypeInfo styler() {
            if(classKind().isManual() && manualStyler().isPresent()) {
                return manualStyler().get();
            }
            return genStyler();
        }

        // Styler factory

        @Value.Default default TypeInfo genFactory() {
            return TypeInfo.of(shared().languagePackage(), shared().classPrefix() + "StylerFactory");
        }

        Optional<TypeInfo> manualFactory();

        default TypeInfo factory() {
            if(classKind().isManual() && manualFactory().isPresent()) {
                return manualFactory().get();
            }
            return genFactory();
        }


        /// Adapter project classes

        @Value.Derived default ResourcePath adapterClassesGenDirectory() {
            return shared().adapterProject().genSourceSpoofaxJavaDirectory();
        }

        // Style task definition

        @Value.Default default TypeInfo genStyleTaskDef() {
            return TypeInfo.of(shared().adapterTaskPackage(), shared().classPrefix() + "Style");
        }

        Optional<TypeInfo> manualStyleTaskDef();

        default TypeInfo styleTaskDef() {
            if(classKind().isManual() && manualStyleTaskDef().isPresent()) {
                return manualStyleTaskDef().get();
            }
            return genStyleTaskDef();
        }


        @Value.Check default void check() {
            final ClassKind kind = classKind();
            final boolean manual = kind.isManual();
            if(!manual) return;
            if(!manualStyler().isPresent()) {
                throw new IllegalArgumentException("Kind '" + kind + "' indicates that a manual class will be used, but 'manualStyler' has not been set");
            }
            if(!manualFactory().isPresent()) {
                throw new IllegalArgumentException("Kind '" + kind + "' indicates that a manual class will be used, but 'manualFactory' has not been set");
            }
            if(!manualStyleTaskDef().isPresent()) {
                throw new IllegalArgumentException("Kind '" + kind + "' indicates that a manual class will be used, but 'manualStyleTaskDef' has not been set");
            }
        }
    }
}
