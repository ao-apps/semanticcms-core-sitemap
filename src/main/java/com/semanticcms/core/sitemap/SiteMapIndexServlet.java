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
import com.aoindustries.io.TempFileList;
import com.aoindustries.servlet.filter.TempFileContext;
import com.aoindustries.servlet.http.ServletUtil;
import com.semanticcms.core.model.Book;
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

	private static void writeSitemap(HttpServletRequest req, HttpServletResponse resp, PrintWriter out, Book book) throws IOException {
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

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.reset();
		resp.setContentType(CONTENT_TYPE);
		resp.setCharacterEncoding(ENCODING);
		PrintWriter out = resp.getWriter();
		out.println("<?xml version=\"1.0\" encoding=\"" + ENCODING + "\"?>");
		out.println("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
		SemanticCMS semanticCMS = SemanticCMS.getInstance(getServletContext());
		final SortedSet<View> views = semanticCMS.getViews();
		Collection<Book> books = semanticCMS.getBooks().values();
		int size = books.size();
		if(
			size > 1
			&& CountConcurrencyFilter.useConcurrentSubrequests(req)
		) {
			// Concurrent implementation
			List<Callable<Boolean>> tasks = new ArrayList<Callable<Boolean>>(size);
			{
				final HttpServletRequest threadSafeReq = new UnmodifiableCopyHttpServletRequest(req);
				final HttpServletResponse threadSafeResp = new UnmodifiableCopyHttpServletResponse(resp);
				final TempFileList tempFileList = TempFileContext.getTempFileList(req);
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
									getServletContext(),
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
			int i = 0;
			for(Book book : books) {
				if(results.get(i++)) {
					writeSitemap(req, resp, out, book);
				}
			}
			assert i == size;
		} else {
			// Sequential implementation
			for(Book book : books) {
				if(
					hasSiteMapUrl(
						getServletContext(),
						req,
						resp,
						views,
						book,
						book.getContentRoot()
					)
				) {
					writeSitemap(req, resp, out, book);
				}
			}
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
				public Set<PageRef> getEdges(Page page) {
					return page.getChildPages();
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
