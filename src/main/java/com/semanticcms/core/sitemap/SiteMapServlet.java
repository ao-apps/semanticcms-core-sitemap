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
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.servlet.CaptureLevel;
import com.semanticcms.core.servlet.CapturePage;
import com.semanticcms.core.servlet.SemanticCMS;
import com.semanticcms.core.servlet.View;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.Set;
import java.util.SortedSet;
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
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final ServletContext servletContext = getServletContext();
		// Find the book for this request
		String servletPath = req.getServletPath();
		if(!servletPath.endsWith(SERVLET_PATH)) {
			// Incorrect mapping, treat as not found
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		String bookName = servletPath.substring(0, servletPath.length() - SERVLET_PATH.length());
		if(bookName.isEmpty()) bookName = "/";
		SemanticCMS semanticCMS = SemanticCMS.getInstance(getServletContext());
		final Book book = semanticCMS.getBooks().get(bookName);
		if(book == null) {
			// Book not found
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		final SortedSet<View> views = semanticCMS.getViews();
		resp.reset();
		resp.setContentType(CONTENT_TYPE);
		resp.setCharacterEncoding(ENCODING);
		final PrintWriter out = resp.getWriter();
		out.println("<?xml version=\"1.0\" encoding=\"" + ENCODING + "\"?>");
		out.println("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
		CapturePage.traversePagesDepthFirst(
			servletContext,
			req,
			resp,
			book.getContentRoot(),
			CaptureLevel.META,
			new CapturePage.PageHandler<Void>() {
				@Override
				public Void handlePage(Page page) throws ServletException, IOException {
					PageRef pageRef = page.getPageRef();
					assert pageRef.getBook().equals(book);
					// TODO: Concurrency: Any benefit to processing each view concurrently?  allowRobots and isApplicable can be expensive but should also benefit from capture caching
					for(View view : views) {
						if(
							view.getAllowRobots(servletContext, req, resp, page)
							&& view.isApplicable(servletContext, req, resp, page)
						) {
							out.println("    <url>");
							out.print("        <loc>");
							String servletPath = pageRef.getServletPath();
							if(!view.isDefault()) {
								servletPath += "?view=" + URLEncoder.encode(view.getName(), resp.getCharacterEncoding());
							}
							ServletUtil.getAbsoluteURL(
								req,
								resp.encodeURL(servletPath),
								textInXhtmlEncoder,
								out
							);
							out.println("</loc>");
							out.println("    </url>");
						}
					}
					return null;
				}
			},
			new CapturePage.TraversalEdges() {
				@Override
				public Set<PageRef> getEdges(Page page) {
					return page.getChildPages();
				}
			},
			new CapturePage.EdgeFilter() {
				@Override
				public boolean applyEdge(PageRef childPage) {
					return book.equals(childPage.getBook());
				}
			},
			null
		);
		out.println("</urlset>");
	}
}
