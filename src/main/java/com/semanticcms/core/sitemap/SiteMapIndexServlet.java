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
import com.semanticcms.core.servlet.SemanticCMS;
import com.semanticcms.core.servlet.View;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Creates a site map index of all per-book sitemaps.
 */
@WebServlet(SiteMapIndexServlet.SERVLET_PATH)
public class SiteMapIndexServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	public static final String SERVLET_PATH = "/sitemap-index.xml";

	private static final String CONTENT_TYPE = "application/xml";

	private static final String ENCODING = "UTF-8";

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.reset();
		resp.setContentType(CONTENT_TYPE);
		resp.setCharacterEncoding(ENCODING);
		PrintWriter out = resp.getWriter();
		out.println("<?xml version=\"1.0\" encoding=\"" + ENCODING + "\"?>");
		out.println("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
		SemanticCMS semanticCMS = SemanticCMS.getInstance(getServletContext());
		for(Book book : semanticCMS.getBooks().values()) {
			if(
				hasSiteMapUrl(
					getServletContext(),
					req,
					resp,
					semanticCMS.getViews(),
					book,
					book.getContentRoot(),
					new HashSet<PageRef>()
				)
			) {
				out.println("    <sitemap>");
				out.print("        <loc>");
				ServletUtil.getAbsoluteURL(
					req,
					resp.encodeURL(book.getPathPrefix() + SiteMapServlet.SERVLET_PATH),
					textInXhtmlEncoder,
					out
				);
				out.println("</loc>");
				out.println("    </sitemap>");
			}
		}
		out.println("</sitemapindex>");
	}

	/**
	 * Checks if the sitemap has at least one page.
	 */
	private static boolean hasSiteMapUrl(
		ServletContext servletContext,
		HttpServletRequest req,
		HttpServletResponse resp,
		SortedSet<View> views,
		Book book,
		PageRef pageRef,
		Set<PageRef> visited
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
		for(View view : views) {
			if(
				view.getAllowRobots(servletContext, req, resp, page)
				&& view.isApplicable(servletContext, req, resp, page)
			) {
				return true;
			}
		}
		// Check all child pages that are in the same book
		for(PageRef childRef : page.getChildPages()) {
			if(
				book.equals(childRef.getBook())
				&& !visited.contains(childRef)
				&& hasSiteMapUrl(
					servletContext,
					req,
					resp,
					views,
					book,
					childRef,
					visited
				)
			) {
				return true;
			}
		}
		return false;
	}
}
