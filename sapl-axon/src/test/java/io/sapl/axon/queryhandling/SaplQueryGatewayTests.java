package io.sapl.axon.queryhandling;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.DefaultSubscriptionQueryResult;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SimpleQueryBus;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.UpdateHandlerRegistration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class SaplQueryGatewayTests {

	private static final AccessDeniedException ACCESS_DENIED         = new AccessDeniedException("Access denied");
	private static final String                DEFAULT_QUERY_MESSAGE = "default test query message";
	private static final ResponseType<?>       DEFAULT_RESPONSE_TYPE = ResponseTypes.instanceOf(String.class);

	private QueryBus queryBus;

	private SaplQueryGateway gateway;
	private AtomicInteger    accessDeniedCounter;
	private Runnable         accessDeniedHandler = () -> accessDeniedCounter.getAndIncrement();

	@BeforeEach
	void beforeEach() {
		queryBus            = spy(SimpleQueryBus.builder().build());
		gateway             = spy(new SaplQueryGateway(queryBus, List.of()));
		accessDeniedCounter = new AtomicInteger(0);
	}

	@Test
	void when_recoverableSubscriptionQuery_with_responseTypes_then_subscriptionQueryResult() {
		gateway.recoverableSubscriptionQuery(DEFAULT_QUERY_MESSAGE, DEFAULT_RESPONSE_TYPE, DEFAULT_RESPONSE_TYPE,
				accessDeniedHandler);
		verify(gateway, times(1)).subscriptionQuery(eq(String.class.getName()), any(SubscriptionQueryMessage.class),
				eq(DEFAULT_RESPONSE_TYPE), eq(ResponseTypes.instanceOf(RecoverableResponse.class)), any(int.class));
		assertTrue(accessDeniedCounter.get() == 0);
	}

	@Test
	void when_recoverableSubscriptionQuery_with_types_then_subscriptionQueryResult() {
		gateway.recoverableSubscriptionQuery(DEFAULT_QUERY_MESSAGE, String.class, String.class, accessDeniedHandler);
		verify(gateway, times(1)).subscriptionQuery(eq(String.class.getName()), any(SubscriptionQueryMessage.class),
				eq(DEFAULT_RESPONSE_TYPE), eq(ResponseTypes.instanceOf(RecoverableResponse.class)), any(int.class));
		assertTrue(accessDeniedCounter.get() == 0);
	}

	@Test
	@SuppressWarnings("unchecked")
	void when_wrappedSubscriptionQuery_with_accessDenied_then_executeAccessDeniedHandler() {
		var emitter      = mock(QueryUpdateEmitter.class);
		var registration = new UpdateHandlerRegistration<>(() -> false, Flux.just(), () -> {
							});
		when(emitter.registerUpdateHandler(any(SubscriptionQueryMessage.class), anyInt())).thenReturn(registration);
		queryBus = spy(SimpleQueryBus.builder().queryUpdateEmitter(emitter).build());
		gateway  = spy(new SaplQueryGateway(queryBus, List.of()));

		doReturn(
				new DefaultSubscriptionQueryResult<>(Mono.empty(), Flux.error(ACCESS_DENIED), () -> false))
				.when(gateway).subscriptionQuery(any(String.class), any(SubscriptionQueryMessage.class),
						any(ResponseType.class), any(ResponseType.class), anyInt());
		var result = gateway.recoverableSubscriptionQuery(DEFAULT_QUERY_MESSAGE, DEFAULT_RESPONSE_TYPE,
				DEFAULT_RESPONSE_TYPE,
				accessDeniedHandler);

		assertNull(result.initialResult().block());
		assertThrows(AccessDeniedException.class, () -> result.updates().blockLast());
		assertTrue(accessDeniedCounter.get() == 1);
	}
}
