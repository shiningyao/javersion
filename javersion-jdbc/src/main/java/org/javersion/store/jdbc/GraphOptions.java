/*
 * Copyright 2015 Samppa Saarela
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.javersion.store.jdbc;

import org.javersion.core.KeepHeadsAndNewest;
import org.javersion.core.VersionNode;
import org.javersion.object.ObjectVersionGraph;
import org.javersion.path.PropertyPath;
import org.javersion.util.Check;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.util.function.Function;
import java.util.function.Predicate;

@Immutable
public class GraphOptions<Id, M> {

    public static <Id, M> GraphOptions<Id, M> keepHeadsAndNewest(final int count, final int compactThreshold) {
        Check.that(compactThreshold > count, "compactThreshold should be > count");
        return new GraphOptions<>(sizeExcludingHeadsExceeds(compactThreshold), keepHeadsAndNewest(count));
    }

    public static <M> Predicate<ObjectVersionGraph<M>> sizeExcludingHeadsExceeds(int compactThreshold) {
        Check.that(compactThreshold > 0, "compactThreshold should be > 0");
        return g -> g.size() - g.getHeads().size() >=  compactThreshold;
    }

    public static <M> Function<ObjectVersionGraph<M>, Predicate<VersionNode<PropertyPath, Object, M>>> keepHeadsAndNewest(int count) {
        Check.that(count >= 0, "count should be >= 0");
        return g -> new KeepHeadsAndNewest<>(g, count);
    }

    public static <M> Predicate<ObjectVersionGraph<M>> never() {
        return  g -> false;
    }

    public static <M> Function<ObjectVersionGraph<M>, Predicate<VersionNode<PropertyPath, Object, M>>> all() {
        return g -> v -> true;
    }

    @Nonnull
    public final Predicate<ObjectVersionGraph<M>> optimizeWhen;

    @Nonnull
    public final Function<ObjectVersionGraph<M>, Predicate<VersionNode<PropertyPath, Object, M>>> optimizeKeep;

    public GraphOptions() {
        this(null, null);
    }

    public GraphOptions(@Nullable Predicate<ObjectVersionGraph<M>> optimizeWhen,
                        @Nullable Function<ObjectVersionGraph<M>, Predicate<VersionNode<PropertyPath, Object, M>>> optimizeKeep) {
        if (optimizeWhen != null) {
            if (optimizeKeep == null) {
                throw new IllegalArgumentException("compactWhen requires compactKeep");
            }
            this.optimizeWhen = optimizeWhen;
            this.optimizeKeep = optimizeKeep;
        } else {
            if (optimizeKeep != null) {
                throw new IllegalArgumentException("compactKeep requires compactWhen");
            }
            this.optimizeWhen = never();
            this.optimizeKeep = all();
        }
    }

}
