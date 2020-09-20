package uk.co.terminological.bibliography.record;

import java.util.Optional;
import java.util.function.Consumer;

public interface RecordReference {

	public Optional<String> getIdentifier();
	public IdType getIdentifierType();
	
	public default ImmutableRecordReference asId() {
		return Builder.recordReference(this);
	}
	
	public static String print(RecordReference rr) {
		return rr.getIdentifierType().toString()+":"+rr.getIdentifier().orElse("unknown");
	}
	
	public default boolean isComplete() {return getIdentifier().isPresent();}
	
	public default void ifComplete(Consumer<ImmutableRecordReference> c) {
		if (getIdentifier().isPresent()) c.accept(Builder.recordReference(this));
	}
}
