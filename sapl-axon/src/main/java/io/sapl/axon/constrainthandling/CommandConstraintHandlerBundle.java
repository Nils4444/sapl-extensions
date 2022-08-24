package io.sapl.axon.constrainthandling;

import java.util.function.Function;

import org.axonframework.commandhandling.CommandMessage;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CommandConstraintHandlerBundle<R, T> {
	public static final CommandConstraintHandlerBundle<?, ?> NOOP_BUNDLE = new CommandConstraintHandlerBundle<>();

	protected final Runnable                                       onDecision;
	protected final Function<Throwable, Throwable>                 errorMapper;
	protected final Function<CommandMessage<?>, CommandMessage<?>> commandMapper;
	protected final Function<R, R>                                 resultMapper;
	protected final Runnable                                       handlersOnObject;

	// @formatter:off
	private CommandConstraintHandlerBundle() {
		this.onDecision = ()->{};
		this.commandMapper = Function.identity();
		this.errorMapper = Function.identity();
		this.resultMapper = Function.identity();
		this.handlersOnObject = ()->{}; 
	}
	// @formatter:on

	public void executeOnDecisionHandlers() {
		onDecision.run();
	}

	public Exception executeOnErrorHandlers(Exception t) {
		var mapped = errorMapper.apply(t);
		if (mapped instanceof Exception)
			return (Exception) mapped;
		return new RuntimeException("Error: " + t.getMessage(), t);
	}

	public void executeAggregateConstraintHandlerMethods() {
		handlersOnObject.run();
	}

	public CommandMessage<?> executeCommandMappingHandlers(CommandMessage<?> message) {
		return commandMapper.apply(message);
	}

	@SuppressWarnings("unchecked")
	public Object executePostHandlingHandlers(Object value) {
		return resultMapper.apply((R) value);
	}

}