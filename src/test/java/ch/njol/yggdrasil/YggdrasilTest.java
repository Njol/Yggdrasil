package ch.njol.yggdrasil;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.StreamCorruptedException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.Test;

import ch.njol.yggdrasil.YggdrasilSerializable.YggdrasilExtendedSerializable;

@SuppressWarnings("resource")
public class YggdrasilTest {
	
	static Yggdrasil y = new Yggdrasil();
	static {
		y.registerSingleClass(TestEnum.class);
		y.registerSingleClass(PETest1.class);
		y.registerSingleClass(PETest1.PETest2.class);
		y.registerSingleClass(TestClass1.class);
		y.registerSingleClass(TestClass2.class);
	}
	
	@YggdrasilID("test-enum #!~/\r\n\t\\\"'<>&amp;,.:'`´¢⽰杻鱶")
	private static enum TestEnum implements YggdrasilSerializable {
		SOMETHING, SOMETHINGELSE;
	}
	
	@YggdrasilID("PETest1")
	private static class PETest1 extends PseudoEnum<PETest1> {
		protected PETest1(final String name) {
			super(name);
		}
		
		public final static PETest1 PET1_0 = new PETest1("PET1_0 #!~/\r\n\t\\\"'<>&amp;,.:'`´¢⽰杻鱶");
		
		@YggdrasilID("PETest2")
		public static class PETest2 extends PETest1 {
			protected PETest2(final String name) {
				super(name);
			}
			
			public final static PETest2 PET2_0 = new PETest2("PET2_0");
			
			public final static PETest2 PET2_1 = new PETest2("PET2_1") {
				@Override
				public String toString() {
					return "PET2_1!!!";
				}
			};
			
			public final static PETest1 PET1_1 = new PETest1("PET1_1");
			
		}
		
		public final static PETest2 PET2_2 = new PETest2("PET2_2");
		
		public final static PETest1 PET1_2 = new PETest1("PET1_2") {};
		
	}
	
	public final static PETest1 PET1_3 = new PETest1("PET1_3");
	
	@YggdrasilID("TestClass1")
	private final static class TestClass1 implements YggdrasilSerializable {
		@Nullable
		private final String blah;
		
		private TestClass1() {
			blah = "blah";
		}
		
		public TestClass1(final String b) {
			blah = b;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			final String blah = this.blah;
			result = prime * result + ((blah == null) ? 0 : blah.hashCode());
			return result;
		}
		
		@Override
		public boolean equals(final @Nullable Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof TestClass1))
				return false;
			final TestClass1 other = (TestClass1) obj;
			final String blah = this.blah;
			if (blah == null) {
				if (other.blah != null)
					return false;
			} else if (!blah.equals(other.blah))
				return false;
			return true;
		}
		
		@Override
		public String toString() {
			return "" + blah;
		}
	}
	
	@YggdrasilID("TestClass2")
	private final static class TestClass2 implements YggdrasilExtendedSerializable {
		private transient boolean ok = false;
		private final static int DEFAULT = 5;
		private final int someFinalInt;
		
		@SuppressWarnings("unused")
		public TestClass2() {
			someFinalInt = DEFAULT;
		}
		
		TestClass2(final int what) {
			assert what != DEFAULT;
			someFinalInt = what;
			ok = true;
		}
		
		@Override
		public Fields serialize() throws NotSerializableException {
			return new Fields(this);
		}
		
		@Override
		public void deserialize(final Fields fields) throws StreamCorruptedException, NotSerializableException {
			fields.setFields(this, y);
			assert !ok;
			if (someFinalInt != DEFAULT)
				ok = true;
			assert ok;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (ok ? 1231 : 1237);
			result = prime * result + someFinalInt;
			return result;
		}
		
		@Override
		public boolean equals(final @Nullable Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof TestClass2))
				return false;
			final TestClass2 other = (TestClass2) obj;
			if (ok != other.ok)
				return false;
			if (someFinalInt != other.someFinalInt)
				return false;
			return true;
		}
		
		@Override
		public String toString() {
			return ok + "; " + someFinalInt;
		}
	}
	
	@SuppressWarnings("null")
	private static byte[] save(final @Nullable Object o) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final YggdrasilOutputStream s = y.newOutputStream(out);
		s.writeObject(o);
		s.flush();
		s.close();
		return out.toByteArray();
	}
	
//	@SuppressWarnings("null")
//	private static String saveXML(final @Nullable Object o) throws IOException {
//		final ByteArrayOutputStream out = new ByteArrayOutputStream();
//		final YggXMLOutputStream s = y.newXMLOutputStream(out);
//		s.writeObject(o);
//		s.flush();
//		s.close();
//		return out.toString("utf-8");
//	}
	
	@Nullable
	private static Object load(final byte[] d) throws IOException {
		final YggdrasilInputStream l = y.newInputStream(new ByteArrayInputStream(d));
		return l.readObject();
	}
	
//	@Nullable
//	private static Object loadXML(final String xml) throws IOException {
//		final YggdrasilInputStream l = y.newXMLInputStream(new ByteArrayInputStream(xml.getBytes("utf-8")));
//		return l.readObject();
//	}
	
	// random objects
	/* private constructor is tested -> */@SuppressWarnings("synthetic-access")
	final Object[] random = {
			1, .5, true, 'a', "abc", "multi\nline\r\nstring\rwith\t\n\r\ttabs \u2001\nand\n\u00A0other\u2000\nwhitespace", 2l, (byte) -1, (short) 124, Float.POSITIVE_INFINITY,
			Byte.MIN_VALUE, Byte.MAX_VALUE, Short.MIN_VALUE, Short.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE,
			Float.MIN_NORMAL, Float.MIN_VALUE, Float.NEGATIVE_INFINITY, -Float.MAX_VALUE, Double.MIN_NORMAL, Double.MIN_VALUE, Double.NEGATIVE_INFINITY, -Double.MAX_VALUE,
			(byte) 0x12, (short) 0x1234, 0x12345678, 0x123456789abcdef0L, Float.intBitsToFloat(0x12345678), Double.longBitsToDouble(0x123456789abcdef0L),
			
			new double[] {0, 1, Double.MIN_NORMAL, Double.POSITIVE_INFINITY, Double.MAX_VALUE, -500, 0.123456, Double.NaN},
			new float[] {.1f, 7f, 300}, new byte[] {0x12, 0x34, 0x56, 0x78, (byte) 0x9a, (byte) 0xbc, (byte) 0xde, (byte) 0xf0}, new long[][][] {{ {0}, {0, 5, 7}, null, {}}},
			new Object[][] { {new int[] {0, 4}}, null, {new int[] {}, null, new int[] {-1, 300, 42}}, {}, new Integer[] {5, 7, null}, {null, null, new int[][] {null, {5, 7}, {}}}},
			new ArrayList[][] { {new ArrayList<Integer>(Arrays.asList(1, 2, null, 9, 100)), null, null, new ArrayList<Object>(Arrays.asList())}, {null}, null, {}},
			
			Object.class, ArrayList.class,
			new ArrayList<Integer>(Arrays.asList(1, 2, 3)), new HashSet<Integer>(Arrays.asList(1, 4, 3, 3, 2)),
			new HashMap<Object, Object>(), new LinkedList<Integer>(Arrays.asList(4, 3, 2, 1)),
			
			TestEnum.SOMETHING, PETest1.PET1_0, PETest1.PETest2.PET1_1, PETest1.PET1_2, PET1_3, PETest1.PETest2.PET2_1, PETest1.PET2_2, PETest1.PETest2.PET2_0,
			new TestClass1(), new TestClass1("foo"), new TestClass2(20)
	};
	
//	private final static class CollectionTests {
//		Collection<?> al = new ArrayList<>(Arrays.asList(1, 2, 3)),
//				hs = new HashSet<>(Arrays.asList(1, 2, 3, 3, 4)),
//				ll = new LinkedList<>(Arrays.asList(4, 3, 2, 1));
//		Map<?, ?> hm = new HashMap<>();
//	}
	
	@Test
	public void generalTest() throws IOException {
		System.out.println();
		for (final Object o : random) {
			final byte[] d = save(o);
			print(o, d);
			final Object l = load(d);
			assert equals(o, l) : o.getClass().getName() + ": " + toString(o) + " <> " + toString(l);
			final byte[] d2 = save(l);
			assert equals(d, d2) : o.getClass().getName() + ": " + toString(o) + "\n" + toString(d) + " <>\n" + toString(d2);
		}
	}
	
//	@Test
//	public void generalXMLTest() throws IOException {
//		System.out.println();
//		for (final Object o : random) {
//			final String d = saveXML(o);
//			System.out.println(o + ": " + d);
//			System.out.println();
//			final Object l = loadXML(d);
//			assert equals(o, l) : toString(o) + " <> " + toString(l);
//			final String d2 = saveXML(l);
//			assert equals(d, d2) : toString(o) + "\n" + toString(d) + " <>\n" + toString(d2);
//		}
//	}
	
	@Test
	public void keepReferencesTest() throws IOException {
		System.out.println();
		final Object ref = new Object();
		final Map<Integer, Object> m = new HashMap<Integer, Object>();
		m.put(1, ref);
		m.put(2, new Object());
		m.put(3, ref);
		final byte[] md = save(m);
		print(m, md);
		@SuppressWarnings("unchecked")
		final Map<Integer, Object> ms = (Map<Integer, Object>) load(md);
		assert ms != null && ms.get(1) == ms.get(3) && ms.get(1) != ms.get(2) : ms;
	}
	
	private final static class UnmodifiedClass implements YggdrasilSerializable {
		final int unchanged;
		
		@SuppressWarnings("unused")
		UnmodifiedClass() {
			unchanged = -10;
		}
		
		UnmodifiedClass(final int c) {
			unchanged = c;
		}
	}
	
	private final static class ModifiedClass implements YggdrasilSerializable {
		@YggdrasilID("unchanged")
		final int changed;
		
		ModifiedClass() {
			changed = -20;
		}
		
		@SuppressWarnings("unused")
		ModifiedClass(final int c) {
			changed = c;
		}
	}
	
	final static String modifiedClassID = "something random";
	static Class<?> currentModifiedClass = UnmodifiedClass.class;
	static {
		y.registerClassResolver(new ClassResolver() {
			@Override
			@Nullable
			public String getID(final Class<?> c) {
				if (c == currentModifiedClass)
					return modifiedClassID;
				return null;
			}
			
			@Override
			@Nullable
			public Class<?> getClass(final String id) {
				if (id.equals(modifiedClassID))
					return currentModifiedClass;
				return null;
			}
		});
	}
	
	@Test
	public void renameTest() throws IOException {
		System.out.println();
		currentModifiedClass = UnmodifiedClass.class;
		final UnmodifiedClass o1 = new UnmodifiedClass(200);
		final byte[] d1 = save(o1);
		print(o1, d1);
		currentModifiedClass = ModifiedClass.class;
		final ModifiedClass o2 = (ModifiedClass) load(d1);
		assert o2 != null;
		assertEquals(o1.unchanged, o2.changed);
		
		currentModifiedClass = ModifiedClass.class;
		final ModifiedClass o3 = new ModifiedClass();
		final byte[] d3 = save(o3);
		print(o3, d3);
		currentModifiedClass = UnmodifiedClass.class;
		final UnmodifiedClass o4 = (UnmodifiedClass) load(d3);
		assert o4 != null;
		assertEquals(o3.changed, o4.unchanged);
	}
	
	private static boolean equals(final @Nullable Object o1, final @Nullable Object o2) {
		if (o1 == null || o2 == null)
			return o1 == o2;
		if (o1.getClass() != o2.getClass())
			return false;
		if (o1.getClass().isArray()) {
			if (o1 instanceof Object[]) {
				return Arrays.deepEquals((Object[]) o1, (Object[]) o2);
			} else {
				final int l1 = Array.getLength(o1);
				final int l2 = Array.getLength(o2);
				if (l1 != l2)
					return false;
				for (int i = 0; i < l1; i++) {
					if (!Array.get(o1, i).equals(Array.get(o2, i)))
						return false;
				}
				return true;
			}
		} else if (o1 instanceof Collection) {
			final Iterator<?> i1 = ((Collection<?>) o1).iterator(), i2 = ((Collection<?>) o2).iterator();
			while (i1.hasNext()) {
				if (!i2.hasNext())
					return false;
				if (!equals(i1.next(), i2.next()))
					return false;
			}
			return !i1.hasNext();
		} else {
			return o1.equals(o2);
		}
	}
	
	private String toString(final @Nullable Object o) {
		if (o == null)
			return "null";
		if (o.getClass().isArray()) {
			final StringBuilder b = new StringBuilder("[");
			b.append(o.getClass().getCanonicalName()).append("::");
			final int l = Array.getLength(o);
			for (int i = 0; i < l; i++) {
				if (i != 0)
					b.append(", ");
				b.append(toString(Array.get(o, i)));
			}
			b.append("]");
			return "" + b;
		}
		return "" + o;
	}
	
	private final static void print(final @Nullable Object o, final byte[] d) {
		System.out.print(o);
		System.out.print(": ");
		for (final byte b : d) {
			if (Pattern.matches("[a-zA-Z.]", "" + ((char) b))) {
				System.out.print((char) b);
			} else {
				final String h = Integer.toHexString(b & 0xFF);
				System.out.print(" " + (h.length() == 1 ? "0" : "") + h + " ");
			}
		}
		System.out.println();
		System.out.println();
	}
	
}
