package uk.co.terminological.bibliography.record;

import java.io.Serializable;

public class ImmutableRecordReferenceMapping implements RecordReferenceMapping,Serializable {

	ImmutableRecordReference source;
	ImmutableRecordReference target;
	
	public ImmutableRecordReferenceMapping(RecordReferenceMapping m) {
		this.source = Builder.recordReference(m.getSource());
		this.target = Builder.recordReference(m.getTarget());
	}
	
	public ImmutableRecordReferenceMapping(
			RecordReference source,
			RecordReference target) {
		this.source = Builder.recordReference(source);
		this.target = Builder.recordReference(target);
	}

	/* (non-Javadoc)
	 * @see uk.co.terminological.bibliography.record.ReordIdMapping#getSource()
	 */
	@Override
	public ImmutableRecordReference getSource() {return source;}
	/* (non-Javadoc)
	 * @see uk.co.terminological.bibliography.record.ReordIdMapping#getTarget()
	 */
	@Override
	public ImmutableRecordReference getTarget() {return target;}

	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((source == null) ? 0 : source.hashCode());
		result = prime * result + ((target == null) ? 0 : target.hashCode());
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ImmutableRecordReferenceMapping other = (ImmutableRecordReferenceMapping) obj;
		if (source == null) {
			if (other.source != null)
				return false;
		} else if (!source.equals(other.source))
			return false;
		if (target == null) {
			if (other.target != null)
				return false;
		} else if (!target.equals(other.target))
			return false;
		return true;
	}
	
}
