/*
 * Copyright © 2019-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.interpreter.pip.util;

import com.hivemq.client.internal.mqtt.exceptions.MqttClientStateExceptions;
import com.hivemq.client.mqtt.exceptions.ConnectionClosedException;
import com.hivemq.client.mqtt.exceptions.ConnectionFailedException;
import com.hivemq.client.mqtt.exceptions.MqttClientStateException;
import com.hivemq.client.mqtt.exceptions.MqttSessionExpiredException;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5DisconnectException;
import com.hivemq.client.mqtt.mqtt5.message.Mqtt5Message;
import com.hivemq.client.mqtt.mqtt5.message.Mqtt5MessageType;
import com.hivemq.client.mqtt.mqtt5.message.disconnect.Mqtt5Disconnect;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.NoSuchElementException;

import static io.sapl.interpreter.pip.util.ErrorUtility.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ErrorUtilityTest {
    @Test
    void when_isConnectionClosedException_then_returnTrue() {
        assertTrue(isErrorRelevantToRemoveClientCache(new ConnectionClosedException("error")));
    }

    @Test
    void when_isConnectionFailedException_then_returnTrue() {
        assertTrue(isErrorRelevantToRemoveClientCache(new ConnectionFailedException("error")));
    }

    @Test
    void when_isMqttSessionExpiredException_then_returnTrue() {
        assertTrue(isErrorRelevantToRemoveClientCache(new MqttSessionExpiredException("error",
                new ConnectionFailedException("error"))));
    }

    @Test
    void when_isMqttClientStateExceptionAndClientIsNotConnected_then_returnTrue() {
        assertTrue(isErrorRelevantToRemoveClientCache(
                new MqttClientStateException(MqttClientStateExceptions.notConnected().getMessage())));
    }

    @Test
    void when_isMqttClientStateExceptionAndClientIsConnected_then_returnFalse() {
        assertFalse(isErrorRelevantToRemoveClientCache(
                new MqttClientStateException(MqttClientStateExceptions.alreadyConnected().getMessage())));
    }

    @Test
    void when_evaluatingIfDisconnectWasCausedByClientAndWasCausedByServer_then_returnFalse() {
        // GIVEN
        Mqtt5DisconnectException mqtt5DisconnectExceptionMock = mock(Mqtt5DisconnectException.class);
        when(mqtt5DisconnectExceptionMock.getMessage()).thenReturn("Server sent " + Mqtt5MessageType.DISCONNECT);

        // WHEN
        boolean isClientCausedDisconnect = isClientCausedDisconnect(new Throwable(mqtt5DisconnectExceptionMock));

        // THEN
        assertFalse(isClientCausedDisconnect);
    }

    @Test
    void when_clientSendDisconnect_then_clientCausedTheDisconnect() {
        // GIVEN
        Mqtt5DisconnectException disconnectException = new Mqtt5DisconnectException(Mqtt5Disconnect.builder().build(),
                "Client sent " + Mqtt5MessageType.DISCONNECT);
        RuntimeException wrappedDisconnectException = new RuntimeException(disconnectException);

        // WHEN
        boolean isCausedByClient = isClientCausedDisconnect(wrappedDisconnectException);

        // THEN
        assertTrue(isCausedByClient);
    }

    @Test
    void when_errorIsNoSuchElementException_then_doNotRetry() {
        // GIVEN
        var errorFlux  = Flux.error(new NoSuchElementException("No element"))
                .retryWhen(getRetrySpec(null));

        // THEN
        StepVerifier.create(errorFlux)
                .expectError(NoSuchElementException.class)
                .verify();
    }
}