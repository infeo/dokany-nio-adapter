package com.dokany.java.migrated.structure.filesecurity;

import com.dokany.java.migrated.constants.microsoft.filesecurity.AccessControlEntryFlag;
import com.dokany.java.migrated.constants.microsoft.filesecurity.AccessControlEntryType;
import com.dokany.java.migrated.structure.EnumIntegerSet;

public abstract class AccessControlEntry implements Byteable {

	protected final AccessControlEntryType type;

	protected final EnumIntegerSet<AccessControlEntryFlag> flags;

	protected AccessControlEntry(AccessControlEntryType type, EnumIntegerSet<AccessControlEntryFlag> flags) {
		this.type = type;
		this.flags = flags;
	}

	@Override
	public abstract byte[] toByteArray();

	@Override
	public abstract int sizeOfByteArray();

}
