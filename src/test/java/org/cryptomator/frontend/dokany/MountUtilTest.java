package org.cryptomator.frontend.dokany;

import dev.dokan.dokan_java.constants.dokany.MountOption;
import dev.dokan.dokan_java.structure.EnumIntegerSet;
import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static dev.dokan.dokan_java.constants.dokany.MountOption.CURRENT_SESSION;
import static dev.dokan.dokan_java.constants.dokany.MountOption.DEBUG_MODE;
import static org.cryptomator.frontend.dokany.MountUtil.MountOptions;

public class MountUtilTest {

	@Test
	void testIllegalOptionString() {
		String optionsString = "Axolotl";
		Assertions.assertThrows(IllegalArgumentException.class, () -> MountUtil.parse(optionsString));
	}

	@Test
	void testDokanOptionsParsing() {
		String optionsString = "--options CURRENT_SESSION,DEBUG_MODE";
		Assertions.assertDoesNotThrow(() -> MountUtil.parse(optionsString));
	}

	@Test
	void testUnsupportedDokanOptionsFail() {
		String optionsString = "--options CURRENT_SESSION,ALT_STREAM";
		Assertions.assertThrows(IllegalArgumentException.class, () -> MountUtil.parse(optionsString));
	}

	@Test
	void testThreadCountToBig() {
		String optionsString = "--thread-count 65.536";
		Assertions.assertThrows(IllegalArgumentException.class, () -> MountUtil.parse(optionsString));
	}

	@Test
	void testParsing() throws ParseException {
		String optionsString = "--thread-count 10 --sector-size 4096 --options CURRENT_SESSION,DEBUG_MODE";

		MountOptions expected = new MountUtil.MountOptionsBuilder().addThreadCount((short) 10).addSectorSize(4096).addDokanOptions(EnumIntegerSet.enumSetFromInt(CURRENT_SESSION.getMask() | DEBUG_MODE.getMask(), MountOption.values())).build();

		MountOptions actual = MountUtil.parse(optionsString);
		Assertions.assertEquals(expected.getDokanOptions(), actual.getDokanOptions());
		Assertions.assertEquals(expected.getThreadCount(), actual.getThreadCount());
		Assertions.assertEquals(expected.getSectorSize(), actual.getSectorSize());
		Assertions.assertEquals(expected.getTimeout(), actual.getTimeout());
	}

	@Test
	void shortOptionDisabled() {
		String optionsString = "-t 10 -to 1000 -ss 4096";
		Assertions.assertThrows(ParseException.class, () -> MountUtil.parse(optionsString));
	}

}
