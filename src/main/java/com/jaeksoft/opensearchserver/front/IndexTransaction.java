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

import com.qwazr.search.index.IndexStatus;
import com.qwazr.utils.StringUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

class IndexTransaction extends IndexBase {

	private final static String TEMPLATE_INDEX = "index.ftl";

	IndexTransaction(final IndexServlet indexServlet, final String indexName, final HttpServletRequest request,
			final HttpServletResponse response) {
		super(indexServlet, indexName, request, response);
	}

	void delete() throws IOException, ServletException {
		final String indexName = request.getParameter("indexName");
		if (!StringUtils.isBlank(indexName)) {
			if (indexName.equals(this.indexName)) {
				indexesService.deleteIndex(indexName);
				addMessage(ServletTransaction.Css.info, null, "Index \"" + indexName + "\" deleted");
				response.sendRedirect("/");
				return;
			} else
				addMessage(ServletTransaction.Css.warning, null, "Please confirm the name of the index to delete");
		}
		doGet();
	}

	@Override
	void doGet() throws IOException, ServletException {
		request.setAttribute("indexName", indexName);
		final IndexStatus status = indexesService.getIndex(indexName).getIndexStatus();
		request.setAttribute("indexSize", status.segments_size);
		request.setAttribute("indexCount", status.num_docs);
		doTemplate(TEMPLATE_INDEX);
	}

}
