package mb.tiger.spoofax.taskdef;

import mb.constraint.common.ConstraintAnalyzer.SingleFileResult;
import mb.constraint.common.ConstraintAnalyzerContext;
import mb.constraint.common.ConstraintAnalyzerException;
import mb.log.api.LoggerFactory;
import mb.pie.api.ExecContext;
import mb.pie.api.ExecException;
import mb.pie.api.Provider;
import mb.pie.api.TaskDef;
import mb.resource.ResourceKey;
import mb.resource.ResourceService;
import mb.stratego.common.StrategoIOAgent;
import mb.tiger.TigerConstraintAnalyzer;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spoofax.interpreter.terms.IStrategoTerm;

import javax.inject.Inject;
import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;

public class TigerAnalyze implements TaskDef<TigerAnalyze.Input, @Nullable SingleFileResult> {
    public static class Input implements Serializable {
        public final ResourceKey resourceKey;
        public final Provider<@Nullable IStrategoTerm> astProvider;

        public Input(ResourceKey resourceKey, Provider<@Nullable IStrategoTerm> astProvider) {
            this.resourceKey = resourceKey;
            this.astProvider = astProvider;
        }

        @Override public boolean equals(Object o) {
            if(this == o) return true;
            if(o == null || getClass() != o.getClass()) return false;
            final Input input = (Input)o;
            return resourceKey.equals(input.resourceKey) && astProvider.equals(input.astProvider);
        }

        @Override public int hashCode() {
            return Objects.hash(resourceKey, astProvider);
        }

        @Override public String toString() {
            return "Input(resourceKey=" + resourceKey + ", astProvider=" + astProvider + ')';
        }
    }

    private final TigerConstraintAnalyzer constraintAnalyzer;
    private final LoggerFactory loggerFactory;
    private final ResourceService resourceService;

    @Inject
    public TigerAnalyze(TigerConstraintAnalyzer constraintAnalyzer, LoggerFactory loggerFactory, ResourceService resourceService) {
        this.constraintAnalyzer = constraintAnalyzer;
        this.loggerFactory = loggerFactory;
        this.resourceService = resourceService;
    }

    @Override public String getId() {
        return getClass().getName();
    }

    @Override
    public @Nullable SingleFileResult exec(ExecContext context, Input input) throws ExecException, IOException, InterruptedException {
        final @Nullable IStrategoTerm ast = context.require(input.astProvider);
        if(ast == null) {
            return null;
        }

        try {
            return constraintAnalyzer.analyze(input.resourceKey, ast, new ConstraintAnalyzerContext(), new StrategoIOAgent(loggerFactory, resourceService));
        } catch(ConstraintAnalyzerException e) {
            throw new RuntimeException("Constraint analysis failed unexpectedly", e);
        }
    }
}
