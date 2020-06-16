package mb.common.option;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.Serializable;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class Option<T extends Serializable> implements Serializable {
    private static final Option<?> NONE = new Option<>();

    private final @Nullable T value;


    private Option() {
        this.value = null;
    }

    private Option(T value) {
        this.value = Objects.requireNonNull(value);
    }


    public static <T extends Serializable> Option<T> ofNone() {
        @SuppressWarnings("unchecked") final Option<T> empty = (Option<T>)NONE;
        return empty;
    }

    public static <T extends Serializable> Option<T> ofSome(T value) {
        return new Option<>(value);
    }

    public static <T extends Serializable> Option<T> ofNullable(@Nullable T value) {
        return value == null ? ofNone() : ofSome(value);
    }


    public boolean isSome() {
        return value != null;
    }

    public void ifSome(Consumer<? super T> consumer) {
        if(value != null) {
            consumer.accept(value);
        }
    }

    public boolean isNone() {
        return value == null;
    }

    public void ifNone(Runnable runnable) {
        if(value != null) {
            runnable.run();
        }
    }

    public void ifElse(Consumer<? super T> someConsumer, Runnable noneRunnable) {
        if(isSome()) {
            someConsumer.accept(value);
        } else {
            noneRunnable.run();
        }
    }


    public <U extends Serializable> Option<U> map(Function<? super T, ? extends U> mapper) {
        return value != null ? Option.ofSome(mapper.apply(value)) : ofNone();
    }

    public <U extends Serializable> U mapOr(U def, Function<? super T, ? extends U> mapper) {
        return value != null ? mapper.apply(value) : def;
    }

    public <U extends @Nullable Serializable> @Nullable U mapOrNull(Function<? super T, ? extends U> mapper) {
        return value != null ? mapper.apply(value) : null;
    }

    public <U extends Serializable> U mapOrElse(Supplier<? extends U> def, Function<? super T, ? extends U> mapper) {
        return value != null ? mapper.apply(value) : def.get();
    }

    public <U extends Serializable, E extends Throwable> U mapOrElseThrow(Supplier<? extends E> exceptionSupplier, Function<? super T, ? extends U> mapper) throws E {
        if(value != null) {
            return mapper.apply(value);
        }
        throw exceptionSupplier.get();
    }

    public <U extends Serializable> Option<U> flatMap(Function<? super T, Option<U>> mapper) {
        return value != null ? mapper.apply(value) : ofNone();
    }


    public T unwrap() {
        if(value != null) {
            return value;
        }
        throw new NoSuchElementException("Called `unwrap` on a `None` value");
    }

    public T unwrapOr(T other) {
        return value != null ? value : other;
    }

    public T unwrapOrElse(Supplier<? extends T> other) {
        return value != null ? value : other.get();
    }

    public <E extends Throwable> T unwrapOrElseThrow(Supplier<? extends E> exceptionSupplier) throws E {
        if(value != null) {
            return value;
        } else {
            throw exceptionSupplier.get();
        }
    }


    public @Nullable T get() {
        return value;
    }

    public @Nullable T getOr(@Nullable T def) {
        return value != null ? value : def;
    }

    public @Nullable T getOrElse(Supplier<? extends @Nullable T> def) {
        return value != null ? value : def.get();
    }


    public Optional<T> toOptional() {
        return Optional.ofNullable(value);
    }


    @Override public boolean equals(Object o) {
        if(this == o) return true;
        if(o == null || getClass() != o.getClass()) return false;
        final Option<?> option = (Option<?>)o;
        return Objects.equals(value, option.value);
    }

    @Override public int hashCode() {
        return Objects.hash(value);
    }

    @Override public String toString() {
        return value != null ? "Some(" + value + ")" : "None";
    }

    // TODO: deserialize none to shared none value
}
