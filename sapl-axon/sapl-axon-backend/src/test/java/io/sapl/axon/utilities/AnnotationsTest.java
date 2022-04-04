/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sapl.axon.utilities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.sapl.spring.method.metadata.EnforceDropWhileDenied;
import io.sapl.spring.method.metadata.EnforceRecoverableIfDenied;
import io.sapl.spring.method.metadata.EnforceTillDenied;
import io.sapl.spring.method.metadata.PostEnforce;
import io.sapl.spring.method.metadata.PreEnforce;

class AnnotationsTest {

    @Test
    public void subscriptionAnnotations_Unchanged(){
        assertEquals(Annotations.SUBSCRIPTION_ANNOTATIONS,List.of(
        		EnforceTillDenied.class,
                EnforceDropWhileDenied.class,
                EnforceRecoverableIfDenied.class));
    }

    @Test
    public void annotations_Unchanged(){
        assertEquals(Annotations.SINGLE_ANNOTATIONS,List.of(
                PreEnforce.class, PostEnforce.class));
    }



}