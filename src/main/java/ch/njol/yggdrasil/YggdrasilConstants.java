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

import java.io.Externalizable;
import java.nio.charset.Charset;

public abstract class YggdrasilConstants {
	private YggdrasilConstants() {}
	
	/**
	 * Magic Number: "Ygg\0"
	 * <p>
	 * hex: 0x59676700
	 */
	public final static int I_YGGDRASIL = ('Y' << 24) + ('g' << 16) + ('g' << 8) + '\0';
	
	/** protocol version */
	public final static short VERSION = 1;
	
	// ===
	
	public enum SerializationType {
		/**
		 * Default serialisation method
		 */
		DEFAULT(0),
		/**
		 * The object has a writeObject(ObjectOutputStream) method
		 */
		CUSTOM(1),
		/**
		 * The object implements {@link Externalizable}
		 */
		EXTERNALIZED(2);
		
		public final int tag;
		
		SerializationType(final int tag) {
			this.tag = tag;
		}
	}
	
	// === UTF-8 ===
	
	public final static Charset utf8 = Charset.forName("UTF-8");
	
}
