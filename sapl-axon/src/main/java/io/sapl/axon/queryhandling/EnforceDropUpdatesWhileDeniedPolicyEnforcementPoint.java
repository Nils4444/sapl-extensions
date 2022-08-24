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
package io.sapl.axon.queryhandling;

import static java.util.function.Predicate.not;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.GenericSubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.axon.constrainthandling.ConstraintHandlerService;
import io.sapl.axon.constrainthandling.QueryConstraintHandlerBundle;
import reactor.core.CoreSubscriber;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * The EnforceTillDeniedPolicyEnforcementPoint implements continuous policy
 * enforcement on a Flux resource access point.
 *
 * If the initial decision of the PDP is not PERMIT, an AccessDeniedException is
 * signaled downstream without subscribing to resource access point.
 *
 * After an initial PERMIT, the PEP subscribes to the resource access point and
 * forwards events downstream until a non-PERMIT decision from the PDP is
 * received. Then, an AccessDeniedException is signaled downstream and the PDP
 * and resource access point subscriptions are cancelled.
 *
 * Whenever a decision is received, the handling of obligations and advice are
 * updated accordingly.
 *
 * The PEP does not permit onErrorContinue() downstream.
 *
 * @param <U> type of the Flux contents
 */
public class EnforceDropUpdatesWhileDeniedPolicyEnforcementPoint<U> extends Flux<SubscriptionQueryUpdateMessage<U>> {

	private final Flux<AuthorizationDecision> decisions;
	private final ConstraintHandlerService    constraintHandlerService;
	private final ResponseType<?>             resultResponseType;
	private final ResponseType<?>             updateResponseType;

	private Flux<SubscriptionQueryUpdateMessage<U>>            resourceAccessPoint;
	private EnforcementSink<SubscriptionQueryUpdateMessage<U>> sink;

	final AtomicReference<Disposable>                         decisionsSubscription = new AtomicReference<>();
	final AtomicReference<Disposable>                         dataSubscription      = new AtomicReference<>();
	final AtomicReference<AuthorizationDecision>              latestDecision        = new AtomicReference<>();
	final AtomicReference<QueryConstraintHandlerBundle<?, ?>> constraintHandler     = new AtomicReference<>();
	final AtomicBoolean                                       stopped               = new AtomicBoolean(false);

	private EnforceDropUpdatesWhileDeniedPolicyEnforcementPoint(Flux<AuthorizationDecision> decisions,
			Flux<SubscriptionQueryUpdateMessage<U>> updateMessageFlux,
			ConstraintHandlerService constraintHandlerService, ResponseType<?> resultResponseType,
			ResponseType<?> updateResponseType) {
		this.decisions                = decisions;
		this.resourceAccessPoint      = updateMessageFlux;
		this.constraintHandlerService = constraintHandlerService;
		this.updateResponseType       = updateResponseType;
		this.resultResponseType       = resultResponseType;
	}

	public static <U> Flux<SubscriptionQueryUpdateMessage<U>> of(Flux<AuthorizationDecision> decisions,
			Flux<SubscriptionQueryUpdateMessage<U>> updateMessageFlux,
			ConstraintHandlerService constraintHandlerService, ResponseType<?> resultResponseType,
			ResponseType<?> updateResponseType) {
		EnforceDropUpdatesWhileDeniedPolicyEnforcementPoint<U> pep = new EnforceDropUpdatesWhileDeniedPolicyEnforcementPoint<U>(
				decisions, updateMessageFlux, constraintHandlerService, resultResponseType, updateResponseType);
		return (Flux<SubscriptionQueryUpdateMessage<U>>) pep
				.onErrorMap(AccessDeniedException.class, pep::handleAccessDenied).doOnCancel(pep::handleCancel)
				.onErrorStop();
	}

	@Override
	public void subscribe(CoreSubscriber<? super SubscriptionQueryUpdateMessage<U>> subscriber) {
		if (sink != null)
			throw new IllegalStateException("Operator may only be subscribed once.");
		var context = subscriber.currentContext();
		sink                = new EnforcementSink<>();
		resourceAccessPoint = resourceAccessPoint.contextWrite(context);
		Flux.create(sink).subscribe(subscriber);
		decisionsSubscription.set(decisions.doOnNext(this::handleNextDecision).contextWrite(context).subscribe());
	}

	@SuppressWarnings("unchecked")
	private void handleNextDecision(AuthorizationDecision decision) {
		var                                previousDecision = latestDecision.getAndSet(decision);
		QueryConstraintHandlerBundle<?, ?> newBundle;
		try {
			newBundle = constraintHandlerService.buildQueryPreHandlerBundle(decision, resultResponseType,
					Optional.of(updateResponseType));
			constraintHandler.set(newBundle);
		} catch (AccessDeniedException e) {
			sink.error(e);
			disposeDecisionsAndResourceAccessPoint();
			return;
		}
		try {
			constraintHandler.get().executeOnDecisionHandlers();
		} catch (AccessDeniedException e) {
			// NOP
			return;
		}
		if (decision.getDecision() != Decision.PERMIT) {
			// NOP
			return;
		}

		if (decision.getResource().isPresent()) {
			try {
				var newResponse = constraintHandlerService.deserializeResource(decision.getResource().get(),
						updateResponseType);
				sink.next(new GenericSubscriptionQueryUpdateMessage<>((U) newResponse));
			} catch (AccessDeniedException e) {
				sink.error(e);
			}
			sink.complete();
			disposeDecisionsAndResourceAccessPoint();
		}

		if (previousDecision == null)
			dataSubscription.set(wrapResourceAccessPointAndSubscribe());
	}

	private Disposable wrapResourceAccessPointAndSubscribe() {
		return resourceAccessPoint.doOnError(this::handleError).doOnNext(this::handleNext)
				.doOnComplete(this::handleComplete).subscribe();
	}

	@SuppressWarnings("unchecked")
	private void handleNext(SubscriptionQueryUpdateMessage<U> value) {
		// the following guard clause makes sure that the constraint handlers do not get
		// called after downstream consumers cancelled. If the RAP is not consisting of
		// delayed elements, but something like Flux.just(1,2,3) the handler would be
		// called for 2 and 3, even if there was a take(1) applied downstream.
		if (stopped.get()) {
			return;
		}
		if (latestDecision.get().getDecision() != Decision.PERMIT) {
			// Drop while not permitted
			return;
		}
		try {
			var bundle = constraintHandler.get();
			bundle.executeOnNextHandlers(value).ifPresent(val -> sink.next((SubscriptionQueryUpdateMessage<U>) val));
		} catch (Throwable t) {
			handleNextDecision(AuthorizationDecision.DENY);
			// Alternative: terminate
			// sink.error(t);
			// disposeDecisionsAndResourceAccessPoint();
		}
	}

	private void handleComplete() {
		if (stopped.get())
			return;
		sink.complete();
		disposeDecisionsAndResourceAccessPoint();
	}

	private void handleCancel() {
		disposeDecisionsAndResourceAccessPoint();
	}

	private void handleError(Throwable error) {
		try {
			sink.error(constraintHandler.get().executeOnErrorHandlers(error));
		} catch (Throwable t) {
			sink.error(t);
			disposeDecisionsAndResourceAccessPoint();
		}
	}

	private Throwable handleAccessDenied(Throwable error) {
		try {
			return constraintHandler.get().executeOnErrorHandlers(error);
		} catch (Throwable t) {
			disposeDecisionsAndResourceAccessPoint();
			return t;
		}
	}

	private void disposeDecisionsAndResourceAccessPoint() {
		stopped.set(true);
		disposeActiveIfPresent(decisionsSubscription);
		disposeActiveIfPresent(dataSubscription);
	}

	private void disposeActiveIfPresent(AtomicReference<Disposable> atomicDisposable) {
		Optional.ofNullable(atomicDisposable.get()).filter(not(Disposable::isDisposed)).ifPresent(Disposable::dispose);
	}

}