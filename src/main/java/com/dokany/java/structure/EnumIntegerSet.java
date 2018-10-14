package com.dokany.java.structure;

import java.util.AbstractSet;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Objects;

import com.dokany.java.migrated.constants.microsoft.AccessMask;
import com.dokany.java.migrated.constants.microsoft.FileSystemFlag;
import com.dokany.java.migrated.constants.EnumInteger;
import com.dokany.java.migrated.constants.dokany.MountOption;

/**
 * Used to store multiple enum values such as {@link FileSystemFlag} and {@link MountOption}.
 *
 * @param <T> Type of enum
 */
public final class EnumIntegerSet<T extends Enum<T> & EnumInteger> extends AbstractSet<T> {
	private final EnumSet<T> elements;

	public EnumIntegerSet(final Class<T> clazz) {
		elements = EnumSet.noneOf(clazz);
	}

	public EnumIntegerSet(T first, T... others) {
		this.elements = EnumSet.of(first, others);
	}

	/**
	 * Will return an
	 *
	 * @param value
	 * @param allEnumValues
	 * @return
	 */
	public static <T extends Enum<T> & EnumInteger> EnumIntegerSet<T> enumSetFromInt(final int value, final T[] allEnumValues) {
		EnumIntegerSet<T> elements = new EnumIntegerSet<>(allEnumValues[0].getDeclaringClass());
		int remainingValues = value;
		for (T current : allEnumValues) {
			int mask = current.getMask();

			if ((remainingValues & mask) == mask) {
				elements.add(current);
				remainingValues -= mask;
			}
		}
		return elements;
	}

	public final void add(T item, T... items) {
		//TODO: add checks for item
		if (Objects.isNull(items) || (items.length < 1)) {
			throw new IllegalArgumentException("items array cannot be empty");
		}
		for (final T item : items) {
			if (Objects.nonNull(item)) {
				elements.add(item);
			}
		}
	}

	public int toInt() {
		int toReturn = 0;
		for (final T current : elements) {
			// Already checked (in constructor) to ensure only objects which implement EnumInteger are stored in values
			final EnumInteger enumInt = (EnumInteger) current;
			toReturn |= enumInt.getMask();
		}
		return toReturn;
	}

	@Override
	public boolean add(final T e) {
		return elements.add(e);
	}

	@Override
	public Iterator<T> iterator() {
		return elements.iterator();
	}

	@Override
	public int size() {
		return elements.size();
	}

	@Override
	@SuppressWarnings("all")
	public String toString() {
		return "EnumIntegerSet(elements=" + this.elements + ")";
	}
}
