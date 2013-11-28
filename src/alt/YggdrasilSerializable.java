package ch.njol.yggdrasil;

import java.io.Serializable;
import java.lang.reflect.Field;

public interface YggdrasilSerializable extends Serializable {
	
	public boolean incompatibleFieldType(Field f, Object value);
	
	public boolean missingField(String field, Object value);
	
}
