/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.VariantTransformConfigurationException;
import org.gradle.api.internal.artifacts.VariantTransformRegistry;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.model.internal.type.ModelType;

import javax.annotation.Nullable;
import java.util.Arrays;

public class DefaultTransformationRegistration implements VariantTransformRegistry.Registration {

    private final ImmutableAttributes from;
    private final ImmutableAttributes to;
    private final TransformationStep transformationStep;

    public static VariantTransformRegistry.Registration create(ImmutableAttributes from, ImmutableAttributes to, Class<? extends ArtifactTransform> implementation, @Nullable Object config, Object[] params, IsolatableFactory isolatableFactory, ClassLoaderHierarchyHasher classLoaderHierarchyHasher, InstantiatorFactory instantiatorFactory, TransformerInvoker transformerInvoker) {
        Hasher hasher = Hashing.newHasher();
        hasher.putString(implementation.getName());
        hasher.putHash(classLoaderHierarchyHasher.getClassLoaderHash(implementation.getClassLoader()));

        // TODO - should snapshot later
        Isolatable<Object[]> paramsSnapshot;
        Isolatable<?> configSnapshot;
        try {
            paramsSnapshot = isolatableFactory.isolate(params);
            configSnapshot = isolatableFactory.isolate(config);
        } catch (Exception e) {
            throw new VariantTransformConfigurationException(String.format("Could not snapshot parameters values for transform %s: %s", ModelType.of(implementation).getDisplayName(), Arrays.asList(params)), e);
        }

        paramsSnapshot.appendToHasher(hasher);
        configSnapshot.appendToHasher(hasher);

        Transformer transformer = new DefaultTransformer(implementation, configSnapshot, paramsSnapshot, hasher.hash(), instantiatorFactory, from);
        return new DefaultTransformationRegistration(from, to, new TransformationStep(transformer, transformerInvoker));
    }

    public DefaultTransformationRegistration(ImmutableAttributes from, ImmutableAttributes to, TransformationStep transformationStep) {
        this.from = from;
        this.to = to;
        this.transformationStep = transformationStep;
    }

    @Override
    public AttributeContainerInternal getFrom() {
        return from;
    }

    @Override
    public AttributeContainerInternal getTo() {
        return to;
    }

    @Override
    public TransformationStep getTransformationStep() {
        return transformationStep;
    }
}
