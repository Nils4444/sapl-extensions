package io.sapl.axon.commandhandling.model;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import lombok.Value;

public class TestAggregateAPI {

	@Value
	public static class CreateAggregate {
		@TargetAggregateIdentifier
		final String id;
	}

	@Value
	public static class AggregateCreated {
		final String id;
	}

}
