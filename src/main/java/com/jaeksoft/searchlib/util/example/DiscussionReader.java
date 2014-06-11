package com.jaeksoft.searchlib.util.example;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.cxf.jaxrs.client.WebClient;

import au.com.bytecode.opencsv.CSVReader;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.jaeksoft.searchlib.analysis.LanguageEnum;
import com.jaeksoft.searchlib.template.TemplateList;
import com.jaeksoft.searchlib.util.IOUtils;
import com.jaeksoft.searchlib.util.JsonUtils;
import com.jaeksoft.searchlib.util.StringUtils;
import com.jaeksoft.searchlib.webservice.document.DocumentUpdate;
import com.jaeksoft.searchlib.webservice.document.DocumentUpdate.Field;

public class DiscussionReader {

	private final static TypeReference<List<List<String>>> discussionType = new TypeReference<List<List<String>>>() {
	};

	private final static class DiscussionItem {

		private final int type;
		private final String message;

		// private final String timeStamp;

		private DiscussionItem(int discussion_pos, int message_pos,
				List<String> discussionItem) {
			type = Integer.parseInt(discussionItem.get(0));
			message = StringEscapeUtils.unescapeHtml(StringUtils
					.removeTag(discussionItem.get(2)));
		}

	}

	private static List<DocumentUpdate> indexDocumentUpdates = new ArrayList<DocumentUpdate>();
	private static WebClient webClient = null;
	private static String indexName = null;
	private static int conversationCount = 0;

	private final static DocumentUpdate getDocumentUpdate(String question,
			String answer, String conversation, int pos) {
		DocumentUpdate documentUpdate = new DocumentUpdate(LanguageEnum.FRENCH);
		documentUpdate.fields.add(new Field("question", question, null));
		documentUpdate.fields.add(new Field("answer", answer, null));
		documentUpdate.fields
				.add(new Field("pos", Integer.toString(pos), null));
		documentUpdate.fields
				.add(new Field("conversation", conversation, null));
		return documentUpdate;
	}

	private final static void checkResponse(Response response)
			throws IOException {
		if (response.getStatus() != 200)
			throw new IOException(response.getStatus() + " "
					+ response.getStatusInfo().getReasonPhrase());

	}

	private final static void index(boolean bForce) throws IOException {
		if (!bForce && indexDocumentUpdates.size() < 256)
			return;
		if (indexDocumentUpdates.isEmpty())
			return;
		String json = JsonUtils.toJsonString(indexDocumentUpdates);
		Response response = null;
		try {
			webClient.reset();
			response = webClient
					.path("/services/rest/index/{index_name}/document",
							indexName).accept(MediaType.APPLICATION_JSON)
					.type(MediaType.APPLICATION_JSON).put(json);
			checkResponse(response);
		} finally {
			if (response != null)
				response.close();
		}
		indexDocumentUpdates.clear();
		System.out.println("Indexed conversations: " + conversationCount);
	}

	// Type 2 = question
	// Type 1 = answer

	private final static void buildQA(List<DiscussionItem> discussion)
			throws IOException {
		StringBuilder sbConv = new StringBuilder();
		for (DiscussionItem discussionItem : discussion) {
			if (discussionItem.type == 2 || discussionItem.type == 1) {
				sbConv.append(discussionItem.message);
				sbConv.append(' ');
			}
		}
		int pos = 0;
		int lastType = 0;
		String conversation = sbConv.toString().trim();
		StringBuilder sbQuestion = new StringBuilder();
		StringBuilder sbAnswer = new StringBuilder();
		for (DiscussionItem discussionItem : discussion) {
			switch (discussionItem.type) {
			case 2:
				if (lastType != 2) {
					sbQuestion = new StringBuilder();
					pos++;
				}
				sbQuestion.append(discussionItem.message);
				sbQuestion.append(". ");
				lastType = 2;
				break;
			case 1:
				if (lastType != 1)
					sbAnswer = new StringBuilder();
				sbAnswer.append(discussionItem.message);
				sbAnswer.append(". ");
				addQA(sbQuestion.toString(), discussionItem.message,
						conversation, pos);
				lastType = 1;
				break;
			default:
				break;
			}
		}
		index(false);
	}

	private final static void addQA(String question, String answer,
			String conversation, int pos) {
		if (question == null || answer == null)
			return;
		question = question.trim();
		answer = answer.trim();
		if (question.isEmpty() || answer.isEmpty())
			return;
		indexDocumentUpdates.add(getDocumentUpdate(question, answer,
				conversation, pos));
	}

	private final static void readJson(int discussion_pos, Date date,
			String json) throws JsonMappingException, IOException {
		json = StringUtils.replaceChars(json, "\u0000", "");
		json = StringUtils.replaceChars(json, "\\'", "'");
		json = StringUtils.replaceChars(json, "\r\t\n\u000b", "     ");
		List<List<String>> discussion = JsonUtils.getObject("[" + json + "]",
				discussionType);
		if (CollectionUtils.isEmpty(discussion))
			return;
		List<DiscussionItem> discussionList = new ArrayList<DiscussionItem>();
		int item_pos = 0;
		for (List<String> discussionItem : discussion)
			discussionList.add(new DiscussionItem(discussion_pos, ++item_pos,
					discussionItem));
		buildQA(discussionList);
	}

	private final static int readCsv(String path) throws Exception {
		FileReader fileReader = null;
		CSVReader csvReader = null;
		conversationCount = 0;
		String currentJson = null;
		try {
			fileReader = new FileReader(path);
			csvReader = new CSVReader(fileReader, ',');
			String[] nextLine;
			SimpleDateFormat dateFormat = new SimpleDateFormat(
					"yyyy-MM-dd HH:mm:ss");
			while ((nextLine = csvReader.readNext()) != null) {
				if (++conversationCount == 1)
					continue;
				currentJson = nextLine[0];
				Date date = dateFormat.parse(nextLine[1]);
				try {
					readJson(conversationCount, date, currentJson);
				} catch (JsonParseException e) {
					System.err.println("Error in line: " + conversationCount
							+ " " + e.getMessage());
				}
			}
			index(true);
			return conversationCount;
		} catch (Exception e) {
			System.err.println("Error in line: " + conversationCount + " "
					+ e.getMessage() + " " + currentJson);
			throw e;
		} finally {
			IOUtils.close(csvReader, fileReader);
		}
	}

	private final static WebClient getWebClient(String url) {
		WebClient webClient = WebClient.create(url,
				Collections.singletonList(new JacksonJsonProvider()));
		WebClient.getConfig(webClient).getRequestContext()
				.put("use.async.http.conduit", Boolean.TRUE);
		return webClient;
	}

	private final static String getResource(String name) throws IOException {
		InputStream is = DiscussionReader.class.getResourceAsStream(name);
		return IOUtils.toString(is);
	}

	private final static void reCreateIndex() throws IOException {
		Response response = null;
		try {
			// Delete old index
			webClient.reset();
			response = webClient
					.path("/services/rest/index/{index_name}", indexName)
					.accept(MediaType.APPLICATION_JSON).delete();
			checkResponse(response);
			response.close();
			response = null;
			// Create index
			webClient.reset();
			response = webClient
					.path("/services/rest/index/{index_name}/template/{template_name}",
							indexName, TemplateList.EMPTY_INDEX.name())
					.accept(MediaType.APPLICATION_JSON).post(null);
			checkResponse(response);
			response.close();
			response = null;
			// Create schema
			String json = getResource("schema.json");
			webClient.reset();
			response = webClient
					.path("/services/rest/index/{index_name}/field", indexName)
					.accept(MediaType.APPLICATION_JSON)
					.type(MediaType.APPLICATION_JSON).put(json);
			checkResponse(response);
			response.close();
			response = null;
			// Set default field
			webClient.reset();
			response = webClient.accept(MediaType.APPLICATION_JSON)
					.path("/services/rest/index/{index_name}/field", indexName)
					.query("default", "question").post(null);
			checkResponse(response);
			response.close();
			response = null;
			// Create query
			json = getResource("query.json");
			webClient.reset();
			response = webClient
					.path("/services/rest/index/{index_name}/search/field/{request_name}",
							indexName, "search")
					.accept(MediaType.APPLICATION_JSON)
					.type(MediaType.APPLICATION_JSON).put(json);
			checkResponse(response);
			response.close();
			response = null;
		} finally {
			if (response != null)
				response.close();
		}
	}

	public final static void main(String[] args) throws Exception {
		webClient = getWebClient(args[0]);
		indexName = args[1];
		reCreateIndex();
		int i = readCsv(args[2]);
		webClient.close();
		System.out.println(i + " lines.");
		System.exit(1);
	}
}
