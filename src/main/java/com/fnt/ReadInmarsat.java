package com.fnt;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

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
			LOG.info("Usage : readinmarsat  <fullpath to file>");
			return;
		}
		String fileName = args[0];
		ReadInmarsat pgm = new ReadInmarsat();
		try {
			pgm.execute(fileName);
		} catch (IllegalArgumentException | FileNotFoundException | InmarsatException e) {
			LOG.error(e.toString());
		}

	}

	private void execute(String fileName) throws FileNotFoundException, InmarsatException {

		if (fileName == null) {
			throw new IllegalArgumentException("no filename provided");
		}
		File f = new File(fileName);
		if (!f.exists()) {
			throw new IllegalArgumentException("file does not exist");
		}

		MiniFileTransfer miniFileTransfer = new MiniFileTransfer();
		FileInputStream fis = new FileInputStream(fileName);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		miniFileTransfer.copy(fis, bos, true);
		byte[] msg = bos.toByteArray();

		InmarsatMessage[] inmarsatMessages = byteToInmMessage(msg);
		int nMessages = inmarsatMessages.length;

		for (int current = 0; current < nMessages; current++) {

			InmarsatMessage inmarsatMessage = inmarsatMessages[current];
			InmarsatHeader inmarsatHeader = inmarsatMessage.getHeader();
			if (inmarsatHeader != null) {
				String header = inmarsatHeader.toString();
				LOG.info(format(header));
				InmarsatBody inmarsatBody = inmarsatMessage.getBody();
				if (inmarsatBody != null) {
					String body = inmarsatBody.toString();
					LOG.info(format(body));
				}
				else {
					LOG.info("No BODY");
				}
			}
		}
	}

	public InmarsatMessage[] byteToInmMessage(final byte[] fileBytes) {

		byte[] bytes = insertMissingData(fileBytes);

		ArrayList<InmarsatMessage> messages = new ArrayList<>();

		if (bytes == null || bytes.length <= PATTERN_LENGTH) {
			LOG.info("File is not a valid Inmarsat Message");
			return new InmarsatMessage[] {};
		}
		boolean errorInfile = false;
		// Parse bytes for messages
		for (int i = 0; i < (bytes.length - PATTERN_LENGTH); i++) {
			// Find message
			if (InmarsatHeader.isStartOfMessage(bytes, i)) {
				InmarsatMessage message;
				byte[] messageBytes = Arrays.copyOfRange(bytes, i, bytes.length);
				try {
					message = new InmarsatMessage(messageBytes);
				} catch (InmarsatException e) {
					LOGGER.info(e.toString(), e);
					errorInfile = true;
					continue;
				}

				if (message.validate()) {
					messages.add(message);
				} else {
					LOGGER.info("Message in file rejected:{}", message);
				}
			}
		}

		if (errorInfile) {
			LOGGER.info("Error in file detected");
		}
		return messages.toArray(new InmarsatMessage[messages.size()]);
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

	public byte[] insertMissingData(byte[] input) {
		byte[] output = insertMissingMsgRefNo(input);
		output = insertMissingStoredTime(output);
		output = insertMissingMemberNo(output);

		if (LOGGER.isDebugEnabled() && (input.length < output.length)) {
			LOGGER.info("Message fixed: {} -> {}", InmarsatUtils.bytesArrayToHexString(input),
					InmarsatUtils.bytesArrayToHexString(output));
		}
		return output;

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
					HeaderDataPresentation presentation = InmarsatHeader.getDataPresentation(header);

					if (presentation == null) {
						LOGGER.info("Presentation is not correct so we add 00 to msg ref no");
						insert = true;
						insertPosition = i + HeaderStruct.POS_REF_NO_END;
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

	private byte[] insertMissingStoredTime(byte[] contents) {
		// Missing Date byte (incorrect date..)? - insert #00 in date first position
		byte[] input = contents.clone();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		boolean insert = false;
		int insertPosition = 0;

		for (int i = 0; i < input.length; i++) {
			// Find SOH
			if (InmarsatHeader.isStartOfMessage(input, i)) {
				byte[] header = Arrays.copyOfRange(input, i, input.length);
				HeaderType headerType = InmarsatHeader.getType(header);

				Date headerDate = InmarsatHeader.getStoredTime(header);

				if (headerDate.after(Calendar.getInstance(InmarsatDefinition.API_TIMEZONE).getTime())) {
					LOGGER.info("Stored time is not correct so we add 00 to in first position");
					insert = true;
					insertPosition = i + headerType.getHeaderStruct().getPositionStoredTime();
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
				LOGGER.info("Message is missing member no");
				output.write((byte) 0xFF);
				insert = false;
				insertPosition = 0;

			}
			output.write(input[i]);
		}
		return output.toByteArray();
	}

}
