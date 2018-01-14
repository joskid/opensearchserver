/*
 * Copyright 2017-2018 Emmanuel Keller / Jaeksoft
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.jaeksoft.opensearchserver.front;

import com.jaeksoft.opensearchserver.model.WebCrawlRecord;
import com.qwazr.crawler.web.WebCrawlStatus;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

class CrawlerWebStatus extends IndexBase {

	private final static String TEMPLATE_INDEX = "web_crawl_status.ftl";

	private final Map<UUID, WebCrawlRecord> webCrawlRecords;
	private final WebCrawlRecord webCrawlRecord;
	private final WebCrawlStatus webCrawlStatus;

	CrawlerWebStatus(final IndexServlet servlet, final String indexName, final String webCrawlUuid,
			final HttpServletRequest request, final HttpServletResponse response) throws IOException {
		super(servlet, indexName, request, response);
		webCrawlRecords = new LinkedHashMap<>();
		webCrawlsService.fillMap(indexName, webCrawlRecords);
		webCrawlRecord = webCrawlRecords.get(UUID.fromString(webCrawlUuid));
		webCrawlStatus = webCrawlsService.getCrawlStatus(indexName, webCrawlUuid);
	}

	@Override
	void doGet() throws IOException, ServletException {
		if (webCrawlRecord == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		request.setAttribute("indexName", indexName);
		request.setAttribute("webCrawlRecord", webCrawlRecord);
		request.setAttribute("webCrawlStatus", webCrawlStatus);
		doTemplate(TEMPLATE_INDEX);
	}
}
