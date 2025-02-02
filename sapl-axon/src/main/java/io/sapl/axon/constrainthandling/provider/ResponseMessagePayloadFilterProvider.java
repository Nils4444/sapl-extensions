package io.sapl.axon.constrainthandling.provider;

import java.util.Objects;
import java.util.Set;

import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.axon.constrainthandling.api.ResultConstraintHandlerProvider;
import io.sapl.spring.constraints.providers.ContentFilterUtil;
import lombok.RequiredArgsConstructor;

/**
 * This provider offers the manipulation of ResultMessage payloads.
 * 
 * The constraint must be a JSON Object.
 * 
 * The constraint must contain the field "type" with value
 * "filterMessagePayloadContent".
 * 
 * See {@link io.sapl.spring.constraints.providers.ContentFilterUtil} for
 * supported actions.
 * 
 * @author Dominic Heutelbeck
 * @since 2.1.0
 */
@RequiredArgsConstructor
public class ResponseMessagePayloadFilterProvider implements ResultConstraintHandlerProvider {

	private static final String               CONSTRAINT_TYPE = "filterMessagePayloadContent";
	private static final String               TYPE            = "type";
	private static final Set<ResponseType<?>> SUPPORTED_TYPES = Set.of(ResponseTypes.instanceOf(Object.class),
			ResponseTypes.optionalInstanceOf(Object.class), ResponseTypes.multipleInstancesOf(Object.class),
			ResponseTypes.publisherOf(Object.class));

	private final ObjectMapper objectMapper;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isResponsible(JsonNode constraint) {
		if (constraint == null || !constraint.isObject())
			return false;

		var type = constraint.get(TYPE);

		if (Objects.isNull(type) || !type.isTextual())
			return false;

		return CONSTRAINT_TYPE.equals(type.asText());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Set<ResponseType<?>> getSupportedResponseTypes() {
		return SUPPORTED_TYPES;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object mapPayload(Object payload, Class<?> clazz, JsonNode constraint) {
		return ContentFilterUtil.getHandler(constraint, objectMapper).apply(payload);
	}

}
