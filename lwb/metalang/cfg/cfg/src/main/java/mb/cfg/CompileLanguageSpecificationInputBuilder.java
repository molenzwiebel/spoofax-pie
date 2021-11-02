package mb.cfg;

import mb.cfg.metalang.CfgEsvConfig;
import mb.cfg.metalang.CfgSdf3Config;
import mb.cfg.metalang.CfgStatixConfig;
import mb.cfg.metalang.CompileStrategoInput;
import mb.common.util.Properties;
import mb.spoofax.compiler.util.Shared;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Facade for consistently building a {@link CompileLanguageSpecificationInput} instance.
 */
public class CompileLanguageSpecificationInputBuilder {
    private boolean sdf3Enabled = false;
    public CfgSdf3Config.Builder sdf3 = CfgSdf3Config.builder();

    private boolean esvEnabled = false;
    public CfgEsvConfig.Builder esv = CfgEsvConfig.builder();

    private boolean statixEnabled = false;
    public CfgStatixConfig.Builder statix = CfgStatixConfig.builder();

    private boolean strategoEnabled = false;
    public CompileStrategoInput.Builder stratego = CompileStrategoInput.builder();

    public CompileLanguageSpecificationInput.Builder compileLanguage = CompileLanguageSpecificationInput.builder();


    public CfgSdf3Config.Builder withSdf3() {
        sdf3Enabled = true;
        return sdf3;
    }

    public CfgEsvConfig.Builder withEsv() {
        esvEnabled = true;
        return esv;
    }

    public CfgStatixConfig.Builder withStatix() {
        statixEnabled = true;
        return statix;
    }

    public CompileStrategoInput.Builder withStratego() {
        strategoEnabled = true;
        return stratego;
    }


    public CompileLanguageSpecificationInput build(Properties persistentProperties, Shared shared, CompileLanguageSpecificationShared compileLanguageSpecificationShared) {
        final @Nullable CfgSdf3Config sdf3 = buildSdf3(compileLanguageSpecificationShared);
        if(sdf3 != null) compileLanguage.sdf3(sdf3);

        final @Nullable CfgEsvConfig esv = buildEsv(compileLanguageSpecificationShared);
        if(esv != null) compileLanguage.esv(esv);

        final @Nullable CfgStatixConfig statix = buildStatix(compileLanguageSpecificationShared);
        if(statix != null) compileLanguage.statix(statix);

        final @Nullable CompileStrategoInput stratego = buildStratego(persistentProperties, shared, compileLanguageSpecificationShared);
        if(stratego != null) compileLanguage.stratego(stratego);

        return compileLanguage
            .compileLanguageShared(compileLanguageSpecificationShared)
            .build();
    }


    private @Nullable CfgSdf3Config buildSdf3(
        CompileLanguageSpecificationShared compileLanguageSpecificationShared
    ) {
        if(!sdf3Enabled) return null;
        return sdf3
            .compileLanguageShared(compileLanguageSpecificationShared)
            .build();
    }

    private @Nullable CfgEsvConfig buildEsv(
        CompileLanguageSpecificationShared compileLanguageSpecificationShared
    ) {
        if(!esvEnabled) return null;
        return esv
            .compileLanguageShared(compileLanguageSpecificationShared)
            .build();
    }

    private @Nullable CfgStatixConfig buildStatix(
        CompileLanguageSpecificationShared compileLanguageSpecificationShared
    ) {
        if(!statixEnabled) return null;
        return statix
            .compileLanguageShared(compileLanguageSpecificationShared)
            .build();
    }

    private @Nullable CompileStrategoInput buildStratego(
        Properties persistentProperties,
        Shared shared,
        CompileLanguageSpecificationShared compileLanguageSpecificationShared
    ) {
        if(!strategoEnabled) return null;
        return stratego
            .withPersistentProperties(persistentProperties)
            .shared(shared)
            .compileLanguageShared(compileLanguageSpecificationShared)
            .build();
    }
}
