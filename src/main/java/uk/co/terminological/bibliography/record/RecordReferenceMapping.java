package uk.co.terminological.bibliography.record;

public interface RecordReferenceMapping {

	RecordReference getSource();
	RecordReference getTarget();

	public default boolean isComplete() {
		return getSource().isComplete() && getTarget().isComplete();
	}

}