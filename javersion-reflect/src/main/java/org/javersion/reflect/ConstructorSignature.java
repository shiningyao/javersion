/*
 * Copyright 2016 Samppa Saarela
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
package org.javersion.reflect;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableList;

public final class ConstructorSignature {

    public static final ConstructorSignature DEFAULT_CONSTRUCTOR = new ConstructorSignature();

    public static final ConstructorSignature STRING_CONSTRUCTOR = new ConstructorSignature(String.class);

    @Nonnull
    private final List<Class<?>> parameterTypes;

    public ConstructorSignature(Constructor<?> constructor) {
        this(constructor.getParameterTypes());
    }

    public ConstructorSignature(Class<?>... parameterTypes) {
        this.parameterTypes = ImmutableList.copyOf(parameterTypes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConstructorSignature that = (ConstructorSignature) o;

        return parameterTypes.equals(that.parameterTypes);
    }

    @Override
    public int hashCode() {
        return parameterTypes.hashCode();
    }

    public String toString() {
        return parameterTypes.stream()
                .map(TypeDescriptor::getSimpleName)
                .collect(Collectors.joining(",", "(", ")"));
    }

}
