package mb.statix.task.spoofax;

import mb.common.result.Result;
import mb.jsglr1.common.JSGLR1ParseException;
import mb.jsglr1.common.JSGLR1ParseOutput;
import mb.jsglr1.pie.JSGLR1ParseTaskInput;
import mb.pie.api.ExecContext;
import mb.pie.api.stamp.resource.ResourceStampers;
import mb.statix.StatixClassLoaderResources;
import mb.statix.StatixParser;
import mb.statix.StatixScope;
import mb.statix.task.StatixParse;

import javax.inject.Inject;
import javax.inject.Provider;

@StatixScope
public class StatixParseWrapper extends StatixParse {
    private final StatixClassLoaderResources classLoaderResources;
    private final StatixConfigFunctionWrapper configFunctionWrapper;

    @Inject public StatixParseWrapper(
        StatixClassLoaderResources classLoaderResources,
        Provider<StatixParser> parserProvider,
        StatixConfigFunctionWrapper configFunctionWrapper
    ) {
        super(classLoaderResources, parserProvider);
        this.classLoaderResources = classLoaderResources;
        this.configFunctionWrapper = configFunctionWrapper;
    }

    @Override public String getId() {
        return getClass().getName();
    }

    @Override
    public Result<JSGLR1ParseOutput, JSGLR1ParseException> exec(ExecContext context, JSGLR1ParseTaskInput input) throws Exception {
        context.require(classLoaderResources.tryGetAsLocalResource(getClass()), ResourceStampers.hashFile());
        // TODO: enable when config has source origins.
        // TODO: instead of requiring all origins for each file to parse, only require the origins that corresponds to a certain file.
        //input.rootDirectoryHint().ifPresent(d -> configFunctionWrapper.get().apply(context, d).ifOk(o -> o.ifSome(c -> c.sourceFileOrigins.forEach(context::require))));
        return super.exec(context, input);
    }
}
