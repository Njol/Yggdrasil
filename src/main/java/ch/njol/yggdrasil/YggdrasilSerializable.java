/*
 *   This file is part of Yggdrasil, a data format to store object graphs, and the Java implementation thereof.
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
 * Copyright 2013-2014 Peter Güttinger
 * 
 */

package ch.njol.yggdrasil;

import java.io.NotSerializableException;
import java.io.StreamCorruptedException;
import java.lang.reflect.Field;

import org.eclipse.jdt.annotation.NonNull;

import ch.njol.yggdrasil.Fields.FieldContext;

/**
 * Marks a class as serialisable by Yggdrasil.
 * <p>
 * Enums don't have to implement this interface to be serialisable, but can implement {@link YggdrasilRobustEnum}.
 * 
 * @author Peter Güttinger
 */
public interface YggdrasilSerializable {
	
	/**
	 * A class that has had fields added, changed, or removed from it should implement this interface to handle the now invalid/missing fields that may still be read from stream.
	 * 
	 * @author Peter Güttinger
	 */
	public static interface YggdrasilRobustSerializable extends YggdrasilSerializable {
		
		/**
		 * Called if a field that was read from stream is of an incompatible type to the existing field in this class.
		 * 
		 * @param field The Java field
		 * @param value The field read from stream
		 * @return Whether the field was handled. If false,
		 *         <tt>yggdrasil.{@link Yggdrasil#incompatibleField(Object, Field, FieldContext) incompatibleField}(this, field, value)</tt> will be called.
		 */
		@SuppressWarnings("null")
		public boolean incompatibleField(@NonNull Field field, @NonNull FieldContext value) throws StreamCorruptedException;
		
		/**
		 * Called if a field was read from stream which does not exist in this class.
		 * 
		 * @param field The field read from stream
		 * @return Whether the field was handled. If false, <tt>yggdrasil.{@link Yggdrasil#excessiveField(Object, FieldContext) excessiveField}(this, field)</tt> will be called.
		 */
		@SuppressWarnings("null")
		public boolean excessiveField(@NonNull FieldContext field) throws StreamCorruptedException;
		
		/**
		 * Called if a field was not found in the stream.
		 * 
		 * @param field The field that did not occur in the stream
		 * @return Whether the field was handled (e.g. true if the default value is fine). If false,
		 *         <tt>yggdrasil.{@link Yggdrasil#missingField(Object, Field) missingField}(this, field)</tt> will be called.
		 */
		@SuppressWarnings("null")
		public boolean missingField(@NonNull Field field) throws StreamCorruptedException;
		
	}
	
	/**
	 * Provides a method to resolve missing enum constants.
	 * 
	 * @author Peter Güttinger
	 */
	public static interface YggdrasilRobustEnum {
		
		/**
		 * Called when an enum constant is read from stream that does not exist in this enum.
		 * <p>
		 * This method will be called on an arbitrary enum constant. An exception will be thrown if this enum is empty (because this method won't be able to return anything
		 * anyway).
		 * 
		 * @param name The name read from stream
		 * @return The renamed enum constant or null if the read string is invalid. If the returned Enum is not an instance of this enum type an exception will be thrown.
		 */
		public Enum<?> excessiveConstant(String name);
		
	}
	
	/**
	 * A class that has transient fields or more generally wants to exactly define which fields to write to/read from stream should implement this interface. It provides two
	 * methods similar to Java's writeObject and readObject methods.
	 * <p>
	 * If a class implements this interface implementing {@link YggdrasilRobustSerializable} as well is pointless since its methods won't get called.
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
		 * @throws NotSerializableException If this object or one of its fields is not serialisable
		 */
		public Fields serialize() throws NotSerializableException;
		
		/**
		 * Deserialises this object. No fields have been set when this method is called, use <tt>fields.{@link Fields#setFields setFields}(this, yggdrasil)</tt> to set all
		 * compatible non-transient and non-static fields (and call incompatible/missing field handlers if applicable &ndash; this implies that errors will be thrown if the fields
		 * object is invalid).
		 * <p>
		 * You can use <tt>fields.{@link Fields#setFields(Object) setFields}(this);</tt> to emulate the default behaviour.
		 * 
		 * @param fields A Fields object containing all fields read from stream
		 * @throws StreamCorruptedException If the Fields object is invalid, i.e. was not written by {@link #serialize()} or Yggrdasil's default serialisation.
		 * @throws NotSerializableException
		 */
		@SuppressWarnings("null")
		public void deserialize(@NonNull Fields fields) throws StreamCorruptedException, NotSerializableException;
		
	}
	
}
