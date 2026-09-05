/*
 EcmDroid - Android Diagnostic Tool for Buell Motorcycles
 Copyright (C) 2012 by Michel Marti

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 3
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, see <http://www.gnu.org/licenses/>.
 */
package biz.logicminds.buelltune;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * New edge-case coverage for signed-byte protocol semantics (KTD8/plan U4)
 * that had no existing test before the Kotlin port: negative-offset wrap in
 * {@link Bit#refreshValue(byte[])}, a {@link BitSet} whose mask spans bit 7
 * (the sign bit), and {@link PDU}'s checksum-rejection contract.
 */
@RunWith(JUnit4.class)
public class TestByteSemantics {

	/**
	 * Bit.refreshValue wraps a negative offset via `data.length + offset`,
	 * matching the ECM's "count from the end of the buffer" convention used
	 * for a handful of trailing status bytes. This was previously exercised
	 * only indirectly (and never with an explicit negative-offset assertion)
	 * via the instrumented TestBitSetProvider.
	 */
	@Test
	public void testBitRefreshValueNegativeOffsetWrap() {
		Bit bit = new Bit();
		bit.setBitNr(3);
		bit.setOffset(-2); // second-to-last byte: 5 + (-2) == index 3

		byte[] data = new byte[]{0x00, 0x00, 0x00, (byte) 0xFF, 0x00};
		assertTrue(bit.refreshValue(data));
		assertTrue(bit.isSet());
		assertEquals((byte) 0x08, bit.getValue());

		byte[] cleared = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00};
		assertFalse(bit.refreshValue(cleared));
		assertFalse(bit.isSet());
	}

	/**
	 * A BitSet mask/value spanning bit 7 forces the mask byte negative
	 * (0xC0 == -64 as a signed byte); getMask()/getValue()/updateValue()
	 * must produce the correct unsigned bit pattern despite the
	 * sign-extension that occurs when Kotlin (like Java) widens a Byte to
	 * Int for bitwise arithmetic.
	 */
	@Test
	public void testBitSetMaskSpansSignBit() {
		BitSet bitset = new BitSet("SignSpan", null, 0);
		Bit bit6 = new Bit();
		bit6.setBitNr(6);
		Bit bit7 = new Bit();
		bit7.setBitNr(7);
		bitset.add(bit6);
		bitset.add(bit7);

		assertEquals((byte) 0xC0, bitset.getMask());

		bit6.setValue(true);
		bit7.setValue(true);
		assertEquals((byte) 0xC0, bitset.getValue());

		byte[] bytes = new byte[]{0x00};
		assertTrue(bitset.updateValue(bytes));
		assertEquals((byte) 0xC0, bytes[0]);

		// Applying the same value again must report "unchanged".
		assertFalse(bitset.updateValue(bytes));
		assertEquals((byte) 0xC0, bytes[0]);
	}

	/**
	 * PDU.validate() rejects a frame whose trailing checksum byte doesn't
	 * match the XOR of its payload -- the wire-corruption case that
	 * distinguishes a real protocol error from a short/malformed packet.
	 */
	@Test
	public void testCorruptedChecksumRejected() throws PduParseException {
		PDU valid = PDU.getRequest(1, 0, 0x10);
		byte[] bytes = valid.getBytes().clone();
		bytes[bytes.length - 1] = (byte) (bytes[bytes.length - 1] ^ 0xFF);

		try {
			new PDU(bytes, bytes.length);
			fail("Expected PduParseException for a corrupted checksum");
		} catch (PduParseException e) {
			assertTrue("Exception message should mention the checksum: " + e.getMessage(),
				e.getMessage().contains("checksum"));
		}
	}
}
