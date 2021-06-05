/*
 * semanticcms-core-sitemap - Automatic sitemaps for SemanticCMS.
 * Copyright (C) 2016, 2017, 2019, 2020, 2021  AO Industries, Inc.
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

import static com.aoapps.encoding.TextInXhtmlEncoder.encodeTextInXhtml;
import static com.aoapps.encoding.TextInXhtmlEncoder.textInXhtmlEncoder;
import com.aoapps.lang.io.ContentType;
import com.aoapps.net.URIEncoder;
import com.semanticcms.core.model.Book;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.servlet.CaptureLevel;
import com.semanticcms.core.servlet.CapturePage;
import com.semanticcms.core.servlet.SemanticCMS;
import com.semanticcms.core.servlet.View;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joda.time.ReadableInstant;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

/**
 * Creates a sitemap of one book.
 *
 * @see  SiteMapInitializer  The url-patterns are dynamically registered to have a sitemap.xml in each book.
 */
public class SiteMapServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	public static final String SERVLET_PATH = "/sitemap.xml";

	private static final String CONTENT_TYPE = ContentType.XML;

	private static final Charset ENCODING = StandardCharsets.UTF_8;

	private static Book getBook(SemanticCMS semanticCMS, HttpServletRequest req) {
		// Find the book for this request
		String servletPath = req.getServletPath();
		if(!servletPath.endsWith(SERVLET_PATH)) {
			// Incorrect mapping, treat as not found
			return null;
		}
		String bookName = servletPath.substring(0, servletPath.length() - SERVLET_PATH.length());
		if(bookName.isEmpty()) bookName = "/";
		return semanticCMS.getBooks().get(bookName);
	}

	/**
	 * Gets the most recent of the last modified of all views applicable to the given
	 * book and accessible to the search engines.  If any view returns {@code null}
	 * from {@link View#getLastModified(javax.servlet.ServletContext, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.semanticcms.core.model.Page)},
	 * the sitemap overall will not have any last modified time.
	 *
	 * @return  the most recently last modified or {@code null} if unknown
	 */
	static ReadableInstant getLastModified(
		final ServletContext servletContext,
		final HttpServletRequest req,
		final HttpServletResponse resp,
		final SortedSet<View> views,
		final Book book
	) throws ServletException, IOException {
		// The most recent is kept here, but set to null the first time a missing
		// per page/view last modified time is found
		final ReadableInstant[] result = new ReadableInstant[1];
		CapturePage.traversePagesAnyOrder(
			servletContext,
			req,
			resp,
			book.getContentRoot(),
			CaptureLevel.META,
			(Page page) -> {
				// TODO: Chance for more concurrency here by view?
				for(View view : views) {
					if(
						view.getAllowRobots(servletContext, req, resp, page)
						&& view.isApplicable(servletContext, req, resp, page)
					) {
						ReadableInstant lastModified = view.getLastModified(servletContext, req, resp, page);
						if(lastModified == null) {
							// Stop searching, return null for this book
							result[0] = null;
							return false;
						} else {
							if(
								result[0] == null
								|| lastModified.compareTo(result[0]) > 0
							) {
								result[0] = lastModified;
							}
						}
					}
				}
				return null;
			},
			Page::getChildRefs,
			(PageRef childPage) -> book.equals(childPage.getBook())
		);
		return result[0];
	}

	/**
	 * The response is not given to getLastModified, but we need it for captures to get
	 * the last modified.
	 */
	private static final String RESPONSE_IN_REQUEST_ATTRIBUTE = SiteMapServlet.class.getName() + ".responseInRequest";

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		Object old = req.getAttribute(RESPONSE_IN_REQUEST_ATTRIBUTE);
		try {
			req.setAttribute(RESPONSE_IN_REQUEST_ATTRIBUTE, resp);
			super.service(req, resp);
		} finally {
			req.setAttribute(RESPONSE_IN_REQUEST_ATTRIBUTE, old);
		}
	}

	@Override
	protected long getLastModified(HttpServletRequest req) {
		ServletContext servletContext = getServletContext();
		SemanticCMS semanticCMS = SemanticCMS.getInstance(servletContext);
		Book book = getBook(semanticCMS, req);
		if(book == null) {
			log("Book not found: " + req.getServletPath());
			return -1;
		} else {
			try {
				ReadableInstant lastModified = getLastModified(
					getServletContext(),
					req,
					(HttpServletResponse)req.getAttribute(RESPONSE_IN_REQUEST_ATTRIBUTE),
					semanticCMS.getViews(),
					book
				);
				return lastModified == null ? -1 : lastModified.getMillis();
			} catch(ServletException | IOException e) {
				log("getLastModified failed", e);
				return -1;
			}
		}
	}

	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final ServletContext servletContext = getServletContext();
		SemanticCMS semanticCMS = SemanticCMS.getInstance(servletContext);
		final Book book = getBook(semanticCMS, req);
		if(book == null) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		final SortedSet<View> views = semanticCMS.getViews();

		final SortedSet<SiteMapUrl> urls = new TreeSet<>();
		CapturePage.traversePagesAnyOrder(
			servletContext,
			req,
			resp,
			book.getContentRoot(),
			CaptureLevel.META,
			(Page page) -> {
				assert page.getPageRef().getBook().equals(book);
				// TODO: Concurrency: Any benefit to processing each view concurrently?  allowRobots and isApplicable can be expensive but should also benefit from capture caching
				for(View view : views) {
					if(
						view.getAllowRobots(servletContext, req, resp, page)
						&& view.isApplicable(servletContext, req, resp, page)
					) {
						urls.add(
							new SiteMapUrl(
								view.getCanonicalUrl(servletContext, req, resp, page),
								view.getLastModified(servletContext, req, resp, page)
							)
						);
					}
				}
				return null;
			},
			Page::getChildRefs,
			(PageRef childPage) -> book.equals(childPage.getBook())
		);

		final DateTimeFormatter iso8601 = ISODateTimeFormat.dateTime();

		resp.resetBuffer();
		resp.setContentType(CONTENT_TYPE);
		resp.setCharacterEncoding(ENCODING.name());
		PrintWriter out = resp.getWriter();

		out.println("<?xml version=\"1.0\" encoding=\"" + ENCODING + "\"?>");
		out.println("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
		for(SiteMapUrl url : urls) {
			out.println("    <url>");
			out.print("        <loc>");
			// RFC 3986 US-ASCII, although RFC 3987 might be possible as per https://www.google.com/sitemaps/faq.html#faq_xml_encoding
			URIEncoder.encodeURI(
				url.getLoc(),
				textInXhtmlEncoder,
				out
			);
			out.println("</loc>");
			ReadableInstant lastmod = url.getLastmod();
			if(lastmod != null) {
				out.print("        <lastmod>");
				encodeTextInXhtml(iso8601.print(lastmod), out);
				out.println("</lastmod>");
			}
			out.println("    </url>");
		}
		out.println("</urlset>");
	}
}
