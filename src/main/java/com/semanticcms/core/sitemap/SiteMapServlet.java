/*
 * semanticcms-core-sitemap - Automatic sitemaps for SemanticCMS.
 * Copyright (C) 2016  AO Industries, Inc.
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

import static com.aoindustries.encoding.TextInXhtmlEncoder.textInXhtmlEncoder;
import com.aoindustries.servlet.http.ServletUtil;
import com.semanticcms.core.model.Book;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.servlet.CaptureLevel;
import com.semanticcms.core.servlet.CapturePage;
import com.semanticcms.core.servlet.PageUtils;
import com.semanticcms.core.servlet.SemanticCMS;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Creates a sitemap of one book.
 *
 * @see  SiteMapInitializer  The url-patterns are dynamically registered to have a sitemap.xml in each book.
 */
public class SiteMapServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	public static final String SERVLET_PATH = "/sitemap.xml";

	private static final String CONTENT_TYPE = "application/xml";

	private static final String ENCODING = "UTF-8";

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		ServletContext servletContext = getServletContext();
		// Find the book for this request
		String servletPath = req.getServletPath();
		if(!servletPath.endsWith(SERVLET_PATH)) {
			// Incorrect mapping, treat as not found
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		String bookName = servletPath.substring(0, servletPath.length() - SERVLET_PATH.length());
		if(bookName.isEmpty()) bookName = "/";
		Book book = SemanticCMS.getInstance(servletContext).getBooks().get(bookName);
		if(book == null) {
			// Book not found
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		resp.reset();
		resp.setContentType(CONTENT_TYPE);
		resp.setCharacterEncoding(ENCODING);
		PrintWriter out = resp.getWriter();
		out.println("<?xml version=\"1.0\" encoding=\"" + ENCODING + "\"?>");
		out.println("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
		printUrls(
			servletContext,
			req,
			resp,
			book,
			book.getContentRoot(),
			new HashSet<PageRef>(),
			out
		);
		out.println("</urlset>");
	}

	private static void printUrls(
		ServletContext servletContext,
		HttpServletRequest req,
		HttpServletResponse resp,
		Book book,
		PageRef pageRef,
		Set<PageRef> visited,
		PrintWriter out
	) throws ServletException, IOException {
		assert pageRef.getBook().equals(book);
		assert !visited.contains(pageRef);
		visited.add(pageRef);
		com.semanticcms.core.model.Page page = CapturePage.capturePage(
			servletContext,
			req,
			resp,
			pageRef,
			CaptureLevel.PAGE
		);
		if(PageUtils.findAllowRobots(servletContext, req, resp, page)) {
			out.println("    <url>");
			out.print("        <loc>");
			ServletUtil.getAbsoluteURL(
				req,
				resp.encodeURL(pageRef.getServletPath()),
				textInXhtmlEncoder,
				out
			);
			out.println("</loc>");
			out.println("    </url>");
		}
		// Add all child pages that are in the same book
		for(PageRef childRef : page.getChildPages()) {
			if(
				book.equals(childRef.getBook())
				&& !visited.contains(childRef)
			) {
				printUrls(
					servletContext,
					req,
					resp,
					book,
					childRef,
					visited,
					out
				);
			}
		}
	}
}
