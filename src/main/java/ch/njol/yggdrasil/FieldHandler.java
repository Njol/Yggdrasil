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

import java.io.StreamCorruptedException;
import java.lang.reflect.Field;

import ch.njol.yggdrasil.Fields.FieldContext;

public interface FieldHandler {
	
	/**
	 * Called when a loaded field doesn't exist.
	 * 
	 * @param o The object whose filed is missing
	 * @param name The name of the missing field
	 * @param value The value loaded for the field
	 * @return Whether this Handler handled the request
	 */
	public boolean missingField(Object o, FieldContext field) throws StreamCorruptedException;
	
	/**
	 * Called when a loaded value is not compatible with the type of a field.
	 * 
	 * @param o The object the field belongs to
	 * @param f The field to set
	 * @param value The value that couldn't be stored in the field
	 * @return Whether this Handler handled the request
	 */
	public boolean incompatibleFieldType(Object o, Field f, FieldContext field) throws StreamCorruptedException;
	
}