package uk.co.terminological.bibliography.record;

import java.util.Optional;

public class RecordIdentifier implements RecordReference {

	IdType idType;
	String id;
	
	@Override
	public Optional<String> getIdentifier() {
		return Optional.of(id);
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
		RecordIdentifier other = (RecordIdentifier) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equalsIgnoreCase(other.id))
			return false;
		if (idType != other.idType)
			return false;
		return true;
	}

}
