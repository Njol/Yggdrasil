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

public interface ClassResolver {
	
	/**
	 * Resolves a class by its ID.
	 * 
	 * @param id The ID used when storing objects
	 * @return The Class object that represents data with the given ID, or null if the ID does not belong to the implementor
	 */
	public Class<?> getClass(String id);
	
	/**
	 * Gets an ID for a Class. The ID is used to identify the type of a saved object.
	 * <p>
	 * // TODO make sure that it's unique
	 * 
	 * @param c The class to get the ID of
	 * @return The ID of the given class, or null if this is not a class of the implementor
	 */
	public String getID(Class<?> c);
	
}
