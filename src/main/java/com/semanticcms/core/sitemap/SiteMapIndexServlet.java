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

import static com.aoindustries.encoding.TextInXhtmlEncoder.encodeTextInXhtml;
import static com.aoindustries.encoding.TextInXhtmlEncoder.textInXhtmlEncoder;
import com.aoindustries.io.TempFileList;
import com.aoindustries.servlet.filter.TempFileContext;
import com.aoindustries.servlet.http.ServletUtil;
import com.aoindustries.util.Tuple2;
import com.semanticcms.core.model.Book;
import com.semanticcms.core.model.ChildRef;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.servlet.CaptureLevel;
import com.semanticcms.core.servlet.CapturePage;
import com.semanticcms.core.servlet.CountConcurrencyFilter;
import com.semanticcms.core.servlet.SemanticCMS;
import com.semanticcms.core.servlet.View;
import com.semanticcms.core.servlet.util.HttpServletSubRequest;
import com.semanticcms.core.servlet.util.HttpServletSubResponse;
import com.semanticcms.core.servlet.util.UnmodifiableCopyHttpServletRequest;
import com.semanticcms.core.servlet.util.UnmodifiableCopyHttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joda.time.ReadableInstant;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

/**
 * Creates a site map index of all per-book sitemaps.
 */
@WebServlet(SiteMapIndexServlet.SERVLET_PATH)
public class SiteMapIndexServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final Logger logger = Logger.getLogger(SiteMapIndexServlet.class.getName());

	public static final String SERVLET_PATH = "/sitemap-index.xml";

	private static final String CONTENT_TYPE = "application/xml";

	private static final String ENCODING = "UTF-8";

	private static void writeSitemap(
		HttpServletRequest req,
		HttpServletResponse resp,
		PrintWriter out,
		Book book,
		ReadableInstant lastmod,
		DateTimeFormatter iso8601
	) throws IOException {
		out.println("    <sitemap>");
		out.print("        <loc>");
		ServletUtil.getAbsoluteURL(
			req,
			resp.encodeURL(book.getPathPrefix() + SiteMapServlet.SERVLET_PATH),
			textInXhtmlEncoder,
			out
		);
		out.println("</loc>");
		if(lastmod != null) {
			out.print("        <lastmod>");
			encodeTextInXhtml(iso8601.print(lastmod), out);
			out.println("</lastmod>");
		}
		out.println("    </sitemap>");
	}

	/**
	 * The response is not given to getLastModified, but we need it for captures to get
	 * the last modified.
	 */
	private static final String RESPONSE_IN_REQUEST_ATTRIBUTE = SiteMapIndexServlet.class.getName() + ".responseInRequest";

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

	/**
	 * Gets all the books that contain at least one accessible view/page combo.
	 * Also provides the last modified time, if known, for the book.
	 */
	private static List<Tuple2<Book,ReadableInstant>> getSitemapBooks(
		final ServletContext servletContext,
		HttpServletRequest req,
		HttpServletResponse resp
	) throws ServletException, IOException {
		SemanticCMS semanticCMS = SemanticCMS.getInstance(servletContext);
		final SortedSet<View> views = semanticCMS.getViews();
		Collection<Book> books = semanticCMS.getBooks().values();
		int numBooks = books.size();
		if(
			numBooks > 1
			&& CountConcurrencyFilter.useConcurrentSubrequests(req)
		) {
			// Concurrent implementation
			final HttpServletRequest threadSafeReq = new UnmodifiableCopyHttpServletRequest(req);
			final HttpServletResponse threadSafeResp = new UnmodifiableCopyHttpServletResponse(resp);
			final TempFileList tempFileList = TempFileContext.getTempFileList(req);
			List<Book> booksWithSiteMapUrl;
			{
				List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>(numBooks);
				{
					for(final Book book : books) {
						tasks.add(
							new Callable<Boolean>() {
								@Override
								public Boolean call() throws ServletException, IOException {
									HttpServletRequest subrequest = new HttpServletSubRequest(threadSafeReq);
									HttpServletResponse subresponse = new HttpServletSubResponse(threadSafeResp, tempFileList);
									if(logger.isLoggable(Level.FINE)) logger.log(
										Level.FINE,
										"called, subrequest={0}, book={1}",
										new Object[] {
											subrequest,
											book
										}
									);
									return hasSiteMapUrl(
										servletContext,
										subrequest,
										subresponse,
										views,
										book,
										book.getContentRoot()
										//new HashSet<PageRef>()
									);
								}
							}
						);
					}
				}
				List<Boolean> results;
				try {
					results = semanticCMS.getExecutors().getPerProcessor().callAll(tasks);
				} catch(InterruptedException e) {
					// Restore the interrupted status
					Thread.currentThread().interrupt();
					throw new ServletException(e);
				} catch(ExecutionException e) {
					Throwable cause = e.getCause();
					if(cause instanceof RuntimeException) throw (RuntimeException)cause;
					if(cause instanceof ServletException) throw (ServletException)cause;
					if(cause instanceof IOException) throw (IOException)cause;
					throw new ServletException(cause);
				}
				// Now find the last modified with concurrency
				booksWithSiteMapUrl = new ArrayList<Book>(numBooks);
				{
					int i = 0;
					for(Book book : books) {
						if(results.get(i++)) booksWithSiteMapUrl.add(book);
					}
					assert i == numBooks;
				}
			}
			int booksWithSiteMapUrlSize = booksWithSiteMapUrl.size();
			if(booksWithSiteMapUrlSize > 0) {
				if(booksWithSiteMapUrlSize > 1) {
					// Concurrent implementation
					List<Callable<ReadableInstant>> lastModifiedTasks = new ArrayList<Callable<ReadableInstant>>(booksWithSiteMapUrlSize);
					{
						for(final Book book : booksWithSiteMapUrl) {
							lastModifiedTasks.add(
								new Callable<ReadableInstant>() {
									@Override
									public ReadableInstant call() throws ServletException, IOException {
										HttpServletRequest subrequest = new HttpServletSubRequest(threadSafeReq);
										HttpServletResponse subresponse = new HttpServletSubResponse(threadSafeResp, tempFileList);
										if(logger.isLoggable(Level.FINE)) logger.log(
											Level.FINE,
											"called, subrequest={0}, book={1}",
											new Object[] {
												subrequest,
												book
											}
										);
										return SiteMapServlet.getLastModified(
											servletContext,
											subrequest,
											subresponse,
											views,
											book
										);
									}
								}
							);
						}
					}
					List<ReadableInstant> lastModifieds;
					try {
						lastModifieds = semanticCMS.getExecutors().getPerProcessor().callAll(lastModifiedTasks);
					} catch(InterruptedException e) {
						// Restore the interrupted status
						Thread.currentThread().interrupt();
						throw new ServletException(e);
					} catch(ExecutionException e) {
						Throwable cause = e.getCause();
						if(cause instanceof RuntimeException) throw (RuntimeException)cause;
						if(cause instanceof ServletException) throw (ServletException)cause;
						if(cause instanceof IOException) throw (IOException)cause;
						throw new ServletException(cause);
					}
					List<Tuple2<Book,ReadableInstant>> sitemapBooks = new ArrayList<Tuple2<Book,ReadableInstant>>(booksWithSiteMapUrlSize);
					for(int i = 0; i < booksWithSiteMapUrlSize; i++) {
						sitemapBooks.add(
							new Tuple2<Book,ReadableInstant>(
								booksWithSiteMapUrl.get(i),
								lastModifieds.get(i)
							)
						);
					}
					return sitemapBooks;
				} else {
					// Single implementation
					Book book = booksWithSiteMapUrl.get(0);
					return Collections.singletonList(
						new Tuple2<Book,ReadableInstant>(
							book,
							SiteMapServlet.getLastModified(
								servletContext,
								req,
								resp,
								views,
								book
							)
						)
					);
				}
			} else {
				return Collections.emptyList();
			}
		} else {
			// Sequential implementation
			List<Tuple2<Book,ReadableInstant>> sitemapBooks = new ArrayList<Tuple2<Book,ReadableInstant>>(numBooks);
			for(Book book : books) {
				if(
					hasSiteMapUrl(
						servletContext,
						req,
						resp,
						views,
						book,
						book.getContentRoot()
					)
				) {
					sitemapBooks.add(
						new Tuple2<Book,ReadableInstant>(
							book,
							SiteMapServlet.getLastModified(
								servletContext,
								req,
								resp,
								views,
								book
							)
						)
					);
				}
			}
			return sitemapBooks;
		}
	}

	/**
	 * Last modified is known only when the last modified is known for all books,
	 * and it is the most recent of all the per-book last modified.
	 */
	@Override
	protected long getLastModified(HttpServletRequest req) {
		try {
			ReadableInstant mostRecent = null;
			for(
				Tuple2<Book,ReadableInstant> sitemapBook
				: getSitemapBooks(
					getServletContext(),
					req,
					(HttpServletResponse)req.getAttribute(RESPONSE_IN_REQUEST_ATTRIBUTE)
				)
			) {
				ReadableInstant lastModified = sitemapBook.getElement2();
				// If any single book is unknown, the overall result is unknown
				if(lastModified == null) {
					mostRecent = null;
					break;
				}
				if(
					mostRecent == null
					|| (lastModified.compareTo(mostRecent) > 0)
				) {
					mostRecent = lastModified;
				}
			}
			return mostRecent == null ? -1 : mostRecent.getMillis();
		} catch(ServletException e) {
			log("getLastModified failed", e);
			return -1;
		} catch(IOException e) {
			log("getLastModified failed", e);
			return -1;
		}
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		List<Tuple2<Book,ReadableInstant>> sitemapBooks = getSitemapBooks(getServletContext(), req, resp);
		final DateTimeFormatter iso8601 = ISODateTimeFormat.dateTime();

		resp.resetBuffer();
		resp.setContentType(CONTENT_TYPE);
		resp.setCharacterEncoding(ENCODING);
		PrintWriter out = resp.getWriter();
		out.println("<?xml version=\"1.0\" encoding=\"" + ENCODING + "\"?>");
		out.println("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
		for(Tuple2<Book,ReadableInstant> sitemapBook : sitemapBooks) {
			writeSitemap(
				req,
				resp,
				out,
				sitemapBook.getElement1(),
				sitemapBook.getElement2(),
				iso8601
			);
		}
		out.println("</sitemapindex>");
	}

	/**
	 * Checks if the sitemap has at least one page.
	 * This version implemented as a traversal.
	 */
	private static boolean hasSiteMapUrl(
		final ServletContext servletContext,
		final HttpServletRequest req,
		final HttpServletResponse resp,
		final SortedSet<View> views,
		final Book book,
		PageRef pageRef
	) throws ServletException, IOException {
		Boolean result = CapturePage.traversePagesAnyOrder(
			servletContext,
			req,
			resp,
			pageRef,
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
							return true;
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
					return book.equals(childPage.getBook());
				}
			}
		);
		assert result == null || result : "Should always be null or true";
		return result != null;
	}
}
