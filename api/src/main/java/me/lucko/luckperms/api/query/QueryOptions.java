/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.api.query;

import me.lucko.luckperms.api.LuckPermsProvider;
import me.lucko.luckperms.api.context.ContextSet;
import me.lucko.luckperms.api.context.ImmutableContextSet;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Represents the parameters for a lookup query.
 */
public interface QueryOptions {

    /**
     * Creates a new {@link Builder} for the given {@link QueryMode}.
     *
     * @param mode the query mode
     * @return a new builder
     */
    static @NonNull Builder builder(@NonNull QueryMode mode) {
        return LuckPermsProvider.get().getContextManager().queryOptionsBuilder(mode);
    }

    /**
     * Creates a {@link QueryMode#CONTEXTUAL contextual} {@link QueryOptions}
     * instance with the given context and flags.
     *
     * @param context the contexts to query in
     * @param flags the query flags
     * @return the query options
     */
    static @NonNull QueryOptions contextual(@NonNull ContextSet context, @NonNull Set<Flag> flags) {
        return builder(QueryMode.CONTEXTUAL).context(context).flags(flags).build();
    }

    /**
     * Creates a {@link QueryMode#NON_CONTEXTUAL non contextual} {@link QueryOptions}
     * instance with the given flags.
     *
     * @param flags the query flags
     * @return the query options
     */
    static @NonNull QueryOptions nonContextual(@NonNull Set<Flag> flags) {
        return builder(QueryMode.NON_CONTEXTUAL).flags(flags).build();
    }

    /**
     * Gets the default {@link QueryMode#NON_CONTEXTUAL non contextual}
     * query options.
     *
     * <p>This instance has the default set of flags.</p>
     *
     * @return the default non contextual query options
     */
    static @NonNull QueryOptions nonContextual() {
        return DefaultQueryOptions.NON_CONTEXTUAL;
    }

    /**
     * Gets the default {@link QueryMode#CONTEXTUAL contextual}
     * query options.
     *
     * <p>This instance has the default set of flags, and an empty set
     * of contexts.</p>
     *
     * @return the default contextual query options
     */
    static @NonNull QueryOptions defaultContextualOptions() {
        return DefaultQueryOptions.CONTEXTUAL;
    }

    /**
     * Gets the {@link QueryMode}.
     *
     * @return the query mode
     */
    @NonNull QueryMode mode();

    /**
     * Gets the {@link ContextSet context}, if the options are
     * {@link QueryMode#CONTEXTUAL contextual}.
     *
     * <p>Throws {@link IllegalStateException} if the {@link #mode() mode} is
     * {@link QueryMode#NON_CONTEXTUAL}.</p>
     *
     * @return the context
     */
    @NonNull ImmutableContextSet context();

    /**
     * Gets if the given {@link Flag} is set.
     *
     * @param flag the flag
     * @return if the flag is set
     */
    boolean flag(@NonNull Flag flag);

    /**
     * Gets the {@link Flag}s which are set.
     *
     * @return the flags
     */
    @NonNull Set<Flag> flags();

    /**
     * Gets the value assigned to the given {@link OptionKey}.
     *
     * <p>Returns an {@link Optional#empty() empty optional} if the option has
     * not been set.</p>
     *
     * @param key the key to lookup
     * @param <O> the option type
     * @return the value assigned to the key
     */
    <O> @NonNull Optional<O> option(@NonNull OptionKey<O> key);

    /**
     * Gets the options which are set.
     *
     * @return the options
     */
    @NonNull Map<OptionKey<?>, Object> options();

    /**
     * Gets whether this {@link QueryOptions} satisfies the given required
     * {@link ContextSet context}.
     *
     * @param contextSet the contexts
     * @return the result
     */
    boolean satisfies(@NonNull ContextSet contextSet);

    /**
     * Converts this {@link QueryOptions} to a mutable builder.
     *
     * @return a builder, with the same properties already set
     */
    @NonNull Builder toBuilder();

    /**
     * Builder for {@link QueryOptions}.
     */
    interface Builder {

        /**
         * Sets the {@link QueryMode}.
         *
         * @param mode the mode to set
         * @return this builder
         */
        @NonNull Builder mode(@NonNull QueryMode mode);

        /**
         * Sets the context.
         *
         * <p>Note that this is a set operation, not append. Existing contexts
         * will be overridden.</p>
         *
         * <p>Throws {@link IllegalStateException} if the mode is not
         * {@link QueryMode#CONTEXTUAL}.</p>
         *
         * @param context the context to set
         * @return this builder
         */
        @NonNull Builder context(@NonNull ContextSet context);

        /**
         * Sets the value of the given flag.
         *
         * @param flag the flag
         * @param value the value to set
         * @return this builder
         */
        @NonNull Builder flag(@NonNull Flag flag, boolean value);

        /**
         * Sets the flags.
         *
         * <p>Note that this is a set operation, not append. Existing flags will
         * be overridden.</p>
         *
         * @param flags the flags
         * @return this builder
         */
        @NonNull Builder flags(@NonNull Set<Flag> flags);

        /**
         * Sets the value of the given option.
         *
         * <p>Passing {@code null} in place of a value will clear any existing
         * value set for the key.</p>
         *
         * @param key the option key
         * @param value the value, or null to clear
         * @param <O> the option type
         * @return this builder
         */
        <O> @NonNull Builder option(@NonNull OptionKey<O> key, @Nullable O value);

        /**
         * Builds a {@link QueryOptions} instance from the properties defined to
         * the builder.
         *
         * @return a {@link QueryOptions} instance.
         */
        @NonNull QueryOptions build();

    }

}
