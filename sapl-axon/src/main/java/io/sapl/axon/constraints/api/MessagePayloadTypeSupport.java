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

package io.sapl.axon.constraints.api;

import org.axonframework.messaging.Message;

/**
 * Interface is used to scope ConstraintHandlerProviders to a specific
 * MessagePayloadType.
 * 
 * @param <T> MessagePayloadType that is supported by implementing
 *            HandlerProvider
 */
public interface MessagePayloadTypeSupport<T> {
	Class<T> getSupportedMessagePayloadType();

	@SuppressWarnings("rawtypes")
	// RawTypes are necessary to be able to return only the class of a Message
	Class<? extends Message> getSupportedMessageType();

	default boolean supports(Message<?> message) {

		if (!getSupportedMessagePayloadType().isAssignableFrom(message.getPayloadType()))
			return false;
		return getSupportedMessageType().isAssignableFrom(message.getClass());
	}
}
