package com.fnt;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fnt.filecopy.MiniFileTransfer;

import fish.focus.uvms.commons.les.inmarsat.InmarsatBody;
import fish.focus.uvms.commons.les.inmarsat.InmarsatDefinition;
import fish.focus.uvms.commons.les.inmarsat.InmarsatException;
import fish.focus.uvms.commons.les.inmarsat.InmarsatFileHandler;
import fish.focus.uvms.commons.les.inmarsat.InmarsatHeader;
import fish.focus.uvms.commons.les.inmarsat.InmarsatMessage;
import fish.focus.uvms.commons.les.inmarsat.InmarsatUtils;
import fish.focus.uvms.commons.les.inmarsat.header.HeaderDataPresentation;
import fish.focus.uvms.commons.les.inmarsat.header.HeaderStruct;
import fish.focus.uvms.commons.les.inmarsat.header.HeaderType;

public class ReadInmarsat {

	private static final Logger LOGGER = LoggerFactory.getLogger(InmarsatFileHandler.class);
	private static final byte[] HEADER_PATTERN = ByteBuffer.allocate(4).put((byte) InmarsatDefinition.API_SOH)
			.put(InmarsatDefinition.API_LEAD_TEXT.getBytes()).array();
	private static final int PATTERN_LENGTH = HEADER_PATTERN.length;

	private static final Logger LOG = LoggerFactory.getLogger(ReadInmarsat.class);

	public static void main(String[] args) throws FileNotFoundException {
		if (args.length != 1) {
			LOG.info("Usage : readinmarsat  <base64 string>");
			System.out.println("Usage : readinmarsat  <base64 string>");
			return;
		}
		String base64 = args[0];
		ReadInmarsat pgm = new ReadInmarsat();
		try {
			pgm.execute(base64);
		} catch (Exception e /*IllegalArgumentException | FileNotFoundException | InmarsatException e*/) {
			System.out.println(e.toString());
		}

	}

	private void execute(String base64) throws FileNotFoundException, InmarsatException {

		/*MiniFileTransfer miniFileTransfer = new MiniFileTransfer();
		FileInputStream fis = new FileInputStream(fileName);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		miniFileTransfer.copy(fis, bos, true);*/

		byte[] msg = Base64.getDecoder().decode(base64);

		InmarsatMessage[] inmarsatMessages = byteToInmMessage(msg);
		int nMessages = inmarsatMessages.length;
		if(inmarsatMessages.length == 0){
			System.out.println("Not an inmarsat message");
			System.out.println(new String(msg));
		}
		for (int current = 0; current < nMessages; current++) {

			InmarsatMessage inmarsatMessage = inmarsatMessages[current];
			InmarsatHeader inmarsatHeader = inmarsatMessage.getHeader();
			if (inmarsatHeader != null) {
				String header = inmarsatHeader.toString();
				InmarsatBody inmarsatBody = inmarsatMessage.getBody();
				System.out.println(format(header));
				if (inmarsatBody != null) {
					String body = inmarsatBody.toString();
					System.out.println(format(body));
				}
				else {
					System.out.println("No BODY");
				}
			}
		}
	}

	private String format(String str) {
		if (str == null)
			return "";
		String[] splitedStr = str.split(";");
		int n = splitedStr.length;
		if (n < 1)
			return "";
		String ret = "";
		for (int i = 0; i < n; i++) {
			String nameValue = splitedStr[i];
			ret += nameValue;
			ret += System.lineSeparator();
		}
		return ret;
	}

	public InmarsatMessage[] byteToInmMessage(final byte[] fileBytes) {
		byte[] bytes = insertMissingData(fileBytes);

		ArrayList<InmarsatMessage> messages = new ArrayList<>();
		if (bytes == null || bytes.length <= PATTERN_LENGTH) {
			LOGGER.error("Not a valid Inmarsat Message: {}", Arrays.toString(bytes));
			return new InmarsatMessage[]{};
		}
		// Parse bytes for messages
		for (int i = 0; i < (bytes.length - PATTERN_LENGTH); i++) {
			// Find message
			if (InmarsatHeader.isStartOfMessage(bytes, i)) {
				InmarsatMessage message;
				byte[] messageBytes = Arrays.copyOfRange(bytes, i, bytes.length);
				try {
					message = new InmarsatMessage(messageBytes);
				} catch (InmarsatException e) {
					System.out.println(e.toString());
					continue;
				}

				if (message.validate()) {
					messages.add(message);
				} else {
					LOGGER.error("Could not validate position(s)");
				}
			}
		}
		return messages.toArray(new InmarsatMessage[0]); // "new InmarsatMessage[0]" is used instead of "new
		// Inmarsat[messages.size()]" to get better performance
	}


	/**
	 * Header sent doesn't always adhere to the byte contract.. This method tries to insert fix the missing parts..
	 *
	 * @param input bytes that might contain miss some bytes
	 * @return message with fixed bytes
	 */
	public byte[] insertMissingData(byte[] input) {

		byte[] output = insertMissingMsgRefNo(input);
		output = insertMissingMsgRefNo(output);         //incoming inmarsat message might be missing one or two bytes in the message reference number
		//output = insertMissingStoredTime(output);
		output = insertMissingMemberNo(output);
		output = padHeaderToCorrectLength(output);

		if (input.length < output.length) {
			System.out.println("Message fixed: " + InmarsatUtils.bytesArrayToHexString(input) +  " -> " + InmarsatUtils.bytesArrayToHexString(output));
		}
		return output;

	}

	private byte[] padHeaderToCorrectLength(final byte[] contents) {
		byte[] input = contents.clone();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		boolean insert = false, doubleInsert = false;
		int insertPosition = 0;
		for (int i = 0; i < input.length; i++) {
			// Find SOH
			if (InmarsatHeader.isStartOfMessage(input, i)) {
				byte[] header = Arrays.copyOfRange(input, i, input.length);
				HeaderType headerType = InmarsatHeader.getType(header);

				int headerLength = headerType.getHeaderLength();
				int realHeaderLength = header.length;
				if((headerLength - 1) < realHeaderLength) {  // avoid arrayoutofbounds
					int token = header[headerLength - 1];
					if (token != InmarsatDefinition.API_EOH) {
						if(header[headerLength - 2] == InmarsatDefinition.API_EOH){
							insert = true;
							insertPosition = i +  headerType.getHeaderStruct().getPositionStoredTime();
						}else if(header[headerLength - 3] == InmarsatDefinition.API_EOH){
							doubleInsert = true;
							insertPosition = i +  headerType.getHeaderStruct().getPositionStoredTime();
						}
						System.out.println("Header is " + ((doubleInsert) ? "two" : "one") + " short so we add 00 as needed to the stored time position at position: " + insertPosition);    //since we dont use stored time it is "relatively" risk-free to add positions there
						System.out.println("Incorrect header short: " + InmarsatUtils.bytesArrayToHexString(Arrays.copyOfRange(input, i, i + 20)));

					}
				}
			}
			if ((insert || doubleInsert) && ((insertPosition) == i)) {
				insert = false;
				insertPosition = 0;
				output.write((byte) InmarsatDefinition.API_UNKNOWN_ERROR); // Just 00
				if(doubleInsert){
					doubleInsert = false;
					output.write((byte) InmarsatDefinition.API_UNKNOWN_ERROR); // One more 00
				}
			}
			output.write(input[i]);
		}
		return output.toByteArray();
	}

	private byte[] insertMissingMsgRefNo(final byte[] contents) {
		byte[] input = contents.clone();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		// Missing last MsgRefNo- insert #00 in before presentation..
		boolean insert = false;
		int insertPosition = 0;
		for (int i = 0; i < input.length; i++) {
			// Find SOH
			if (InmarsatHeader.isStartOfMessage(input, i)) {
				byte[] header = Arrays.copyOfRange(input, i, input.length);
				HeaderType headerType = InmarsatHeader.getType(header);

				if (headerType.getHeaderStruct().isPresentation()) {
					HeaderDataPresentation presentation = InmarsatHeader.getDataPresentation(header);   //Data presentation checks if position 11 is a one or a two

					if (presentation == null) {
						insert = true;
						insertPosition = i + HeaderStruct.POS_REF_NO_START;
						System.out.println("Presentation is not correct so we add 00 to msg ref no at position: " + insertPosition);
						System.out.println("Incorrect header msg ref no: " + InmarsatUtils.bytesArrayToHexString(Arrays.copyOfRange(input, i, i + 20)));
					}
				}
			}
			if (insert && (insertPosition == i)) {
				insert = false;
				insertPosition = 0;
				output.write((byte) 0x00);
			}
			output.write(input[i]);
		}
		return output.toByteArray();

	}


	private byte[] insertMissingMemberNo(byte[] contents) {

		// Missing Member number - insert #FF before EOH
		// continue from previous cleaned data
		byte[] input = contents.clone();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		boolean insert = false;
		int insertPosition = 0;

		for (int i = 0; i < input.length; i++) {
			// Find SOH
			if (InmarsatHeader.isStartOfMessage(input, i)) {
				int headerLength = input[i + HeaderStruct.POS_HEADER_LENGTH];
				int expectedEOHPosition = i + headerLength - 1;
				// Check if memberNo exits
				if ((expectedEOHPosition >= input.length)
						|| ((input[expectedEOHPosition - 1] == (byte) InmarsatDefinition.API_EOH)
						&& input[expectedEOHPosition] != (byte) InmarsatDefinition.API_EOH)) {
					insert = true;
					insertPosition = expectedEOHPosition - 1;
				}

			}
			// Find EOH
			if (insert && (input[i] == (byte) InmarsatDefinition.API_EOH) && (insertPosition == i)) {
				output.write((byte) 0xFF);
				insert = false;
				insertPosition = 0;
				System.out.println("Message is missing member no, inserting FF at position: " + insertPosition);
				System.out.println("Incorrect header member no: " + InmarsatUtils.bytesArrayToHexString(Arrays.copyOfRange(input, i, i + 20)));
			}
			output.write(input[i]);
		}
		return output.toByteArray();
	}
}