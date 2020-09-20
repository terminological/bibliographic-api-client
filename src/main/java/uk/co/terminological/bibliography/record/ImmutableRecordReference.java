package uk.co.terminological.bibliography.record;

import java.io.Serializable;
import java.util.Optional;

public class ImmutableRecordReference implements RecordReference, Serializable {

	IdType idType;
	String id;
	
	public ImmutableRecordReference(RecordReference ref) {
		idType = ref.getIdentifierType();
		id = ref.getIdentifier().map(s -> s.replaceAll("^PMC", "")).orElse(null);
	}
	
	public ImmutableRecordReference(IdType idType2, String id2) {
		this.idType = idType2;
		this.id = id2==null? null: id2.replaceAll("^PMC", "");
	}

	@Override
	public Optional<String> getIdentifier() {
		return Optional.ofNullable(id).map(s -> s.replaceAll("^PMC", ""));
	}

	@Override
	public IdType getIdentifierType() {
		return idType;
	}
	
	

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.toUpperCase().hashCode());
		result = prime * result + ((idType == null) ? 0 : idType.hashCode());
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
		ImmutableRecordReference other = (ImmutableRecordReference) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equalsIgnoreCase(other.id))
			return false;
		if (idType != other.idType)
			return false;
		return true;
	}

	public String toString() {
		return RecordReference.print(this);
	}

	public boolean isComplete() {
		return id != null;
	}
}
