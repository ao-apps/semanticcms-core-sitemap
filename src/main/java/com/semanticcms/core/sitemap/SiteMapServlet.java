/*
 * semanticcms-core-sitemap - Automatic sitemaps for SemanticCMS.
 * Copyright (C) 2016, 2017  AO Industries, Inc.
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

import static com.aoindustries.encoding.TextInXhtmlEncoder.encodeTextInXhtml;
import com.aoindustries.net.Path;
import com.aoindustries.util.WrappedException;
import com.aoindustries.validation.ValidationException;
import com.semanticcms.core.model.ChildRef;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.pages.CaptureLevel;
import com.semanticcms.core.servlet.Book;
import com.semanticcms.core.servlet.CapturePage;
import com.semanticcms.core.servlet.SemanticCMS;
import com.semanticcms.core.servlet.View;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;
import java.util.SortedSet;
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

	private static final String CONTENT_TYPE = "application/xml";

	private static final String ENCODING = "UTF-8";

	private static Book getBook(SemanticCMS semanticCMS, HttpServletRequest req) {
		// Find the book for this request
		String servletPath = req.getServletPath();
		if(!servletPath.endsWith(SERVLET_PATH)) {
			// Incorrect mapping, treat as not found
			return null;
		}
		Path bookPath;
		{
			String bookName = servletPath.substring(0, servletPath.length() - SERVLET_PATH.length());
			if(bookName.isEmpty()) {
				bookPath = Path.ROOT;
			} else {
				try {
					bookPath = Path.valueOf(bookName);
				} catch(ValidationException e) {
					throw new WrappedException(e);
				}
			}
		}
		Book book = semanticCMS.getPublishedBooks().get(bookPath);
		if(book == null) {
			// Book not published
			return null;
		}
		if(!book.isAccessible()) {
			// Book not accessible
			return null;
		}
		return book;
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
		assert
			book.equals(SemanticCMS.getInstance(servletContext).getPublishedBooks().get(book.getBookRef().getPath()))
			: "Book not published: " + book
		;
		assert book.isAccessible();
		// The most recent is kept here, but set to null the first time a missing
		// per page/view last modified time is found
		final ReadableInstant[] result = new ReadableInstant[1];
		CapturePage.traversePagesAnyOrder(
			servletContext,
			req,
			resp,
			book.getContentRoot(),
			CaptureLevel.META,
			new CapturePage.PageHandler<Boolean>() {
				@Override
				public Boolean handlePage(Page page) throws ServletException, IOException {
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
				}
			},
			new CapturePage.TraversalEdges() {
				@Override
				public Set<ChildRef> getEdges(Page page) {
					return page.getChildRefs();
				}
			},
			new CapturePage.EdgeFilter() {
				@Override
				public boolean applyEdge(PageRef childPage) {
					return book.getBookRef().equals(childPage.getBookRef());
				}
			}
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
		final ServletContext servletContext = getServletContext();
		SemanticCMS semanticCMS = SemanticCMS.getInstance(servletContext);
		final Book book = getBook(semanticCMS, req);
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
			} catch(ServletException e) {
				log("getLastModified failed", e);
				return -1;
			} catch(IOException e) {
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
		final DateTimeFormatter iso8601 = ISODateTimeFormat.dateTime();
		resp.resetBuffer();
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
			new CapturePage.PageDepthHandler<Void>() {
				@Override
				public Void handlePage(Page page, int depth) throws ServletException, IOException {
					assert page.getPageRef().getBookRef().equals(book.getBookRef());
					// TODO: Concurrency: Any benefit to processing each view concurrently?  allowRobots and isApplicable can be expensive but should also benefit from capture caching
					for(View view : views) {
						if(
							view.getAllowRobots(servletContext, req, resp, page)
							&& view.isApplicable(servletContext, req, resp, page)
						) {
							out.println("    <url>");
							out.print("        <loc>");
							encodeTextInXhtml(view.getCanonicalUrl(servletContext, req, resp, page), out);
							out.println("</loc>");
							ReadableInstant lastmod = view.getLastModified(servletContext, req, resp, page);
							if(lastmod != null) {
								out.print("        <lastmod>");
								encodeTextInXhtml(iso8601.print(lastmod), out);
								out.println("</lastmod>");
							}
							out.println("    </url>");
						}
					}
					return null;
				}
			},
			new CapturePage.TraversalEdges() {
				@Override
				public Set<ChildRef> getEdges(Page page) {
					return page.getChildRefs();
				}
			},
			new CapturePage.EdgeFilter() {
				@Override
				public boolean applyEdge(PageRef childPage) {
					return book.getBookRef().equals(childPage.getBookRef());
				}
			},
			null
		);
		out.println("</urlset>");
	}
}
