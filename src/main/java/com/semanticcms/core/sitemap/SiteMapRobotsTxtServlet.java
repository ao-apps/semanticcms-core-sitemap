/*
 * semanticcms-core-sitemap - Automatic sitemaps for SemanticCMS.
 * Copyright (C) 2016, 2019  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of semanticcms-core-sitemap.
 *
 * semanticcms-core-sitemap is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * semanticcms-core-sitemap is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with semanticcms-core-sitemap.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.semanticcms.core.sitemap;

import com.aoindustries.servlet.ServletUtil;
import com.aoindustries.servlet.http.HttpServletUtil;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Adds the sitemap index to /robots.txt
 */
@WebServlet(
	urlPatterns = SiteMapRobotsTxtServlet.SERVLET_PATH,
	loadOnStartup = 1
)
public class SiteMapRobotsTxtServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	public static final String SERVLET_PATH = "/robots.txt";

	private static final String CONTENT_TYPE = "text/plain";

	private static final Charset ENCODING = StandardCharsets.UTF_8;

	/**
	 * TODO: Consider a Maven-filter-provided build time annotation instead of using init time.
	 *       This would give consistent results between nodes in a cluster, as long as the same
	 *       build of software deployed to each.
	 */
	private long initTime;

	@Override
	public void init() {
		initTime = System.currentTimeMillis();
	}

	@Override
	protected long getLastModified(HttpServletRequest req) {
		return initTime;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.resetBuffer();
		resp.setContentType(CONTENT_TYPE);
		resp.setCharacterEncoding(ENCODING.name());
		PrintWriter out = resp.getWriter();
		out.println("User-agent: *");
		out.println("Allow: /");
		out.print("Sitemap: ");
		HttpServletUtil.getAbsoluteURL(
			req,
			resp.encodeURL(
				ServletUtil.encodeURI(
					SiteMapIndexServlet.SERVLET_PATH,
					resp
				)
			),
			out
		);
		out.println();
	}
}
