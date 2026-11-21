package com.rentrix.rentrixserver.common.id;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.EventTypeSets;

import java.util.EnumSet;

/** Hibernate generator behind {@link TimeOrderedId}; keeps an id that is already set. */
public class TimeOrderedIdGenerator implements BeforeExecutionGenerator {
	
	@Override
	public Object generate(SharedSessionContractImplementor session, Object owner, Object currentValue,
		EventType eventType) {
		return currentValue != null ? currentValue : TimeOrderedIds.next();
	}
	
	@Override
	public boolean allowAssignedIdentifiers() {
		return true;
	}
	
	@Override
	public EnumSet<EventType> getEventTypes() {
		return EventTypeSets.INSERT_ONLY;
	}
	
}
