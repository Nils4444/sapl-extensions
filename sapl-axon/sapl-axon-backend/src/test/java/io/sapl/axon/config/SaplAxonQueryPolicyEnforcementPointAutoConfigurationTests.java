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

package io.sapl.axon.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.constraints.ConstraintHandlerService;
import io.sapl.axon.utilities.AuthorizationSubscriptionBuilderService;

public class SaplAxonQueryPolicyEnforcementPointAutoConfigurationTests {

	@Test
	void whenRan_thenMapperIsAvailableAndModulesAreRegistered() {
		var logger = (Logger) LoggerFactory.getLogger("org.springframework.beans.factory.support.DefaultListableBeanFactory");
		logger.setLevel(Level.ERROR);
		var mapper = new ObjectMapper();
		SimpleModule module = new SimpleModule();
		mapper.registerModule(module);

		var contextRunner = new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(SaplAxonQueryPolicyEnforcementPointAutoConfiguration.class))
				.withBean(ObjectMapper.class, () -> mapper)
				.withBean(AuthorizationSubscriptionBuilderService.class, () -> mock(AuthorizationSubscriptionBuilderService.class))
				.withBean(ConstraintHandlerService.class, () -> mock(ConstraintHandlerService.class))
				.withBean(PolicyDecisionPoint.class, ()-> mock(PolicyDecisionPoint.class))
		;

		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(SaplAxonQueryPolicyEnforcementPointAutoConfiguration.class);
		});
	}
	
}