package mb.spt.expectation;

import mb.aterm.common.TermToString;
import mb.common.message.KeyedMessages;
import mb.common.message.KeyedMessagesBuilder;
import mb.common.message.Severity;
import mb.common.region.Region;
import mb.common.result.Result;
import mb.pie.api.Session;
import mb.pie.api.exec.CancelToken;
import mb.resource.ResourceKey;
import mb.spoofax.core.language.LanguageInstance;
import mb.spt.api.model.LanguageUnderTest;
import mb.spt.api.model.TestCase;
import mb.spt.api.model.TestExpectation;
import mb.spt.api.parse.TestableParse;
import mb.spt.util.AtermMatcher;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.terms.TermFactory;

public class ParseToAtermExpectation implements TestExpectation {
    private final IStrategoTerm expectedMatch;
    private final Region sourceRegion;

    public ParseToAtermExpectation(IStrategoTerm expectedMatch, Region sourceRegion) {
        this.expectedMatch = expectedMatch;
        this.sourceRegion = sourceRegion;
    }

    @Override
    public KeyedMessages evaluate(LanguageUnderTest languageUnderTest, Session session, CancelToken cancel, TestCase testCase) throws InterruptedException {
        final KeyedMessagesBuilder messagesBuilder = new KeyedMessagesBuilder();
        final ResourceKey file = testCase.testSuiteFile;
        final LanguageInstance languageInstance = languageUnderTest.getLanguageComponent().getLanguageInstance();
        if(!(languageInstance instanceof TestableParse)) {
            messagesBuilder.addMessage("Cannot evaluate parse to ATerm expectation because language instance '" + languageInstance + "' does not implement TestableParse", Severity.Error, file, sourceRegion);
            return messagesBuilder.build(file);
        }
        final TestableParse testableParse = (TestableParse)languageInstance;
        final Result<IStrategoTerm, ?> result = testableParse.testParseToAterm(session, testCase.resource, testCase.rootDirectoryHint);
        result.ifElse(ast -> {
            if(!AtermMatcher.check(ast, expectedMatch, new TermFactory())) {
                messagesBuilder.addMessage("Expected parse to " + AtermMatcher.prettyPrint(expectedMatch) + ", but got " + TermToString.toString(ast), Severity.Error, file, sourceRegion);
            }
        }, e -> {
            messagesBuilder.addMessage("Failed to parse test fragment; see exception", e, Severity.Error, file, sourceRegion);
        });
        return messagesBuilder.build(file);
    }
}
