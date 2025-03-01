package mb.tego.strategies.runtime;

import mb.log.api.Level;
import mb.log.api.Logger;
import mb.log.api.LoggerFactory;
import mb.log.noop.NoopLogger;
import mb.tego.sequences.DebugSeq;
import mb.tego.sequences.Seq;
import mb.tego.strategies.Strategy;
import mb.tego.strategies.Strategy1;
import mb.tego.strategies.Strategy2;
import mb.tego.strategies.Strategy3;
import mb.tego.strategies.StrategyDecl;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.inject.Inject;

/**
 * Implements the {@link TegoRuntime}.
 */
public /* open */ class TegoRuntimeImpl implements TegoRuntime, TegoEngine {

    @Nullable private final LoggerFactory loggerFactory;
    private final Logger log;
    private final Level strategyLogLevel = Level.Trace;
    private int level = 0;

    /**
     * Initializes a new instance of the {@link TegoRuntimeImpl} class.
     *
     * @param loggerFactory the logger factory
     */
    @Inject
    public TegoRuntimeImpl(
        @Nullable LoggerFactory loggerFactory
    ) {
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory != null ? loggerFactory.create(TegoRuntimeImpl.class) : NoopLogger.instance;
    }

    @Override
    public @Nullable Object eval(StrategyDecl strategy, Object[] args, Object input) {
        enterStrategy(strategy);
        final @Nullable Object result = strategy.evalInternal(this, args, input);
        return exitStrategy(strategy, result);
    }

    @Override
    public <T, R> @Nullable R eval(Strategy<T, R> strategy, T input) {
        enterStrategy(strategy);
        final @Nullable R result = strategy.evalInternal(this, input);
        return exitStrategy(strategy, result);
    }

    @Override
    public <A1, T, R> @Nullable R eval(Strategy1<A1, T, R> strategy, A1 arg1, T input) {
        enterStrategy(strategy);
        final @Nullable R result = strategy.evalInternal(this, arg1, input);
        return exitStrategy(strategy, result);
    }

    @Override
    public <A1, A2, T, R> @Nullable R eval(Strategy2<A1, A2, T, R> strategy, A1 arg1, A2 arg2, T input) {
        enterStrategy(strategy);
        final @Nullable R result = strategy.evalInternal(this, arg1, arg2, input);
        return exitStrategy(strategy, result);
    }

    @Override
    public <A1, A2, A3, T, R> @Nullable R eval(Strategy3<A1, A2, A3, T, R> strategy, A1 arg1, A2 arg2, A3 arg3, T input) {
        enterStrategy(strategy);
        final @Nullable R result = strategy.evalInternal(this, arg1, arg2, arg3, input);
        return exitStrategy(strategy, result);
    }

    /**
     * Called just before a strategy is evaluated.
     *
     * @param strategy the strategy that will be evaluated
     */
    protected void enterStrategy(StrategyDecl strategy) {
        level += 1;
        log.trace(prefixString("→", level, " " + strategy.toString()));
    }

    /**
     * Called just after a strategy is evaluated.
     *
     * @param strategy the strategy that was evaluated
     * @param result the result of evaluating the strategy
     * @return the (possibly modified) result of evaluating the strategy
     */
    protected <R> @Nullable R exitStrategy(StrategyDecl strategy, @Nullable R result) {
        log.trace(prefixString(" ", level - 1, " " + strategy));
        if (result != null) {
            log.trace(prefixString(" ", level - 1, "← " + strategy.toString()));
        } else {
            log.trace(prefixString(" ", level - 1, "⨯ " + strategy.toString()));
        }
        level -= 1;
        return result;
    }

    /**
     * Prefixes a string with a prefix, repeated the specified number of times.
     *
     * @param prefix the prefix
     * @param prefixCount the number of prefix repetitions
     * @param s the string
     * @return the resulting string
     */
    private String prefixString(String prefix, int prefixCount, String s) {
        final StringBuilder sb = new StringBuilder(prefix.length() * prefixCount + s.length());
        for (int i = 0; i < prefixCount; i++) {
            sb.append(prefix);
        }
        sb.append(s);
        return sb.toString();
    }

    @Override
    public boolean isLogEnabled(StrategyDecl strategy) {
        @Nullable final Logger logger = getLogger(strategy);
        if (logger == null) return false;
        return logger.isEnabled(strategyLogLevel);
    }

    @Override
    public void log(StrategyDecl strategy, String message) {
        @Nullable final Logger logger = getLogger(strategy);
        if (logger == null) return;
        logger.log(strategyLogLevel, message);
    }

    @Override
    public void log(StrategyDecl strategy, String message, Throwable cause) {
        @Nullable final Logger logger = getLogger(strategy);
        if (logger == null) return;
        logger.log(strategyLogLevel, message, cause);
    }

    @Override
    public void log(StrategyDecl strategy, String message, Object... args) {
        @Nullable final Logger logger = getLogger(strategy);
        if (logger == null) return;
        logger.log(strategyLogLevel, message, args);
    }

    @Override
    public void log(StrategyDecl strategy, String message, Throwable cause, Object... args) {
        @Nullable final Logger logger = getLogger(strategy);
        if (logger == null) return;
        logger.log(strategyLogLevel, message, cause);
    }

    /**
     * Gets a logger for the given strategy.
     *
     * @param strategy the strategy to get the logger for
     * @return the logger; or {@code null} if no logger will be available
     */
    @Nullable private Logger getLogger(StrategyDecl strategy) {
        if (loggerFactory == null) return null;
        // Most logger implementations (e.g., Log4J) perform some logger caching
        // using a hashtable lookup on the name. Therefore, we are not going to
        // do any effort here to reuse loggers on every log call.
        // We separate the strategy name from the prefix with "::.", such that you
        // can filter logs on a dot-prefix or colon-colon-prefix.
        return loggerFactory.create("tego::." + strategy.getName());
    }
}
