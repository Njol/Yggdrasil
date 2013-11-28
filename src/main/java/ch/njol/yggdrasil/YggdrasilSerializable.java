/*
 *   This file is part of Yggdrasil, a data format to store object graphs.
 *
 *  Yggdrasil is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Yggdrasil is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Skript.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * 
 * Copyright 2013 Peter Güttinger
 * 
 */

package ch.njol.yggdrasil;

import java.io.IOException;
import java.io.StreamCorruptedException;
import java.lang.reflect.Field;

import ch.njol.yggdrasil.Fields.FieldContext;

/**
 * Marks a class as serialisable by Yggdrasil.
 * TODO special case for enums?
 * 
 * @author Peter Güttinger
 */
public interface YggdrasilSerializable {
	
	/**
	 * A class that has had fields changed or removed from it should implement this interface to handle the now invalid fields that may still be read from stream.
	 * 
	 * @author Peter Güttinger
	 */
	public static interface YggdrasilRobustSerializable extends YggdrasilSerializable {
		
		/**
		 * Called if a field that was read from stream is of an incompatible type to the existing field in this class.
		 * 
		 * @param f The Java field
		 * @param value The field read from stream
		 * @return Whether the field was handled.
		 */
		public boolean incompatibleFieldType(Field f, FieldContext value);
		
		/**
		 * Called if a field was read from stream which does not exist in this class.
		 * 
		 * @param field The field read from stream
		 * @return Whether the field was handled.
		 */
		public boolean missingField(FieldContext field);
		
	}
	
	/**
	 * Provides a method to resolve missing enum constants.
	 * 
	 * @author Peter Güttinger
	 */
	public static interface YggdrasilRobustEnum extends YggdrasilSerializable {
		
		/**
		 * Called when an enum constant is read from stream that does not exist in this enum.
		 * 
		 * @param name The name read from stream
		 * @return The renamed enum constant or null if the read string is invalid.
		 */
		public Enum<?> missingConstant(String name);
		
	}
	
	/**
	 * A class that has transient fields or more generally wants to exactly define which fields to write to/read from stream should implement this interface. It provides two
	 * methods similar to Java's writeObject and readObject methods.
	 * 
	 * @author Peter Güttinger
	 */
	public static interface YggdrasilExtendedSerializable extends YggdrasilSerializable {
		
		/**
		 * Serialises this object. Only fields contained in the returned Fields object will be written to stream.
		 * <p>
		 * You can use <tt>return new {@link Fields#Fields(Object) Fields}(this);</tt> to emulate the default behaviour.
		 * 
		 * @return A Fields object containing all fields that should be written to stream
		 */
		public Fields serialize();
		
		/**
		 * Deserialises this object. No fields have been set when this method is called, use <tt>fields.{@link Fields#setFields setFields}(this, yggdrasil)</tt> to set all
		 * compatible non-transient and non-static fields (and call incompatible/missing field handlers if applicable).
		 * <p>
		 * You can use <tt>fields.{@link Fields#setFields(Object, Yggdrasil) setFields}(this, yggdrasil);</tt> to emulate the default behaviour.
		 * 
		 * @param fields A Fields object containing all fields read from stream
		 */
		public void deserialize(Fields fields) throws StreamCorruptedException;
		
	}
	
}
