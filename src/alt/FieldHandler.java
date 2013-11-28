package ch.njol.yggdrasil;

import java.lang.reflect.Field;

public interface FieldHandler {
	
	/**
	 * Called when a loaded field doesn't exist.
	 * 
	 * @param o The object whose filed is missing
	 * @param name The name of the missing field
	 * @param value The value loaded for the field
	 * @return Whether this Handler handled the request
	 */
	public boolean missingField(Object o, String name, Object value);
	
	/**
	 * Called when a loaded value is not compatible with the type of a field.
	 * 
	 * @param o The object the field belongs to
	 * @param f The field to set
	 * @param value The value that couldn't be stored in the field
	 * @return Whether this Handler handled the request
	 */
	public boolean incompatibleFieldType(Object o, Field f, Object value);
	
}
