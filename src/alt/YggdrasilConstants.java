package ch.njol.yggdrasil;

import java.io.Externalizable;
import java.nio.charset.Charset;

public abstract class YggdrasilConstants {
	private YggdrasilConstants() {}
	
	/** 
	 * Magic Number: ASCII for "Ygg\n"
	 * <p>
	 * hex: 0x5967670a
	 */
	public final static int I_YGGDRASIL = ('Y' << 24) + ('g' << 16) + ('g' << 8) + '\n';
	
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
